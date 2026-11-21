package com.dataflow.engine.core.flows;

import com.dataflow.engine.core.run.AssetKeys;
import com.dataflow.engine.core.run.Flow;
import com.dataflow.engine.core.run.FlowResult;
import com.dataflow.engine.core.run.RunContext;
import com.dataflow.engine.core.serving.ServingDdl;
import com.dataflow.engine.core.serving.Warehouse;
import com.dataflow.spec.PipelineSpec;
import com.dataflow.spec.ServeSpec;
import com.dataflow.spec.lake.FlowNames;
import com.dataflow.spec.lake.Layer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class ServingFlow implements Flow {

    private final Warehouse warehouse;
    private final ServingDdl ddl;
    private final Set<String> inputs;

    private static List<String> columns(List<List<String>> description) {
        return description.stream()
                .map(column -> column.get(0) + " " + column.get(1))
                .toList();
    }

    @Override
    public String name() {
        return FlowNames.SERVING;
    }

    @Override
    public Set<String> inputs() {
        return inputs;
    }

    @Override
    public FlowResult run(RunContext context) {
        PipelineSpec plan = context.plan();
        warehouse.execute(ddl.createDatabase());
        FlowResult result = new FlowResult();
        long served = 0;
        for (ServeSpec serve : plan.serve()) {
            String source = serve.servesCurrent()
                    ? context.lake().table(Layer.HISTORY, context.dataset())
                    : context.lake().table(Layer.GOLD, context.dataset(), serve.goldName());
            if (!context.tables().exists(source)) {
                log.info("Serving table {} skipped: {} does not exist yet", serve.table(), source);
                continue;
            }
            long rows = serve.mode() == ServeSpec.Mode.VIEW ? view(context, serve) : copy(context, serve);
            served += rows;
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("mode", serve.mode().name().toLowerCase());
            metadata.put("table", ddl.table(serve.table()));
            metadata.put("rows", rows);
            result.materialized(AssetKeys.of(context.dataset(), FlowNames.SERVING, serve.table()), metadata);
        }
        List<String> logged = context.plans().all().stream()
                .map(PipelineSpec::dataset)
                .filter(dataset -> context.tables().exists(context.lake().events(dataset)))
                .toList();
        if (!logged.isEmpty()) {
            warehouse.execute(ddl.eventsView(logged));
        }
        return result.rows(served, served, 0).detail("event_logs", logged);
    }

    private long view(RunContext context, ServeSpec serve) {
        warehouse.execute(ddl.view(context.dataset(), serve));
        return count(serve);
    }

    private long copy(RunContext context, ServeSpec serve) {
        warehouse.execute(ddl.copyTable(context.dataset(), serve));
        if (!columns(warehouse.query(ddl.describeSource(context.dataset(), serve)))
                .equals(columns(warehouse.query(ddl.describeTable(serve))))) {
            log.info("Serving table {} changed shape; rebuilding the copy", serve.table());
            ddl.dropCopy(serve).forEach(warehouse::execute);
            warehouse.execute(ddl.copyTable(context.dataset(), serve));
        }
        warehouse.execute(ddl.copyRefresher(context.dataset(), serve));
        ddl.refresh(serve).forEach(warehouse::execute);
        return count(serve);
    }

    private long count(ServeSpec serve) {
        return Long.parseLong(warehouse.query(ddl.count(serve)).get(0).get(0));
    }
}
