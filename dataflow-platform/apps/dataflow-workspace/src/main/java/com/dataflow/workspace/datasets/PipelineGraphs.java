package com.dataflow.workspace.datasets;

import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.ReferenceSpec;
import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.TableGraph;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.workspace.catalog.Catalog;
import com.dataflow.workspace.runs.FlowRun;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class PipelineGraphs {

    private static final String NOT_STORED = "not stored";

    private PipelineGraphs() {}

    public static PipelineGraph of(PipelineSpec spec, Map<String, FlowRun> flows) {
        List<GraphNode> nodes = new ArrayList<>();
        List<PipelineGraph.Edge> edges = new ArrayList<>();
        if (spec.ingests()) {
            spine(spec, flows, nodes, edges);
        }
        spec.inputs().forEach((alias, table) ->
                nodes.add(new GraphNode(id(Catalog.INPUT, alias), alias, Catalog.INPUT, null, null, null, null, table)));
        for (ReferenceSpec reference : spec.references()) {
            String id = id(Catalog.REFERENCE, reference.name());
            nodes.add(node(id, reference.name(), Catalog.REFERENCE, FlowNames.REFERENCES, flows, FlowRun::rowsOut, null));
        }
        TableGraph tables = spec.tables();
        for (GoldSpec gold : spec.gold()) {
            String id = id(Catalog.GOLD, gold.name());
            nodes.add(gold.materialize()
                    ? node(id, gold.name(), Catalog.GOLD, FlowNames.gold(gold.name()), flows, FlowRun::rowsOut, null)
                    : new GraphNode(id, gold.name(), Catalog.GOLD, null, null, null, null, NOT_STORED));
            tables.reads(gold.name()).forEach(relation -> tables.kind(relation)
                    .ifPresent(kind -> edges.add(new PipelineGraph.Edge(nodeOf(kind, relation), id))));
        }
        for (ServeSpec serve : spec.serve()) {
            String id = id(Catalog.SERVING, serve.table());
            nodes.add(node(
                    id,
                    serve.table(),
                    Catalog.SERVING,
                    FlowNames.SERVING,
                    flows,
                    f -> null,
                    serve.mode().name().toLowerCase()));
            edges.add(new PipelineGraph.Edge(
                    serve.servesCurrent() ? Catalog.HISTORY : id(Catalog.GOLD, serve.goldName()), id));
        }
        return new PipelineGraph(nodes, edges);
    }

    private static void spine(
            PipelineSpec spec, Map<String, FlowRun> flows, List<GraphNode> nodes, List<PipelineGraph.Edge> edges) {
        nodes.add(node(Catalog.LANDING, Catalog.LANDING, Catalog.LANDING, FlowNames.INGEST, flows, FlowRun::rowsOut, "files"));
        nodes.add(node(Catalog.BRONZE, Catalog.BRONZE, Catalog.BRONZE, FlowNames.BRONZE, flows, FlowRun::rowsOut, null));
        nodes.add(node(
                Catalog.SILVER,
                Catalog.SILVER,
                Catalog.SILVER,
                FlowNames.SILVER,
                flows,
                FlowRun::rowsOut,
                spec.silver().expectations().size() + " rules"));
        edges.add(new PipelineGraph.Edge(Catalog.LANDING, Catalog.BRONZE));
        edges.add(new PipelineGraph.Edge(Catalog.BRONZE, Catalog.SILVER));
        if (spec.quarantines()) {
            nodes.add(node(
                    Catalog.QUARANTINE, Catalog.QUARANTINE, Catalog.QUARANTINE, FlowNames.SILVER, flows, FlowRun::rowsDropped, null));
            edges.add(new PipelineGraph.Edge(Catalog.SILVER, Catalog.QUARANTINE));
        }
        nodes.add(node(
                Catalog.HISTORY,
                Catalog.HISTORY,
                Catalog.HISTORY,
                FlowNames.HISTORY,
                flows,
                FlowRun::rowsOut,
                "SCD2 by " + String.join(", ", spec.history().keys())));
        edges.add(new PipelineGraph.Edge(Catalog.SILVER, Catalog.HISTORY));
    }

    private static String nodeOf(TableGraph.Kind kind, String relation) {
        return switch (kind) {
            case CURRENT -> Catalog.HISTORY;
            case REFERENCE -> id(Catalog.REFERENCE, relation);
            case INPUT -> id(Catalog.INPUT, relation);
            case TABLE -> id(Catalog.GOLD, relation);
        };
    }

    private static String id(String layer, String name) {
        return layer + "." + name;
    }

    private static GraphNode node(
            String id,
            String label,
            String layer,
            String flow,
            Map<String, FlowRun> flows,
            Function<FlowRun, Long> rows,
            String note) {
        FlowRun run = flows.get(flow);
        return new GraphNode(
                id,
                label,
                layer,
                flow,
                run == null ? null : run.status(),
                run == null ? null : rows.apply(run),
                run == null ? null : run.durationMs(),
                note);
    }
}
