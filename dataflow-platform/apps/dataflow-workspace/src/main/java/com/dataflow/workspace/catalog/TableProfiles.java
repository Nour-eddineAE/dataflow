package com.dataflow.workspace.catalog;

import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.QueryResult;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.Table;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.dataflow.workspace.serving.ClickHouseDsl.preview;
import static com.dataflow.workspace.serving.ClickHouseDsl.uniq;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.min;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.selectFrom;

@Component
public class TableProfiles {

    private static final String SOURCE = "source";
    private static final String ROWS = "rows";
    private static final int PREVIEW = 64;
    private static final Pattern COMPARABLE = Pattern.compile(
            "^(Nullable\\()?(LowCardinality\\()?(U?Int|Float|Decimal|Date|String|FixedString|Bool|UUID|Enum)");

    private final ClickHouse serving;

    public TableProfiles(ClickHouse serving) {
        this.serving = serving;
    }

    private static List<Field<?>> measures(QueryResult.Column column, int index) {
        Field<Object> value = field(name(SOURCE, column.name()));
        Field<?> nulls = count().filterWhere(value.isNull()).as(nulls(index));
        if (!COMPARABLE.matcher(column.type()).find()) {
            return List.of(nulls);
        }
        return List.of(
                nulls,
                uniq(value).as(distinct(index)),
                preview(min(value), PREVIEW).as(lowest(index)),
                preview(max(value), PREVIEW).as(highest(index)));
    }

    private static Map<String, Object> firstRow(QueryResult result) {
        Map<String, Object> row = new HashMap<>();
        for (int i = 0; i < result.columns().size(); i++) {
            row.put(result.columns().get(i).name(), result.rows().getFirst().get(i));
        }
        return row;
    }

    private static String nulls(int index) {
        return "nulls_" + index;
    }

    private static String distinct(int index) {
        return "distinct_" + index;
    }

    private static String lowest(int index) {
        return "min_" + index;
    }

    private static String highest(int index) {
        return "max_" + index;
    }

    private static long tally(Object value) {
        return value == null ? 0 : ((Number) value).longValue();
    }

    private static Long number(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    public TableProfile profile(Select<Record> source) {
        Table<Record> rows = source.asTable(SOURCE);
        List<QueryResult.Column> columns =
                serving.fetch(selectFrom(rows).limit(0), 0).columns();
        List<Field<?>> measures = new ArrayList<>();
        measures.add(count().as(ROWS));
        for (int i = 0; i < columns.size(); i++) {
            measures.addAll(measures(columns.get(i), i));
        }
        QueryResult result = serving.fetch(select(measures).from(rows), 1);
        Map<String, Object> measured = firstRow(result);
        List<ColumnProfile> profiles = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            QueryResult.Column column = columns.get(i);
            profiles.add(new ColumnProfile(
                    column.name(),
                    column.type(),
                    tally(measured.get(nulls(i))),
                    number(measured.get(distinct(i))),
                    text(measured.get(lowest(i))),
                    text(measured.get(highest(i)))));
        }
        return new TableProfile(tally(measured.get(ROWS)), profiles, result.elapsedMs(), result.bytesRead());
    }
}
