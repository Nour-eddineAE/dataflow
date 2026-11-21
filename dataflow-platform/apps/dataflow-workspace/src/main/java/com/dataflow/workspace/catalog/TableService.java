package com.dataflow.workspace.catalog;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.ServeSpec;
import com.dataflow.workspace.serving.ClickHouse;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.QueryResult;
import com.dataflow.workspace.serving.SchemaColumn;
import com.dataflow.workspace.serving.SystemTables;
import com.dataflow.workspace.specs.SpecStore;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectWhereStep;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.selectCount;
import static org.jooq.impl.DSL.selectFrom;

@Service
public class TableService {

    private static final int MAX_COMMITS = 200;
    private static final String SOURCE = "source";

    private final SpecStore specs;
    private final LakeIndex index;
    private final ClickHouse serving;
    private final LakeFunctions lake;
    private final DeltaLog log;
    private final LandingFiles landing;
    private final SystemTables system;

    public TableService(
            SpecStore specs,
            LakeIndex index,
            ClickHouse serving,
            LakeFunctions lake,
            DeltaLog log,
            LandingFiles landing,
            SystemTables system) {
        this.specs = specs;
        this.index = index;
        this.serving = serving;
        this.lake = lake;
        this.log = log;
        this.landing = landing;
        this.system = system;
    }

    private static SelectWhereStep<Record> rowsOf(Select<Record> source) {
        return selectFrom(source.asTable(SOURCE));
    }

    private static ColumnInfo describe(String id, QueryResult.Column column, ColumnInfo role) {
        if (role == null) {
            return new ColumnInfo(
                    column.name(), column.type(), id.equals(Catalog.BRONZE) ? "raw" : "output", null, null);
        }
        return new ColumnInfo(column.name(), column.type(), role.role(), role.expression(), role.description());
    }

    private static List<ColumnInfo> declared(PipelineSpec spec, String id) {
        Map<String, ColumnInfo> roles = ColumnRoles.of(spec, id);
        if (id.equals(Catalog.BRONZE)
                || id.startsWith(Catalog.GOLD)
                || id.startsWith(Catalog.REFERENCE)
                || id.equals(Catalog.EVENTS)) {
            return List.of();
        }
        return spec.silver().columns().stream()
                .map(column -> roles.get(column.name()))
                .toList();
    }

    public CatalogTree tree() {
        List<CatalogTree.DatasetTables> datasets = specs.all().stream()
                .map(stored -> new CatalogTree.DatasetTables(
                        stored.dataset(), stored.valid(), stored.valid() ? tables(stored.dataset()) : List.of()))
                .toList();
        return new CatalogTree(datasets, served());
    }

    public List<LakeTable> tables(String dataset) {
        return index.locate(Catalog.tables(specs.spec(dataset)));
    }

    public LakeTable table(String dataset, String id) {
        return tables(dataset).stream()
                .filter(table -> table.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new UnknownTableException(dataset, id));
    }

    public List<ServedTable> served() {
        Set<String> existing = system.tables(serving.database());
        List<ServedTable> served = new ArrayList<>();
        for (PipelineSpec spec : specs.valid()) {
            for (ServeSpec serve : spec.serve()) {
                served.add(new ServedTable(
                        spec.dataset(),
                        serve.table(),
                        serving.database() + "." + serve.table(),
                        serve.from(),
                        serve.mode().name().toLowerCase(),
                        serve.orderBy(),
                        existing.contains(serve.table())));
            }
        }
        return served;
    }

    public Map<String, List<SchemaColumn>> schema() {
        List<String> databases = new ArrayList<>();
        databases.add(serving.database());
        specs.valid().forEach(spec -> databases.add(Catalog.database(spec.dataset())));
        return system.columnsByTable(databases);
    }

    public TableDetail detail(String dataset, String id) {
        PipelineSpec spec = specs.spec(dataset);
        LakeTable table = table(dataset, id);
        if (!table.delta()) {
            return new TableDetail(dataset, table, List.of(), null, null, null, null);
        }
        if (!table.exists()) {
            return new TableDetail(dataset, table, declared(spec, id), null, null, null, null);
        }
        Select<Record> source = Catalog.source(lake, table);
        Map<String, ColumnInfo> roles = ColumnRoles.of(spec, id);
        List<ColumnInfo> columns = serving.fetch(rowsOf(source).limit(0), 0).columns().stream()
                .map(column -> describe(id, column, roles.get(column.name())))
                .toList();
        Long rows = serving.fetchValue(selectCount().from(source.asTable(SOURCE)), Long.class);
        LiveFiles files = log.liveFiles(table.key());
        List<Commit> last = log.commits(table.key(), 1);
        return new TableDetail(
                dataset, table, columns, rows, files.files(), files.bytes(), last.isEmpty() ? null : last.getFirst());
    }

    public TableStorage storage(LakeTable table) {
        LiveFiles files = log.liveFiles(table.key());
        return new TableStorage(
                table.id(),
                table.layer(),
                table.commits(),
                files.files(),
                files.bytes(),
                files.smallFiles(),
                Math.max(0, log.storedBytes(table.key()) - files.bytes()),
                files.reclaimedBytes(),
                table.updatedAt(),
                log.sizeChanges(table.key()));
    }

    public QueryResult sample(String dataset, String id, int limit) {
        if (!table(dataset, id).delta()) {
            return landing(dataset);
        }
        return serving.fetch(rowsOf(source(dataset, id)).limit(limit), limit);
    }

    public Select<Record> source(String dataset, String id) {
        return Catalog.source(lake, written(dataset, id));
    }

    public LakeTable written(String dataset, String id) {
        LakeTable table = table(dataset, id);
        if (!table.delta() || !table.exists()) {
            throw new UnknownTableException(dataset, id + " (not written yet)");
        }
        return table;
    }

    public List<Commit> history(String dataset, String id) {
        LakeTable table = table(dataset, id);
        return table.exists() ? log.commits(table.key(), MAX_COMMITS) : List.of();
    }

    public QueryResult landing(String dataset) {
        return landing.list(table(dataset, Catalog.LANDING).key());
    }

    public QueryResult servedSample(String table, int limit) {
        return serving.fetch(servedSource(table).limit(limit), limit);
    }

    public SelectWhereStep<Record> servedSource(String table) {
        ServedTable served = served().stream()
                .filter(candidate -> candidate.table().equals(table))
                .findFirst()
                .orElseThrow(() -> new UnknownTableException(serving.database(), table));
        return selectFrom(DSL.table(name(serving.database(), served.table())));
    }

    public QueryResult servedColumns(String table) {
        return system.columns(serving.database(), table);
    }
}
