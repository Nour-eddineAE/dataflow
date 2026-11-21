package com.dataflow.workspace.lineage;

import com.dataflow.spec.ColumnSpec;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.ReferenceSpec;
import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.SystemColumns;
import com.dataflow.spec.TableGraph;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.lineage.Lineage.Column;
import com.dataflow.workspace.lineage.Lineage.Edge;
import com.dataflow.workspace.lineage.Lineage.Table;
import com.dataflow.workspace.lineage.SqlReferences.Output;
import com.dataflow.workspace.lineage.SqlReferences.Query;
import com.dataflow.workspace.lineage.SqlReferences.Reference;
import com.dataflow.workspace.lineage.StepLineage.Open;
import com.dataflow.workspace.lineage.StepLineage.Origin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ColumnLineage {

    private static final String SYSTEM = "system";
    private static final String OUTPUT = "output";

    private final PipelineSpec spec;
    private final TableGraph graph;
    private final Map<String, Map<String, Column>> tables = new LinkedHashMap<>();
    private final Map<String, String> layers = new LinkedHashMap<>();
    private final Map<String, List<Open>> open = new HashMap<>();
    private final Set<Edge> edges = new LinkedHashSet<>();
    private final List<String> warnings = new ArrayList<>();

    private ColumnLineage(PipelineSpec spec) {
        this.spec = spec;
        this.graph = spec.tables();
    }

    public static Lineage of(PipelineSpec spec) {
        ColumnLineage lineage = new ColumnLineage(spec);
        lineage.build();
        return lineage.result();
    }

    private static String label(String id) {
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(dot + 1);
    }

    private static String layer(String id) {
        int dot = id.indexOf('.');
        return dot < 0 ? id : id.substring(0, dot);
    }

    private void build() {
        if (spec.ingests()) {
            ingestion();
        }
        for (ReferenceSpec reference : spec.references()) {
            table(Catalog.REFERENCE + "." + reference.name(), Catalog.REFERENCE);
        }
        for (GoldSpec gold : graph.ordered()) {
            gold(gold);
        }
        for (ServeSpec serve : spec.serve()) {
            served(serve);
        }
    }

    private void ingestion() {
        table(Catalog.BRONZE, Catalog.BRONZE);
        table(Catalog.SILVER, Catalog.SILVER);
        for (ColumnSpec column : spec.silver().columns()) {
            column(Catalog.SILVER, new Column(column.name(), column.type(), "declared", column.from()));
            for (Reference reference : references("silver." + column.name(), column.from())) {
                column(Catalog.BRONZE, new Column(reference.column(), "raw", "raw", null));
                edge(Catalog.BRONZE, reference.column(), Catalog.SILVER, column.name());
            }
        }
        for (ColumnSpec column : spec.silver().derive()) {
            Map<String, Column> known = tables.get(Catalog.SILVER);
            Set<Reference> inputs = references("derive." + column.name(), column.from());
            column(Catalog.SILVER, new Column(column.name(), column.type(), "derived", column.from()));
            for (Reference reference : inputs) {
                if (known.containsKey(reference.column())) {
                    edge(Catalog.SILVER, reference.column(), Catalog.SILVER, column.name());
                } else {
                    warnings.add("derive " + column.name() + reads(reference.column())
                            + "', which is not a silver column declared before it");
                }
            }
        }
        column(
                Catalog.SILVER,
                new Column(
                        SystemColumns.FACT_UNIQUE_ID,
                        "string",
                        SYSTEM,
                        "sha256(" + String.join(", ", spec.history().keys()) + ")"));
        spec.history().keys().forEach(key -> edge(Catalog.SILVER, key, Catalog.SILVER, SystemColumns.FACT_UNIQUE_ID));

        table(Catalog.CURRENT, Catalog.HISTORY);
        for (Column column : List.copyOf(tables.get(Catalog.SILVER).values())) {
            column(Catalog.CURRENT, column);
            edge(Catalog.SILVER, column.name(), Catalog.CURRENT, column.name());
        }
        column(Catalog.CURRENT, new Column(SystemColumns.START_AT, "timestamp", SYSTEM, "when this version began"));
    }

    private static String reads(String name) {
        return " reads '" + name;
    }

    private void goldWarning(String gold, String problem) {
        warnings.add("gold " + gold + problem);
    }

    private void gold(GoldSpec gold) {
        String id = Catalog.GOLD + "." + gold.name();
        table(id, Catalog.GOLD);
        if (gold.usesTransformation()) {
            return;
        }
        if (gold.usesSteps()) {
            steps(gold, id);
        } else if (!sql(gold, id)) {
            return;
        }
        column(id, new Column(SystemColumns.COMPUTED_AT, "timestamp", SYSTEM, "when the table was rebuilt"));
    }

    private void steps(GoldSpec gold, String id) {
        StepLineage.Tables view = new StepLineage.Tables(
                this::table, table -> List.copyOf(tables.getOrDefault(table, Map.of()).keySet()), this::complete);
        StepLineage.Result result = StepLineage.trace(gold, view, warnings);
        if (!result.open().isEmpty()) {
            open.put(id, result.open());
        }
        result.columns().forEach((name, origins) -> {
            column(id, new Column(name, null, OUTPUT, null));
            for (Origin origin : origins) {
                touch(origin.table(), origin.column());
                edge(origin.table(), origin.column(), id, name);
            }
        });
    }

    private boolean sql(GoldSpec gold, String id) {
        Query query;
        try {
            query = SqlReferences.query(gold.sql());
        } catch (RuntimeException e) {
            goldWarning(gold.name(), ": " + e.getMessage());
            return false;
        }
        Map<String, String> relations = query.relations();
        relations.values().stream()
                .filter(relation -> graph.kind(relation.toLowerCase(Locale.ROOT)).isEmpty())
                .distinct()
                .forEach(relation -> goldWarning(gold.name(), reads(relation) + "', which is not a table of " + spec.dataset()));
        String only = relations.size() == 1 ? relations.values().iterator().next() : ServeSpec.CURRENT;
        for (Output output : query.outputs()) {
            if (output.star()) {
                String source = output.inputs().isEmpty()
                        ? only
                        : relations.getOrDefault(output.inputs().getFirst().qualifier(), only);
                String table = table(source);
                if (table != null && tables.containsKey(table)) {
                    for (Column column : List.copyOf(tables.get(table).values())) {
                        column(id, column);
                        edge(table, column.name(), id, column.name());
                    }
                }
                if (table != null && !complete(table)) {
                    open.put(id, List.of(new Open(table, null)));
                }
                continue;
            }
            column(id, new Column(output.name(), null, OUTPUT, null));
            for (Reference input : output.inputs()) {
                String source = resolve(input, relations, gold.name());
                if (source != null) {
                    if (!source.equals(Catalog.CURRENT)) {
                        touch(source, input.column());
                    }
                    edge(source, input.column(), id, output.name());
                }
            }
        }
        return true;
    }

    private void served(ServeSpec serve) {
        String id = Catalog.SERVING + "." + serve.table();
        String source = serve.servesCurrent() ? Catalog.CURRENT : Catalog.GOLD + "." + serve.goldName();
        table(id, Catalog.SERVING);
        Map<String, Column> columns = tables.get(source);
        if (columns == null) {
            return;
        }
        for (Column column : List.copyOf(columns.values())) {
            column(id, column);
            edge(source, column.name(), id, column.name());
        }
    }

    private String resolve(Reference input, Map<String, String> relations, String gold) {
        if (input.qualifier() != null) {
            String relation = relations.get(input.qualifier());
            String table = relation == null ? null : table(relation);
            if (table == null) {
                goldWarning(gold, " reads " + input.qualifier() + "." + input.column() + " from an unknown table");
            }
            return table;
        }
        if (tables.getOrDefault(Catalog.CURRENT, Map.of()).containsKey(input.column())) {
            return Catalog.CURRENT;
        }
        List<String> others = relations.values().stream()
                .map(this::table)
                .filter(table -> table != null && !table.equals(Catalog.CURRENT))
                .distinct()
                .toList();
        if (others.size() == 1) {
            return others.getFirst();
        }
        if (relations.values().stream().allMatch(ServeSpec.CURRENT::equals)) {
            goldWarning(gold, reads(input.column()) + "', which current does not have");
        }
        return null;
    }

    private String table(String relation) {
        String name = relation.toLowerCase(Locale.ROOT);
        return graph
                .kind(name)
                .map(kind -> switch (kind) {
                    case CURRENT -> Catalog.CURRENT;
                    case REFERENCE -> Catalog.REFERENCE + "." + name;
                    case INPUT -> Catalog.INPUT + "." + name;
                    case TABLE -> Catalog.GOLD + "." + name;
                })
                .orElse(null);
    }

    private boolean complete(String table) {
        String layer = layer(table);
        return !open.containsKey(table) && !layer.equals(Catalog.INPUT) && !layer.equals(Catalog.REFERENCE);
    }

    private void touch(String table, String column) {
        String layer = layer(table);
        table(table, layer);
        if (tables.get(table).containsKey(column)) {
            return;
        }
        column(table, new Column(column, null, layer.equals(Catalog.GOLD) ? OUTPUT : layer, null));
        for (Origin origin : StepLineage.origins(open.getOrDefault(table, List.of()), column)) {
            touch(origin.table(), origin.column());
            edge(origin.table(), origin.column(), table, column);
        }
    }

    private Set<Reference> references(String where, String expression) {
        try {
            return SqlReferences.expression(expression);
        } catch (RuntimeException e) {
            warnings.add(where + ": " + e.getMessage());
            return Set.of();
        }
    }

    private void table(String id, String layer) {
        tables.putIfAbsent(id, new LinkedHashMap<>());
        layers.putIfAbsent(id, layer);
    }

    private void column(String table, Column column) {
        tables.get(table).putIfAbsent(column.name(), column);
    }

    private void edge(String fromTable, String fromColumn, String toTable, String toColumn) {
        edges.add(new Edge(fromTable, fromColumn, toTable, toColumn));
    }

    private Lineage result() {
        List<Table> result = new ArrayList<>();
        tables.forEach((id, columns) -> {
            if (!columns.isEmpty()) {
                result.add(new Table(id, layers.get(id), label(id), List.copyOf(columns.values())));
            }
        });
        return new Lineage(result, List.copyOf(edges), List.copyOf(warnings));
    }
}
