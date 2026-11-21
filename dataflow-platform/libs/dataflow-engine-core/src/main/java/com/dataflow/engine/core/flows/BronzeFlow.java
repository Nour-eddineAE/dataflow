package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.engine.core.streaming.IdempotentWrites;
import com.dataflow.engine.core.streaming.MicroBatch;
import com.dataflow.engine.core.streaming.MicroBatches;
import com.dataflow.spec.BronzeSpec.SchemaEvolution;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import org.apache.hadoop.fs.Path;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.storage.StorageLevel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.spark.sql.functions.current_timestamp;
import static org.apache.spark.sql.functions.lit;

@SuppressWarnings("resource")
public class BronzeFlow implements Flow {

    static final String FILES_SCHEMA = "path STRING, modificationTime TIMESTAMP, length BIGINT, content BINARY";
    static final List<String> CLUSTER_BY = List.of(SystemColumns.INGESTED_AT);

    private static boolean exists(SparkSession spark, String location) {
        Path path = new Path(location);
        try {
            return path.getFileSystem(spark.sparkContext().hadoopConfiguration())
                    .exists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String name() {
        return FlowNames.BRONZE;
    }

    @Override
    public Set<String> inputs() {
        return Set.of(FlowNames.INGEST);
    }

    @Override
    public FlowResult run(RunContext context) {
        SparkSession spark = context.spark();
        PipelineSpec plan = context.plan();
        String landing = context.lake().table(Layer.LANDING, context.dataset());
        String bronze = context.lake().table(Layer.BRONZE, context.dataset());
        AtomicLong files = new AtomicLong();
        AtomicLong rows = new AtomicLong();
        AtomicLong rescued = new AtomicLong();

        if (exists(spark, landing)) {
            Dataset<Row> newFiles = spark.readStream()
                    .format("binaryFile")
                    .schema(FILES_SCHEMA)
                    .option("maxFilesPerTrigger", plan.bronze().maxFilesPerTrigger())
                    .load(landing)
                    .select("path");
            MicroBatches.drain(
                    newFiles,
                    context.lake().checkpoint(context.dataset(), name()),
                    name(),
                    batch -> land(context, bronze, batch, files, rows, rescued));
        }

        return new FlowResult()
                .rows(rows.get(), rows.get(), 0)
                .detail("files", files.get())
                .detail("rescued_rows", rescued.get())
                .materialized(
                        AssetKeys.of(context.dataset(), Layer.BRONZE),
                        Map.of(
                                "new_rows",
                                rows.get(),
                                "new_files",
                                files.get(),
                                "rescued_rows",
                                rescued.get(),
                                "table",
                                bronze));
    }

    private void land(
            RunContext context,
            String bronze,
            MicroBatch batch,
            AtomicLong files,
            AtomicLong rows,
            AtomicLong rescued) {
        List<String> paths = batch.rows().as(Encoders.STRING()).collectAsList();
        if (paths.isEmpty()) {
            return;
        }
        SparkSession spark = context.spark();
        PipelineSpec plan = context.plan();
        boolean addColumns = plan.bronze().schemaEvolution() == SchemaEvolution.ADD_NEW_COLUMNS;
        StructType target = context.tables().exists(bronze)
                ? Rescue.dataColumns(spark.read().format(DeltaTables.FORMAT).load(bronze).schema())
                : null;

        Dataset<Row> landed = read(spark, plan, paths, target, addColumns)
                .withColumn(SystemColumns.INGESTED_AT, current_timestamp())
                .withColumn(SystemColumns.RUN_ID, lit(context.runId()))
                .persist(StorageLevel.DISK_ONLY());
        try {
            context.tables().ensure(bronze, landed.schema(), CLUSTER_BY, false);
            long count = landed.count();
            rescued.addAndGet(
                    landed.where(landed.col(SystemColumns.RESCUED_DATA).isNotNull())
                            .count());
            IdempotentWrites.append(
                    landed, bronze, batch, addColumns, context.batching().maxRecordsPerFile());
            files.addAndGet(paths.size());
            rows.addAndGet(count);
        } finally {
            landed.unpersist();
        }
    }

    private static Dataset<Row> read(
            SparkSession spark, PipelineSpec plan, List<String> paths, StructType known, boolean addColumns) {
        StructType target = known;
        List<Dataset<Row>> parts = new ArrayList<>();
        for (String path : paths) {
            Dataset<Row> raw = spark.read()
                    .format(plan.source().format())
                    .options(plan.source().options())
                    .load(path);
            if (target == null) {
                target = raw.schema();
            }
            Dataset<Row> conformed = addColumns
                    ? raw.withColumn(SystemColumns.RESCUED_DATA, lit(null).cast(DataTypes.StringType))
                    : Rescue.conform(raw, target);
            parts.add(conformed.withColumn(SystemColumns.SOURCE_FILE, lit(path)));
        }
        return parts.stream().reduce((left, right) -> left.unionByName(right, addColumns)).orElseThrow();
    }
}
