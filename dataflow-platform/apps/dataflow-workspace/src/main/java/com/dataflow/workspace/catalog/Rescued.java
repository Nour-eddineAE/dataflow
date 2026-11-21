package com.dataflow.workspace.catalog;

import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.QueryResult;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;

@Component
public class Rescued {

    private static final String SOURCE = "source";
    private static final String RESCUED = "_rescued_data";
    private static final int LIMIT = 50;

    private final ClickHouse serving;

    public Rescued(ClickHouse serving) {
        this.serving = serving;
    }

    static String suggestion(String key) {
        String[] words = key.split("[^A-Za-z0-9]+");
        StringBuilder camel = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            camel.append(
                    camel.isEmpty()
                            ? word.substring(0, 1).toLowerCase(Locale.ROOT) + word.substring(1)
                            : word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
        }
        String column = camel.isEmpty() || !Character.isLetter(camel.charAt(0)) ? "c" + camel : camel.toString();
        return "- { name: " + column + ", type: string, from: \"get_json_object(_rescued_data, '$."
                + key.replace("'", "") + "')\" }";
    }

    public List<Key> keys(Select<Record> bronze) {
        Set<String> columns = serving.fetch(selectFrom(bronze.asTable(SOURCE)).limit(0), 0).columns().stream()
                .map(column -> column.name().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        Field<String> json = function("assumeNotNull", String.class, field(name(SOURCE, RESCUED)));
        Field<String> key = function("arrayJoin", String.class, function("JSONExtractKeys", String.class, json))
                .as("key");
        Field<Integer> rows = count().as("rows");
        Field<String> sample = function(
                        "any", String.class, function("JSONExtractRaw", String.class, json, field(name("key"))))
                .as("sample");
        QueryResult result = serving.fetch(
                select(key, rows, sample)
                        .from(bronze.asTable(SOURCE))
                        .where(field(name(SOURCE, RESCUED))
                                .isNotNull()
                                .and(field(name(SOURCE, RESCUED), String.class).ne("")))
                        .groupBy(field(name("key")))
                        .orderBy(rows.desc())
                        .limit(LIMIT),
                LIMIT);
        return result.rows().stream()
                .map(row -> {
                    String name = String.valueOf(row.get(0));
                    boolean known = columns.contains(name.toLowerCase(Locale.ROOT));
                    return new Key(
                            name,
                            ((Number) row.get(1)).longValue(),
                            row.get(2) == null ? null : String.valueOf(row.get(2)),
                            known,
                            known ? null : suggestion(name));
                })
                .toList();
    }

    public record Key(String key, long rows, String sample, boolean known, String suggestion) {}
}
