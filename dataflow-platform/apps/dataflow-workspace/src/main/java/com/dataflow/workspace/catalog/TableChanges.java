package com.dataflow.workspace.catalog;

import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.QueryResult;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static org.jooq.impl.DSL.asterisk;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.select;

@Component
public class TableChanges {

    static final int MAX_VERSIONS = 50;
    static final int MAX_ROWS = 500;
    private static final int CHANGE_TYPES = 4;
    private static final Field<String> TYPE = field(name("_change_type"), String.class);
    private static final Field<Long> VERSION = field(name("_commit_version"), Long.class);

    private final ClickHouse serving;
    private final LakeFunctions lake;

    public TableChanges(ClickHouse serving, LakeFunctions lake) {
        this.serving = serving;
        this.lake = lake;
    }

    public Changes between(LakeTable table, long from, long to) {
        if (!Catalog.HISTORY.equals(table.layer())) {
            throw new IllegalArgumentException("Only history tables keep a change feed; " + table.id() + " does not");
        }
        if (from < 0 || to < from || to - from >= MAX_VERSIONS) {
            throw new IllegalArgumentException(
                    "Compare at most " + MAX_VERSIONS + " versions, from an earlier to a later one");
        }
        Map<String, String> range = Map.of(
                "delta_lake_snapshot_start_version",
                String.valueOf(from),
                "delta_lake_snapshot_end_version",
                String.valueOf(to));
        Table<Record> delta = lake.delta(table.key());
        List<Count> counts = serving
                .fetch(
                        select(VERSION, TYPE, count())
                                .from(delta)
                                .groupBy(VERSION, TYPE)
                                .orderBy(VERSION, TYPE),
                        MAX_VERSIONS * CHANGE_TYPES,
                        range)
                .rows()
                .stream()
                .map(row -> new Count(
                        ((Number) row.get(0)).longValue(),
                        String.valueOf(row.get(1)),
                        ((Number) row.get(2)).longValue()))
                .toList();
        QueryResult rows = serving.fetch(
                select(TYPE.as("change"), VERSION.as("version"), asterisk())
                        .from(delta)
                        .orderBy(VERSION, LakeFields.FACT_UNIQUE_ID, TYPE)
                        .limit(MAX_ROWS),
                MAX_ROWS,
                range);
        return new Changes(from, to, counts, rows);
    }

    public record Count(long version, String change, long rows) {}

    public record Changes(long from, long to, List<Count> counts, QueryResult rows) {}
}
