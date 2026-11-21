package com.dataflow.engine.core.history;

import com.dataflow.spec.ColumnSpec;
import io.delta.tables.DeltaTable;
import lombok.RequiredArgsConstructor;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.storage.StorageLevel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.dataflow.spec.SystemColumns.CONTENT_HASH;
import static com.dataflow.spec.SystemColumns.END_AT;
import static com.dataflow.spec.SystemColumns.FACT_UNIQUE_ID;
import static com.dataflow.spec.SystemColumns.START_AT;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;

@RequiredArgsConstructor
public class ScdMerge {

    private static final String TARGET = "t";
    private static final String SOURCE = "s";
    private static final String MERGE_KEY = "mergeKey";
    private static final String CURRENT_START = "currentStart";
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TYPE = Pattern.compile("[A-Za-z0-9_(), <>]+");

    private final SparkSession spark;

    private static void execute(DeltaTable target, Dataset<Row> staged, String[] columns) {
        Map<String, String> insert = new LinkedHashMap<>();
        for (String column : columns) {
            insert.put("`" + column + "`", SOURCE + ".`" + column + "`");
        }
        insert.put(END_AT, "null");
        target.as(TARGET)
                .merge(
                        staged.as(SOURCE),
                        "%s.%s = %s.%s AND %s.%s IS NULL"
                                .formatted(TARGET, FACT_UNIQUE_ID, SOURCE, MERGE_KEY, TARGET, END_AT))
                .whenMatched("%s.%s <> %s.%s".formatted(TARGET, CONTENT_HASH, SOURCE, CONTENT_HASH))
                .updateExpr(Map.of(END_AT, SOURCE + "." + START_AT))
                .whenNotMatched()
                .insertExpr(insert)
                .withSchemaEvolution()
                .execute();
    }

    private static String deltaPath(String path) {
        return path.replace("`", "``");
    }

    private static String identifier(String name) {
        if (name == null || !IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid SQL identifier: " + name);
        }
        return name;
    }

    private static String type(String ddl) {
        String normalized = ddl == null ? "" : ddl.strip();
        if (!TYPE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid SQL type: " + ddl);
        }
        return normalized;
    }

    @SuppressWarnings("SqlSourceToSinkFlow")
    public List<String> addDerivedColumns(String path, List<ColumnSpec> derived) {
        List<String> present =
                Arrays.asList(DeltaTable.forPath(spark, path).toDF().columns());
        List<String> added = new ArrayList<>();
        String safePath = deltaPath(path);
        for (ColumnSpec column : derived) {
            String safeName = identifier(column.name());
            if (present.contains(safeName)) {
                continue;
            }
            String safeType = type(column.type());
            spark.sql("ALTER TABLE delta.`%s` ADD COLUMNS (`%s` %s)".formatted(safePath, safeName, safeType));
            DeltaTable.forPath(spark, path)
                    .updateExpr(Map.of(safeName, "CAST((%s) AS %s)".formatted(column.from(), safeType)));
            added.add(safeName);
        }
        return added;
    }

    public MergeCounts merge(Dataset<Row> versions, String path, boolean ordered) {
        DeltaTable target = DeltaTable.forPath(spark, path);
        Dataset<Row> current = target.toDF()
                .where(col(END_AT).isNull())
                .select(col(FACT_UNIQUE_ID), col(CONTENT_HASH), col(START_AT).alias(CURRENT_START))
                .as(TARGET);
        Column sameFact = col(SOURCE + "." + FACT_UNIQUE_ID).equalTo(col(TARGET + "." + FACT_UNIQUE_ID));
        Column sameContent = col(SOURCE + "." + CONTENT_HASH).equalTo(col(TARGET + "." + CONTENT_HASH));

        Dataset<Row> candidates = versions.as(SOURCE).join(current, sameFact.and(sameContent), "left_anti");
        Dataset<Row> fresh = ordered
                ? candidates
                        .as(SOURCE)
                        .join(
                                current,
                                sameFact.and(col(TARGET + "." + CURRENT_START).gt(col(SOURCE + "." + START_AT))),
                                "left_anti")
                : candidates;
        fresh = fresh.persist(StorageLevel.DISK_ONLY());
        try {
            long freshCount = fresh.count();
            long stale = candidates.count() - freshCount;
            if (freshCount == 0) {
                return new MergeCounts(0, 0, stale);
            }
            Dataset<Row> changed = fresh.as(SOURCE).join(current, sameFact, "left_semi");
            long changedCount = changed.count();
            Dataset<Row> staged = changed.withColumn(MERGE_KEY, lit(null).cast(DataTypes.StringType))
                    .unionByName(fresh.withColumn(MERGE_KEY, col(FACT_UNIQUE_ID)));
            execute(target, staged, versions.columns());
            return new MergeCounts(freshCount - changedCount, changedCount, stale);
        } finally {
            fresh.unpersist();
        }
    }
}
