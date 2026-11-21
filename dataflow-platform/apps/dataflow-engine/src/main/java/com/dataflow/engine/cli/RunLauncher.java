package com.dataflow.engine.cli;

import com.dataflow.engine.core.flows.FlowGraphs;
import com.dataflow.engine.core.run.FullRefresh;
import com.dataflow.engine.core.run.PipelineRun;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.engine.core.run.RunReport;
import com.dataflow.engine.core.tower.TowerReporter;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.PipelineSpecs;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RunLauncher {

    private static final String FULL_REFRESH_LABEL = "full_refresh";

    private final PipelineSpecs plans;
    private final RunContexts contexts;
    private final RunLabels labels;
    private final FlowGraphs graphs;
    private final TowerReporter tower;

    public RunReport launch(String dataset, String partition, String trigger, boolean fullRefresh) {
        PipelineSpec plan = plans.get(dataset);
        String runId = tower.runId().orElseGet(() -> UUID.randomUUID().toString());
        String key = partition != null ? partition : tower.partitionKey().orElse(null);
        RunContext context = contexts.create(plan, runId, key);
        Map<String, Object> runLabels = labels.labels();
        if (fullRefresh) {
            FullRefresh.reset(context);
            runLabels.put(FULL_REFRESH_LABEL, Boolean.TRUE.toString());
        }
        return new PipelineRun(context, graphs.forPlan(plan), tower, trigger, runLabels).run();
    }
}
