package com.dataflow.workspace.catalog;

import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import org.jooq.CommonTableExpression;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record4;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dataflow.workspace.serving.ClickHouseDsl.column;
import static com.dataflow.workspace.serving.ClickHouseDsl.dayUtc;
import static com.dataflow.workspace.serving.ClickHouseDsl.firstMatch;
import static com.dataflow.workspace.serving.ClickHouseDsl.fromEpochMillis;
import static com.dataflow.workspace.serving.ClickHouseDsl.isoUtc;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonHas;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonLong;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonRaw;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonText;
import static com.dataflow.workspace.serving.ClickHouseDsl.jsonUnsigned;
import static com.dataflow.workspace.serving.ClickHouseDsl.unsigned;
import static com.dataflow.workspace.serving.ClickHouseDsl.unsignedOrZero;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.regexpReplaceFirst;
import static org.jooq.impl.DSL.select;
import static org.jooq.impl.DSL.sum;
import static org.jooq.impl.DSL.with;

@Component
public class DeltaLog {

    static final String TABLE_KEY =
            "^.*?((?:bronze|silver|history|quarantine|reference|gold|system/events)/[a-z][a-z0-9_]*(?:/[a-z][a-z0-9_]*)?/)_delta_log/[^/]+$";
    static final String COMMITS = "_delta_log/*.json";
    static final String VERSION = "^(\\d+)\\.json$";
    private static final String COMMIT_INFO = "commitInfo";
    private static final String OPERATION_METRICS = "operationMetrics";
    private static final String ADD = "add";
    private static final String REMOVE = "remove";
    private static final String FILE_PATH = "path";
    private static final String SIZE = "size";
    private static final String BYTES = "bytes";
    private static final String ADDED = "added";
    private static final String REMOVED = "removed";
    private static final String VACUUMED = "vacuumed";
    private static final String DATA_FILES = "*.parquet";
    private static final Field<String> PATH = column("_path", String.class);
    private static final Field<String> FILE = column("_file", String.class);
    private static final Field<Object> TIME = column("_time", Object.class);
    private static final Field<String> JSON = column("json", String.class);

    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final long smallFileBytes;

    public DeltaLog(ClickHouse serving, LakeFunctions lake, WorkspaceProperties properties) {
        this.serving = serving;
        this.lake = lake;
        this.smallFileBytes = properties.catalog().smallFile().toBytes();
    }

    public Map<String, TableLog> tables() {
        Field<String> key = regexpReplaceFirst(PATH, TABLE_KEY, "\\1").as("key");
        Field<Integer> commits = count().as("commits");
        Field<String> updated = isoUtc(max(TIME)).as("updatedAt");
        return serving
                .fetchInto(
                        select(key, commits, updated)
                                .from(lake.objects("**/" + COMMITS))
                                .groupBy(key),
                        TableLog.class)
                .stream()
                .collect(Collectors.toUnmodifiableMap(TableLog::key, Function.identity()));
    }

    public List<Commit> commits(String tableKey, int limit) {
        Field<Long> version = unsigned(firstMatch(FILE, VERSION)).as("version");
        return serving.fetchInto(
                select(
                                version,
                                fromEpochMillis(jsonLong(JSON, COMMIT_INFO, "timestamp"))
                                        .as("timestamp"),
                                jsonText(JSON, COMMIT_INFO, "operation").as("operation"),
                                jsonRaw(JSON, COMMIT_INFO, "operationParameters")
                                        .as("parameters"),
                                jsonRaw(JSON, COMMIT_INFO, OPERATION_METRICS).as("metrics"),
                                jsonText(JSON, COMMIT_INFO, "engineInfo").as("engine"))
                        .from(lake.jsonDocuments(tableKey + COMMITS))
                        .where(jsonHas(JSON, COMMIT_INFO))
                        .orderBy(version.desc())
                        .limit(limit),
                Commit.class);
    }

    public LiveFiles liveFiles(String tableKey) {
        CommonTableExpression<Record4<String, Long, String, Long>> log = name("log")
                .fields(ADDED, SIZE, REMOVED, VACUUMED)
                .as(select(
                                jsonText(JSON, ADD, FILE_PATH),
                                jsonUnsigned(JSON, ADD, SIZE),
                                jsonText(JSON, REMOVE, FILE_PATH),
                                unsignedOrZero(jsonText(JSON, COMMIT_INFO, OPERATION_METRICS, "sizeOfDataToDelete")))
                        .from(lake.jsonDocuments(tableKey + COMMITS)));
        Field<String> added = log.field(ADDED, String.class);
        Field<Long> size = log.field(SIZE, Long.class);
        Field<String> removed = log.field(REMOVED, String.class);
        Field<Long> vacuumed = log.field(VACUUMED, Long.class);
        assert added != null;
        assert removed != null;
        assert size != null;
        assert vacuumed != null;
        Condition live = added.ne("").and(added.notIn(select(removed).from(log).where(removed.ne(""))));
        return serving.fetchInto(
                        with(log)
                                .select(
                                        count().filterWhere(live).as("files"),
                                        sum(size).filterWhere(live).as(BYTES),
                                        count().filterWhere(live.and(size.lt(smallFileBytes)))
                                                .as("smallFiles"),
                                        sum(vacuumed).as("reclaimedBytes"))
                                .from(log),
                        LiveFiles.class)
                .getFirst();
    }

    public long storedBytes(String tableKey) {
        Long stored = serving.fetchValue(
                select(sum(LakeFields.OBJECT_SIZE)).from(lake.objects(tableKey + DATA_FILES)), Long.class);
        return stored == null ? 0 : stored;
    }

    public List<SizeChange> sizeChanges(String tableKey) {
        Field<String> day = dayUtc(TIME).as("day");
        return serving.fetchInto(
                select(
                                day,
                                sum(jsonUnsigned(JSON, ADD, SIZE))
                                        .minus(sum(jsonUnsigned(JSON, REMOVE, SIZE)))
                                        .as(BYTES))
                        .from(lake.jsonDocuments(tableKey + COMMITS))
                        .groupBy(day)
                        .orderBy(day),
                SizeChange.class);
    }
}
