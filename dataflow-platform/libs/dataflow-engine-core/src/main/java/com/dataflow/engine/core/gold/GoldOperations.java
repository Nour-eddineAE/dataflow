package com.dataflow.engine.core.gold;

import com.dataflow.spec.AggregateSpec;
import com.dataflow.spec.DedupeSpec;
import com.dataflow.spec.GoldStepSpec;
import com.dataflow.spec.JoinSpec;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.apache.spark.sql.functions.coalesce;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.expr;
import static org.apache.spark.sql.functions.lit;
import static org.apache.spark.sql.functions.row_number;

public final class GoldOperations {

    private static final String INPUT = "input";

    private static final String RANK = "__dedupe_rank";
    private static final String QUOTE = "`";

    private GoldOperations() {}

    public static Dataset<Row> apply(
            String table, Dataset<Row> start, List<GoldStepSpec> steps, Function<String, Dataset<Row>> relations) {
        Dataset<Row> rows = start;
        for (int index = 0; index < steps.size(); index++) {
            GoldStepSpec step = steps.get(index);
            String where = "gold " + table + " step " + (index + 1) + " (" + step.operation().key() + ")";
            try {
                rows = apply(where, rows, step, relations);
            } catch (GoldStepException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new GoldStepException(where + ": " + e.getMessage(), e);
            }
        }
        return rows;
    }

    private static Dataset<Row> apply(
            String where, Dataset<Row> rows, GoldStepSpec step, Function<String, Dataset<Row>> relations) {
        return switch (step.operation()) {
            case JOIN -> join(where, rows, step.join(), relations.apply(step.join().with()));
            case UNION -> union(rows, step.union(), relations);
            case FILTER -> rows.where(expr(step.filter()));
            case DERIVE -> derive(rows, step.derive());
            case SELECT -> rows.selectExpr(step.select().toArray(String[]::new));
            case RENAME -> rename(where, rows, step.rename());
            case DROP -> drop(where, rows, step.drop());
            case AGGREGATE -> aggregate(rows, step.aggregate());
            case DEDUPE -> dedupe(where, rows, step.dedupe());
        };
    }

    static Dataset<Row> join(String where, Dataset<Row> left, JoinSpec join, Dataset<Row> right) {
        Map<String, String> renamed = prefixed(right.columns(), join);
        Dataset<Row> other = join.prefix() == null ? right : right.withColumnsRenamed(renamed);
        Map<String, String> keys = new LinkedHashMap<>();
        join.on().forEach((leftColumn, rightColumn) -> keys.put(leftColumn, renamed.getOrDefault(rightColumn, rightColumn)));
        require(where, left.columns(), keys.keySet(), "left");
        require(where, other.columns(), keys.values(), "right");
        Dataset<Row> joined = left.alias(JoinSpec.LEFT).join(other.alias(JoinSpec.RIGHT), condition(join, keys), join.type().spark());
        if (!join.type().keepsRight()) {
            return joined.select(Arrays.stream(left.columns()).map(name -> qualified(JoinSpec.LEFT, name)).toArray(Column[]::new));
        }
        Set<String> shared = shared(keys);
        checkClashes(where, left.columns(), other.columns(), shared);
        return joined.select(output(join.type(), left.columns(), other.columns(), shared).toArray(Column[]::new));
    }

    private static Column condition(JoinSpec join, Map<String, String> keys) {
        if (join.condition() != null) {
            return expr(join.condition());
        }
        return keys.entrySet().stream()
                .map(key -> qualified(JoinSpec.LEFT, key.getKey()).equalTo(qualified(JoinSpec.RIGHT, key.getValue())))
                .reduce(Column::and)
                .orElse(lit(true));
    }

    private static Set<String> shared(Map<String, String> keys) {
        Set<String> shared = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        keys.forEach((leftColumn, rightColumn) -> {
            if (leftColumn.equalsIgnoreCase(rightColumn)) {
                shared.add(leftColumn);
            }
        });
        return shared;
    }

    private static void checkClashes(String where, String[] left, String[] right, Set<String> shared) {
        Set<String> leftNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        leftNames.addAll(Arrays.asList(left));
        List<String> clashes = Arrays.stream(right)
                .filter(name -> leftNames.contains(name) && !shared.contains(name))
                .toList();
        if (!clashes.isEmpty()) {
            throw new GoldStepException(where + ": both sides have " + clashes
                    + "; set prefix, or rename or drop them on one side first");
        }
    }

    private static List<Column> output(JoinSpec.Type type, String[] left, String[] right, Set<String> shared) {
        List<Column> output = new ArrayList<>();
        for (String name : left) {
            output.add(!shared.contains(name) ? qualified(JoinSpec.LEFT, name)
                    : type == JoinSpec.Type.FULL ? coalesce(qualified(JoinSpec.LEFT, name), qualified(JoinSpec.RIGHT, name)).as(name)
                    : type == JoinSpec.Type.RIGHT ? qualified(JoinSpec.RIGHT, name).as(name)
                    : qualified(JoinSpec.LEFT, name));
        }
        Arrays.stream(right)
                .filter(name -> !shared.contains(name))
                .forEach(name -> output.add(qualified(JoinSpec.RIGHT, name)));
        return output;
    }

    private static Map<String, String> prefixed(String[] columns, JoinSpec join) {
        if (join.prefix() == null) {
            return Map.of();
        }
        return Arrays.stream(columns)
                .collect(Collectors.toMap(name -> name, join::prefixed, (first, second) -> first, LinkedHashMap::new));
    }

    private static Dataset<Row> union(Dataset<Row> rows, List<String> others, Function<String, Dataset<Row>> relations) {
        Dataset<Row> result = rows;
        for (String other : others) {
            result = result.unionByName(relations.apply(other), true);
        }
        return result;
    }

    private static Dataset<Row> derive(Dataset<Row> rows, Map<String, String> columns) {
        Dataset<Row> result = rows;
        for (Map.Entry<String, String> column : columns.entrySet()) {
            result = result.withColumn(column.getKey(), expr(column.getValue()));
        }
        return result;
    }

    private static Dataset<Row> rename(String where, Dataset<Row> rows, Map<String, String> names) {
        require(where, rows.columns(), names.keySet(), INPUT);
        return rows.withColumnsRenamed(names);
    }

    private static Dataset<Row> drop(String where, Dataset<Row> rows, List<String> names) {
        require(where, rows.columns(), names, INPUT);
        return rows.drop(names.toArray(String[]::new));
    }

    private static Dataset<Row> aggregate(Dataset<Row> rows, AggregateSpec aggregate) {
        Column[] by = aggregate.by().stream().map(functions::expr).toArray(Column[]::new);
        List<Column> measures = aggregate.measures().entrySet().stream()
                .map(measure -> expr(measure.getValue()).as(measure.getKey()))
                .toList();
        Column first = measures.getFirst();
        Column[] rest = measures.subList(1, measures.size()).toArray(Column[]::new);
        return by.length == 0 ? rows.agg(first, rest) : rows.groupBy(by).agg(first, rest);
    }

    private static Dataset<Row> dedupe(String where, Dataset<Row> rows, DedupeSpec dedupe) {
        require(where, rows.columns(), dedupe.keys(), INPUT);
        if (dedupe.latestBy() == null) {
            return rows.dropDuplicates(dedupe.keys().toArray(String[]::new));
        }
        Column[] keys = dedupe.keys().stream().map(GoldOperations::quoted).toArray(Column[]::new);
        return rows.withColumn(RANK, row_number().over(Window.partitionBy(keys).orderBy(expr(dedupe.latestBy()).desc())))
                .where(col(RANK).equalTo(1))
                .drop(RANK);
    }

    private static void require(String where, String[] available, Collection<String> wanted, String side) {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        names.addAll(Arrays.asList(available));
        List<String> missing = wanted.stream().filter(name -> !names.contains(name)).toList();
        if (!missing.isEmpty()) {
            throw new GoldStepException(where + ": the " + side + " table has no column " + missing
                    + "; it has " + Arrays.asList(available));
        }
    }

    private static Column quoted(String name) {
        return col(QUOTE + name.replace(QUOTE, QUOTE + QUOTE) + QUOTE);
    }

    private static Column qualified(String alias, String name) {
        return col(alias + "." + QUOTE + name.replace(QUOTE, QUOTE + QUOTE) + QUOTE);
    }
}
