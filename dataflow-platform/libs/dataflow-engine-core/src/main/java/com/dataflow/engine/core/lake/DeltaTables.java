package com.dataflow.engine.core.lake;

import io.delta.tables.DeltaTable;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class DeltaTables {

    public static final String FORMAT = "delta";
    public static final String CHANGE_DATA_FEED = "delta.enableChangeDataFeed";
    public static final String DELETION_VECTORS = "delta.enableDeletionVectors";

    private final SparkSession spark;

    private static String deltaPath(String path) {
        return path.replace("`", "``");
    }

    public boolean exists(String path) {
        return DeltaTable.isDeltaTable(spark, path);
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public void ensure(String path, StructType schema, List<String> clusterBy, boolean changeFeed) {
        if (exists(path)) {
            return;
        }
        String columns = schema.asNullable().toDDL();
        String clustering = clusterBy.isEmpty()
                ? ""
                : " CLUSTER BY ("
                        + clusterBy.stream().map(column -> "`" + column + "`").collect(Collectors.joining(", ")) + ")";
        Map<String, String> properties =
                Map.of(CHANGE_DATA_FEED, Boolean.toString(changeFeed), DELETION_VECTORS, "false");
        String props = properties.entrySet().stream()
                .map(entry -> "'" + entry.getKey() + "' = '" + entry.getValue() + "'")
                .sorted()
                .collect(Collectors.joining(", "));
        String safePath = deltaPath(path);
        spark.sql("CREATE TABLE IF NOT EXISTS delta.`%s` (%s) USING DELTA%s TBLPROPERTIES (%s)"
                .formatted(safePath, columns, clustering, props));
    }

    public long version(String path) {
        return DeltaTable.forPath(spark, path)
                .history(1)
                .select("version")
                .first()
                .getLong(0);
    }
}
