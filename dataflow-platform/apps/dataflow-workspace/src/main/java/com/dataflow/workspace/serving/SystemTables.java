package com.dataflow.workspace.serving;

import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static org.jooq.impl.DSL.avg;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.function;
import static org.jooq.impl.DSL.inline;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.row;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.substring;
import static org.jooq.impl.DSL.table;

@Component
public class SystemTables {

    private static final int MAX_COLUMNS = 50_000;
    private static final String FINISHED_TYPE = "QueryFinish";
    private static final Field<String> DATABASE = column("database", String.class);
    private static final Field<String> TABLE = column("table", String.class);
    private static final Field<String> NAME = column("name", String.class);
    private static final Field<String> TYPE = column("type", String.class);
    private static final Field<Long> POSITION = column("position", Long.class);
    private static final Field<String> ENGINE = column("engine", String.class);
    private static final Field<Integer> IS_AGGREGATE = column("is_aggregate", Integer.class);
    private static final Field<String> SYNTAX = column("syntax", String.class);
    private static final Field<String> DESCRIPTION = column("description", String.class);
    private static final Field<String> RETURNED_VALUE = column("returned_value", String.class);
    private static final Field<String> VALUE = column("value", String.class);
    private static final Field<String> DEFAULT_VALUE = column("default", String.class);
    private static final Field<Integer> CHANGED = column("changed", Integer.class);
    private static final Field<String> QUERY_TYPE = column("type", String.class);
    private static final Field<String> EVENT_DATE = column("event_date", String.class);
    private static final Field<String> EVENT_TIME = column("event_time", String.class);
    private static final Field<Object> TABLES = column("tables", Object.class);
    private static final String QUERY_LOG = "query_log";
    private static final String QUERIES = "queries";
    private static final Field<String> QUERIED = function("arrayJoin", String.class, TABLES);
    private static final Field<String> QUERIED_COLUMN = function("arrayJoin", String.class, column("columns", Object.class));
    private static final Field<String> LOG_COMMENT = column("log_comment", String.class);
    private static final Field<String> EVENT_MICROS = column("event_time_microseconds", String.class);
    private static final Field<Integer> INITIAL = column("is_initial_query", Integer.class);
    private static final Field<String> QUERY = column("query", String.class);
    private static final Field<Long> DURATION = column("query_duration_ms", Long.class);
    private static final Field<Long> READ_ROWS = column("read_rows", Long.class);
    private static final Field<Long> READ_BYTES = column("read_bytes", Long.class);
    private static final Field<Long> RESULT_ROWS = column("result_rows", Long.class);
    private static final Field<String> EXCEPTION = column("exception", String.class);
    private static final List<String> ENDED = List.of(FINISHED_TYPE, "ExceptionBeforeStart", "ExceptionWhileProcessing");
    private static final int MAX_QUERY = 10_000;
    private static final int MAX_ERROR = 500;
    private static final String FINISHED = FINISHED_TYPE;
    private static final int USAGE_LIMIT = 500;
    private static final String MATERIALIZED_VIEW = "MaterializedView";
    private static final String PLAIN_VIEW = "View";
    private static final String READS_LAKE = "%deltaLake(%";
    private static final Field<String> AS_SELECT = column("as_select", String.class);
    private static final String INTERNAL = "\\_%";
    private static final int HEALTH_WINDOW_HOURS = 1;
    private static final String RESIDENT_MEMORY = "MemoryResident";
    private static final Field<String> METRIC = column("metric", String.class);
    private static final Field<String> VIEW = column("view", String.class);

    private final ClickHouse serving;

    public SystemTables(ClickHouse serving) {
        this.serving = serving;
    }

    private static Table<?> columns() {
        return system("columns");
    }

    private static Table<?> tables() {
        return system("tables");
    }

    private static Table<?> system(String table) {
        return table(name("system", table));
    }

    public Set<String> tables(String database) {
        return serving.fetch(select(NAME).from(tables()).where(DATABASE.eq(database))).rows().stream()
                .map(row -> String.valueOf(row.getFirst()))
                .collect(Collectors.toSet());
    }

    public Set<String> views(Collection<String> databases) {
        return serving.fetch(select(DATABASE, NAME)
                        .from(tables())
                        .where(DATABASE.in(databases))
                        .and(ENGINE.eq(PLAIN_VIEW)))
                .rows().stream()
                .map(row -> row.get(0) + "." + row.get(1))
                .collect(Collectors.toSet());
    }

    public List<DatabaseTables> tablesByDatabase() {
        return serving.fetchInto(
                select(
                                DATABASE,
                                count().as("tableCount"),
                                count().filterWhere(ENGINE.eq(PLAIN_VIEW).and(AS_SELECT.like(READS_LAKE)))
                                        .as("lakeViews"))
                        .from(tables())
                        .groupBy(DATABASE)
                        .orderBy(DATABASE),
                DatabaseTables.class);
    }

    public QueryResult columns(String database, String table) {
        return serving.fetch(select(NAME, TYPE)
                .from(columns())
                .where(DATABASE.eq(database).and(TABLE.eq(table)))
                .orderBy(POSITION));
    }

    public Map<String, List<SchemaColumn>> columnsByTable(Collection<String> databases) {
        Map<String, List<SchemaColumn>> byTable = new TreeMap<>();
        serving.fetchInto(
                        select(DATABASE, TABLE, NAME, TYPE)
                                .from(columns())
                                .where(DATABASE.in(databases))
                                .and(row(DATABASE, TABLE)
                                        .notIn(select(DATABASE, NAME)
                                                .from(tables())
                                                .where(ENGINE.eq(MATERIALIZED_VIEW))))
                                .orderBy(DATABASE, TABLE, POSITION),
                        TableColumn.class,
                        MAX_COLUMNS)
                .forEach(column -> byTable.computeIfAbsent(column.qualifiedTable(), key -> new ArrayList<>()).add(column.schema()));
        return byTable;
    }

    public List<ServerSetting> changedServerSettings() {
        return changed("server_settings");
    }

    public List<ServerSetting> changedQuerySettings() {
        return changed("settings");
    }

    private List<ServerSetting> changed(String settings) {
        return serving.fetchInto(
                select(NAME, VALUE, DEFAULT_VALUE.as("defaultValue"), DESCRIPTION)
                        .from(system(settings))
                        .where(CHANGED.eq(1))
                        .orderBy(NAME),
                ServerSetting.class);
    }

    public List<TableUsage> usage(LocalDate since) {
        return serving
                .fetchInto(
                        select(
                                        QUERIED.as("table"),
                                        count().as(QUERIES),
                                        max(EVENT_TIME).as("last"))
                                .from(system(QUERY_LOG))
                                .where(QUERY_TYPE.eq(FINISHED))
                                .and(EVENT_DATE.ge(since.toString()))
                                .groupBy(QUERIED)
                                .orderBy(count().desc())
                                .limit(USAGE_LIMIT),
                        TableUsage.class)
                .stream()
                .filter(usage ->
                        !usage.table().startsWith("system.") && !usage.table().startsWith("INFORMATION_SCHEMA."))
                .toList();
    }

    public List<ColumnUsage> columnUsage(String table, LocalDate since) {
        String prefix = table + ".";
        return serving
                .fetchInto(
                        select(QUERIED_COLUMN.as(NAME), count().as(QUERIES))
                                .from(system(QUERY_LOG))
                                .where(QUERY_TYPE.eq(FINISHED))
                                .and(EVENT_DATE.ge(since.toString()))
                                .and(function("has", Boolean.class, TABLES, inline(table)).isTrue())
                                .groupBy(QUERIED_COLUMN)
                                .orderBy(count().desc())
                                .limit(USAGE_LIMIT),
                        ColumnUsage.class)
                .stream()
                .filter(usage -> usage.name().startsWith(prefix))
                .map(usage -> new ColumnUsage(usage.name().substring(prefix.length()), usage.queries()))
                .toList();
    }

    public List<QueryRun> history(String person, LocalDate since, int limit) {
        return serving.fetchInto(
                select(
                                EVENT_TIME.as("time"),
                                substring(LOG_COMMENT, ClickHouse.EDITOR.length() + 1).as("person"),
                                substring(QUERY, 1, MAX_QUERY).as("query"),
                                DURATION.as("durationMs"),
                                READ_ROWS.as("rowsRead"),
                                READ_BYTES.as("bytesRead"),
                                RESULT_ROWS.as("resultRows"),
                                QUERY_TYPE.as("status"),
                                substring(EXCEPTION, 1, MAX_ERROR).as("error"))
                        .from(system(QUERY_LOG))
                        .where(EVENT_DATE.ge(since.toString()))
                        .and(QUERY_TYPE.in(ENDED))
                        .and(INITIAL.eq(1))
                        .and(person == null
                                ? LOG_COMMENT.startsWith(ClickHouse.EDITOR)
                                : LOG_COMMENT.eq(ClickHouse.editorComment(person)))
                        .orderBy(EVENT_MICROS.desc())
                        .limit(limit),
                QueryRun.class);
    }

    public ServingHealth health() {
        return new ServingHealth(activity(), residentMemory(), failedRefreshes());
    }

    private ServingHealth.Activity activity() {
        Field<String> from = function(
                "subtractHours", String.class, function("now", String.class), inline(HEALTH_WINDOW_HOURS));
        return serving.fetchInto(
                        select(
                                        count().as(QUERIES),
                                        count().filterWhere(QUERY_TYPE.ne(FINISHED)).as("failed"),
                                        avg(DURATION).as("averageMs"),
                                        max(DURATION).as("slowestMs"))
                                .from(system(QUERY_LOG))
                                .where(EVENT_DATE.ge(function("toDate", String.class, from)))
                                .and(EVENT_TIME.ge(from))
                                .and(QUERY_TYPE.in(ENDED))
                                .and(INITIAL.eq(1)),
                        ServingHealth.Activity.class)
                .getFirst();
    }

    private long residentMemory() {
        Long bytes = serving.fetchValue(
                select(ClickHouseDsl.unsigned(VALUE))
                        .from(system("asynchronous_metrics"))
                        .where(METRIC.eq(RESIDENT_MEMORY)),
                Long.class);
        return bytes == null ? 0 : bytes;
    }

    private List<ServingHealth.FailedRefresh> failedRefreshes() {
        return serving.fetchInto(
                select(DATABASE, VIEW, substring(EXCEPTION, 1, MAX_ERROR).as("error"))
                        .from(system("view_refreshes"))
                        .where(EXCEPTION.ne(""))
                        .orderBy(DATABASE, VIEW),
                ServingHealth.FailedRefresh.class);
    }

    public List<SqlFunction> functions() {
        return serving.fetchInto(
                select(NAME, function("toBool", Boolean.class, IS_AGGREGATE).as("aggregate"))
                        .from(system("functions"))
                        .where(NAME.notLike(INTERNAL))
                        .orderBy(NAME),
                SqlFunction.class);
    }

    public Optional<FunctionDoc> functionDoc(String name) {
        return serving
                .fetchInto(
                        select(NAME, SYNTAX, DESCRIPTION, RETURNED_VALUE.as("returns"))
                                .from(system("functions"))
                                .where(NAME.eq(name))
                                .limit(1),
                        FunctionDoc.class)
                .stream()
                .findFirst();
    }

    record TableColumn(String database, String table, String name, String type) {

        String qualifiedTable() {
            return database + "." + table;
        }

        SchemaColumn schema() {
            return new SchemaColumn(name, type);
        }
    }
}
