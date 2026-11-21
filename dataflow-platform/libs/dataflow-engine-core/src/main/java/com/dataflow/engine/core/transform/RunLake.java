package com.dataflow.engine.core.transform;

import com.dataflow.engine.core.lake.DeltaTables;
import com.dataflow.spec.StepSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.TableRef;
import com.dataflow.spec.lake.Lake;
import io.delta.tables.DeltaTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import static org.apache.spark.sql.functions.col;

public class RunLake implements com.dataflow.transformation.Lake {

    private static final Pattern FILES = Pattern.compile("(landing|reference)/[A-Za-z0-9._/*{}-]{1,400}");

    private final SparkSession spark;
    private final Lake lake;
    private final String dataset;
    private final Map<String, Long> versions = new ConcurrentHashMap<>();
    private final Map<String, String> reads = Collections.synchronizedMap(new LinkedHashMap<>());

    public RunLake(SparkSession spark, Lake lake, String dataset) {
        this.spark = spark;
        this.lake = lake;
        this.dataset = dataset;
    }

    public Dataset<Row> source(String source, Dataset<Row> rows) {
        if (StepSpec.ROWS.equals(source)) {
            return rows;
        }
        if (source.startsWith(StepSpec.REFERENCE)) {
            return table(dataset + "." + source);
        }
        return table(source);
    }

    @Override
    public Dataset<Row> table(String name) {
        TableRef table = TableRef.parse(name)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Not a lake table: '" + name + "'; use " + TableRef.FORMAT));
        String path = table.path(lake);
        long version = versions.computeIfAbsent(path, this::latest);
        reads.put(name, path + "@v" + version);
        Dataset<Row> rows =
                spark.read().format(DeltaTables.FORMAT).option("versionAsOf", version).load(path);
        return table.kind() == TableRef.Kind.CURRENT
                ? rows.where(col(SystemColumns.END_AT).isNull()).drop(SystemColumns.CONTENT_HASH, SystemColumns.END_AT)
                : rows;
    }

    public boolean exists(String name) {
        return TableRef.parse(name)
                .map(table -> DeltaTable.isDeltaTable(spark, table.path(lake)))
                .orElse(false);
    }

    @Override
    public Dataset<Row> files(String path, String format, Map<String, String> options) {
        if (path == null || !FILES.matcher(path).matches() || path.contains("..")) {
            throw new IllegalArgumentException(
                    "Files are read from the lake's landing/ or reference/ only, got '" + path + "'");
        }
        reads.put("files:" + path, lake.root() + "/" + path);
        return spark.read().format(format).options(options).load(lake.root() + "/" + path);
    }

    public Map<String, String> reads() {
        synchronized (reads) {
            return Map.copyOf(reads);
        }
    }

    private long latest(String path) {
        if (!DeltaTable.isDeltaTable(spark, path)) {
            throw new IllegalArgumentException("No Delta table at " + path);
        }
        Row last = DeltaTable.forPath(spark, path).history(1).select("version").first();
        return last.getLong(0);
    }
}
