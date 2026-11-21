package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.quality.Expectations;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowGraph;
import com.dataflow.engine.core.serving.ServingDdl;
import com.dataflow.engine.core.serving.Warehouse;
import com.dataflow.spec.GoldSpec;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.TableGraph;

import com.dataflow.spec.lake.FlowNames;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FlowGraphs {

    private final Sources sources;
    private final Warehouse warehouse;
    private final ServingDdl ddl;
    private final Maintenance maintenance;

    public FlowGraphs(Sources sources, Warehouse warehouse, ServingDdl ddl, Maintenance maintenance) {
        this.sources = sources;
        this.warehouse = warehouse;
        this.ddl = ddl;
        this.maintenance = maintenance;
    }

    public FlowGraph forPlan(PipelineSpec plan) {
        List<Flow> flows = new ArrayList<>();
        Set<String> landed = new HashSet<>();
        if (plan.ingests()) {
            flows.add(new IngestFlow(sources));
            flows.add(new BronzeFlow());
            flows.add(new SilverFlow(new Expectations(), plan.silverReadsReferences()));
            flows.add(new HistoryFlow());
            landed.add(FlowNames.HISTORY);
        }
        if (!plan.references().isEmpty()) {
            flows.add(new ReferencesFlow(sources));
            landed.add(FlowNames.REFERENCES);
        }
        TableGraph tables = plan.tables();
        for (GoldSpec gold : plan.materialized()) {
            flows.add(new GoldFlow(gold, goldInputs(tables, gold)));
            landed.add(FlowNames.gold(gold.name()));
        }
        Set<String> beforeMaintenance = new HashSet<>(landed);
        if (warehouse != null && !plan.serve().isEmpty()) {
            flows.add(new ServingFlow(warehouse, ddl, Set.copyOf(landed)));
            beforeMaintenance = Set.of(FlowNames.SERVING);
        }
        if (maintenance != null) {
            flows.add(new MaintenanceFlow(
                    maintenance.interval(), maintenance.retentionHours(), Set.copyOf(beforeMaintenance)));
        }
        return new FlowGraph(flows);
    }

    private static Set<String> goldInputs(TableGraph tables, GoldSpec gold) {
        Set<String> needs = new HashSet<>();
        for (String source : tables.sources(gold.name())) {
            tables.kind(source).ifPresent(kind -> {
                switch (kind) {
                    case CURRENT -> needs.add(FlowNames.HISTORY);
                    case REFERENCE -> needs.add(FlowNames.REFERENCES);
                    case TABLE -> needs.add(FlowNames.gold(source));
                    case INPUT -> {}
                }
            });
        }
        return needs;
    }

    public record Maintenance(Duration interval, int retentionHours) {}
}
