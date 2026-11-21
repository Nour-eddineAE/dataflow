package com.dataflow.engine.core.draft;

import com.dataflow.engine.core.flows.Sources;
import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.SourceSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.lake.Lake;
import com.dataflow.spec.lake.Layer;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.storage.StorageLevel;

import static org.apache.spark.sql.functions.current_timestamp;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.monotonically_increasing_id;

@RequiredArgsConstructor
public class DraftSample {

    private final SparkSession spark;
    private final Lake lake;
    private final DeltaTables tables;
    private final Sources sources;

    public Sample take(PipelineSpec spec, int rows, String runId) {
        String bronze = lake.table(Layer.BRONZE, spec.dataset());
        boolean landed = tables.exists(bronze);
        Dataset<Row> raw =
                landed ? spark.read().format(DeltaTables.FORMAT).load(bronze).limit(rows) : fromSource(spec, rows, runId);
        Dataset<Row> numbered =
                raw.withColumn(Sample.ROW, monotonically_increasing_id()).persist(StorageLevel.DISK_ONLY());
        return new Sample(numbered, landed ? Sample.Origin.BRONZE : Sample.Origin.SOURCE);
    }

    private Dataset<Row> fromSource(PipelineSpec spec, int rows, String runId) {
        SourceSpec source = spec.source().partitioned()
                ? spec.source().forPartition(spec.source().partitions().start())
                : spec.source();
        String location = source.url() != null
                ? sources.fetch(spec.dataset(), source, true).toUri().toString()
                : source.path();
        return spark.read()
                .format(source.format())
                .options(source.options())
                .load(location)
                .limit(rows)
                .withColumn(SystemColumns.RESCUED_DATA, lit(null).cast(DataTypes.StringType))
                .withColumn(SystemColumns.SOURCE_FILE, lit(location))
                .withColumn(SystemColumns.INGESTED_AT, current_timestamp())
                .withColumn(SystemColumns.RUN_ID, lit(runId));
    }
}
