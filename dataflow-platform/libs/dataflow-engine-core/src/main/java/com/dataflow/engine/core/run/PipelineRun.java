package com.dataflow.engine.core.run;

import com.dataflow.engine.core.events.RunEvent;
import com.dataflow.engine.core.tower.TowerReporter;
import com.dataflow.spec.lake.EventTypes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Slf4j
@SuppressWarnings("resource")
public class PipelineRun {

    public static final String MDC_DATASET = "dataset";
    public static final String MDC_RUN = "runId";
    public static final String MDC_FLOW = "flow";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SCHEDULER_POOL = "spark.scheduler.pool";
    private static final String UPSTREAM_FAILED = "an upstream flow failed";

    private final RunContext context;
    private final FlowGraph graph;
    private final TowerReporter tower;
    private final String trigger;
    private final Map<String, Object> labels;

    public PipelineRun(RunContext context, FlowGraph graph, TowerReporter tower, String trigger) {
        this(context, graph, tower, trigger, Map.of());
    }

    public PipelineRun(
            RunContext context, FlowGraph graph, TowerReporter tower, String trigger, Map<String, ?> labels) {
        this.context = context;
        this.graph = graph;
        this.tower = tower;
        this.trigger = trigger;
        this.labels = Map.<String, Object>copyOf(labels);
    }

    private static Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    static String json(Map<String, Object> details) {
        try {
            return JSON.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public RunReport run() {
        MDC.put(MDC_DATASET, context.dataset());
        MDC.put(MDC_RUN, context.runId());
        Instant started = context.clock().instant();
        log.info(
                "Run {} of {} started: {} flows in {} levels",
                context.runId(),
                context.dataset(),
                graph.flows().size(),
                graph.levels().size());
        context.events().record(runEvent(FlowStatus.RUNNING, started, null, runDetails()));

        List<FlowOutcome> outcomes = runLevels();
        FlowStatus status = outcomes.stream().anyMatch(outcome -> outcome.status() != FlowStatus.COMPLETED)
                ? FlowStatus.FAILED
                : FlowStatus.COMPLETED;
        Instant ended = context.clock().instant();
        Map<String, Object> summary = runDetails();
        summary.put("flows", outcomes.stream().map(FlowOutcome::flow).toList());
        context.events().record(runEvent(status, started, ended, summary));
        log.info(
                "Run {} of {} {} in {} ms",
                context.runId(),
                context.dataset(),
                status == FlowStatus.COMPLETED ? "completed" : "failed",
                Duration.between(started, ended).toMillis());
        MDC.remove(MDC_RUN);
        MDC.remove(MDC_DATASET);
        return new RunReport(context.runId(), context.dataset(), status, List.copyOf(outcomes));
    }

    private List<FlowOutcome> runLevels() {
        List<FlowOutcome> outcomes = Collections.synchronizedList(new ArrayList<>());
        Set<String> skipped = new HashSet<>();
        for (List<Flow> level : graph.levels()) {
            List<Flow> runnable = new ArrayList<>();
            for (Flow flow : level) {
                if (skipped.contains(flow.name())) {
                    outcomes.add(skip(flow));
                } else {
                    runnable.add(flow);
                }
            }
            for (FlowOutcome outcome : runLevel(runnable)) {
                outcomes.add(outcome);
                if (outcome.status() == FlowStatus.FAILED) {
                    skipped.addAll(graph.downstreamOf(outcome.flow()));
                }
            }
        }
        return outcomes;
    }

    private FlowOutcome skip(Flow flow) {
        FlowOutcome outcome = new FlowOutcome(flow.name(), FlowStatus.SKIPPED, null, null, null, UPSTREAM_FAILED);
        record(outcome);
        return outcome;
    }

    private List<FlowOutcome> runLevel(List<Flow> level) {
        if (level.size() <= 1) {
            return level.stream().map(this::runFlow).toList();
        }
        Map<String, String> mdc = MDC.getCopyOfContextMap();
        try (ExecutorService pool = Executors.newFixedThreadPool(level.size())) {
            List<Future<FlowOutcome>> futures = level.stream()
                    .map(flow -> pool.submit(() -> {
                        MDC.setContextMap(mdc);
                        return runFlow(flow);
                    }))
                    .toList();
            List<FlowOutcome> outcomes = new ArrayList<>();
            for (Future<FlowOutcome> future : futures) {
                outcomes.add(future.get());
            }
            return outcomes;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Run interrupted", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    private FlowOutcome runFlow(Flow flow) {
        MDC.put(MDC_FLOW, flow.name());
        context.spark().sparkContext().setLocalProperty(SCHEDULER_POOL, flow.name());
        Instant started = context.clock().instant();
        FlowOutcome outcome;
        try {
            FlowResult result = flow.run(context);
            outcome = new FlowOutcome(
                    flow.name(), FlowStatus.COMPLETED, started, context.clock().instant(), result, null);
        } catch (FlowFailedException e) {
            outcome = new FlowOutcome(
                    flow.name(), FlowStatus.FAILED, started, context.clock().instant(), e.partial(), e.getMessage());
        } catch (RuntimeException e) {
            log.error("Flow {} failed", flow.name(), e);
            outcome = new FlowOutcome(
                    flow.name(),
                    FlowStatus.FAILED,
                    started,
                    context.clock().instant(),
                    null,
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            context.spark().sparkContext().setLocalProperty(SCHEDULER_POOL, null);
        }
        record(outcome);
        report(outcome);
        FlowResult result = outcome.result();
        log.info(
                "Flow {} {} in {} ms: {} rows in, {} out, {} dropped{}",
                flow.name(),
                outcome.status(),
                Duration.between(outcome.startedAt(), outcome.endedAt()).toMillis(),
                result == null ? 0 : result.rowsIn(),
                result == null ? 0 : result.rowsOut(),
                result == null ? 0 : result.rowsDropped(),
                outcome.error() == null ? "" : ": " + outcome.error());
        MDC.remove(MDC_FLOW);
        return outcome;
    }

    private void report(FlowOutcome outcome) {
        FlowResult result = outcome.result();
        if (result == null) {
            return;
        }
        if (outcome.status() == FlowStatus.COMPLETED) {
            result.materializations().forEach(m -> tower.materialized(m.assetKey(), m.metadata()));
        }
        result.checks()
                .forEach(check -> tower.checked(
                        check.assetKey(), check.name(), check.passed(), check.severity(), check.metadata()));
    }

    private void record(FlowOutcome outcome) {
        FlowResult result = outcome.result();
        List<RunEvent> events = new ArrayList<>();
        Map<String, Object> details = new LinkedHashMap<>();
        if (result != null) {
            details.putAll(result.details());
        }
        if (outcome.error() != null) {
            details.put("error", outcome.error());
        }
        Rows rows = result == null ? Rows.NONE : new Rows(result.rowsIn(), result.rowsOut(), result.rowsDropped());
        events.add(event(EventTypes.FLOW_PROGRESS, outcome, rows, details));
        if (result != null) {
            for (CheckResult check : result.checks()) {
                Map<String, Object> checkDetails = new LinkedHashMap<>(check.metadata());
                checkDetails.put(EventTypes.EXPECTATION, check.name());
                checkDetails.put("passed", check.passed());
                Rows checked = new Rows(
                        asLong(check.metadata().get("evaluated")), null, asLong(check.metadata().get("failing_rows")));
                events.add(event(EventTypes.EXPECTATION, outcome, checked, checkDetails));
            }
        }
        try {
            context.events().record(events.toArray(RunEvent[]::new));
        } catch (RuntimeException e) {
            log.warn("Could not record the {} event of flow {}: {}", outcome.status(), outcome.flow(), e.getMessage());
        }
    }

    private Map<String, Object> runDetails() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("trigger", trigger);
        details.putAll(new TreeMap<>(labels));
        return details;
    }

    private RunEvent runEvent(FlowStatus status, Instant started, Instant ended, Map<String, Object> details) {
        return event(EventTypes.RUN, null, status.name(), started, ended, Rows.NONE, details);
    }

    private RunEvent event(String type, FlowOutcome outcome, Rows rows, Map<String, Object> details) {
        return event(type, outcome.flow(), outcome.status().name(), outcome.startedAt(), outcome.endedAt(), rows, details);
    }

    private RunEvent event(
            String type, String flow, String status, Instant started, Instant ended, Rows rows, Map<String, Object> details) {
        return new RunEvent(
                UUID.randomUUID().toString(),
                context.clock().instant(),
                context.runId(),
                context.dataset(),
                context.plan().version(),
                trigger,
                context.partition(),
                type,
                flow,
                status,
                started,
                ended,
                rows.in(),
                rows.out(),
                rows.dropped(),
                json(details));
    }

    private record Rows(Long in, Long out, Long dropped) {

        static final Rows NONE = new Rows(null, null, null);
    }
}
