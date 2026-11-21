package com.dataflow.workspace.runs;

import com.dataflow.spec.PipelineSpec;
import com.dataflow.workspace.specs.SpecStore;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RunService {

    public static final String TASK_TAG = "workspace/task";
    public static final String PERSON_TAG = "workspace/person";
    public static final String DATASET_TAG = "dataset";
    private static final String MESSAGE = "message";
    private static final String PARTITION_TAG = "dagster/partition";
    private static final Map<String, String> TRIGGERS = triggers();
    private static final String JOB_SUFFIX = "_job";
    private static final int COMPARISON_WINDOW = 100;
    private static final String COMPLETED = "COMPLETED";
    private static final Map<String, String> TOWER_VERDICTS = Map.of("FAILURE", "FAILED", "CANCELED", "CANCELED");
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {};

    private final Tower tower;
    private final EventLog events;
    private final SpecStore specs;
    private final ObjectMapper json;

    public RunService(Tower tower, EventLog events, SpecStore specs, ObjectMapper json) {
        this.tower = tower;
        this.events = events;
        this.specs = specs;
        this.json = json;
    }

    static RunSummary summary(JsonNode run) {
        Map<String, String> tags = Tower.tagsOf(run);
        String job = run.path("jobName").asString();
        String dataset = tags.getOrDefault(
                DATASET_TAG, job.endsWith(JOB_SUFFIX) ? job.substring(0, job.length() - JOB_SUFFIX.length()) : null);
        Instant created = instant(run.path("creationTime"));
        Instant started = instant(run.path("startTime"));
        Instant ended = instant(run.path("endTime"));
        Long duration = started == null
                ? null
                : (ended == null ? Instant.now() : ended).toEpochMilli() - started.toEpochMilli();
        return new RunSummary(
                run.path("runId").asString(),
                dataset,
                job,
                run.path("status").asString(),
                trigger(tags),
                tags.get(PARTITION_TAG),
                created,
                started,
                ended,
                duration,
                tags.get(TASK_TAG),
                tags.get(PERSON_TAG));
    }

    private static Map<String, String> triggers() {
        Map<String, String> triggers = new LinkedHashMap<>();
        triggers.put("dagster/backfill", "backfill");
        triggers.put("dagster/schedule_name", "schedule");
        triggers.put("dagster/from_automation_condition", "inputs");
        triggers.put("dagster/sensor_name", "sensor");
        return Collections.unmodifiableMap(triggers);
    }

    static String trigger(Map<String, String> tags) {
        return TRIGGERS.entrySet().stream()
                .filter(trigger -> tags.containsKey(trigger.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("manual");
    }

    private static Instant instant(JsonNode seconds) {
        if (seconds.isMissingNode() || seconds.isNull()) {
            return null;
        }
        return EpochSeconds.instant(seconds.asDouble());
    }

    public List<RunSummary> runs(String dataset, int limit) {
        Map<String, Object> filter = dataset == null ? Map.of() : Map.of("pipelineName", dataset + JOB_SUFFIX);
        return tower.runs(filter, dataset == null ? limit * 2 : limit).stream()
                .map(RunService::summary)
                .filter(run -> run.task() == null)
                .limit(limit)
                .toList();
    }

    public RunSummary summary(String runId) {
        return summary(tower.run(runId));
    }

    public RunDetail detail(String runId) {
        JsonNode node = tower.run(runId);
        RunSummary run = summary(node);
        List<LogLine> log = new ArrayList<>();
        String error = null;
        for (JsonNode event : node.path("eventConnection").path("events")) {
            String type = event.path("__typename").asString();
            JsonNode failure = event.path("error");
            if (failure.has(MESSAGE)) {
                error = failure.path(MESSAGE).asString();
            }
            String level = event.path("level").asString();
            if ("DEBUG".equals(level) && !type.endsWith("FailureEvent")) {
                continue;
            }
            log.add(new LogLine(
                    event.path("timestamp").asString(),
                    level,
                    type.replace("Event", ""),
                    event.path(MESSAGE).asString()));
        }
        if (run.dataset() == null || specs.find(run.dataset()).isEmpty()) {
            return new RunDetail(run, List.of(), List.of(), log, error, null, null);
        }
        return new RunDetail(
                run,
                events.flows(run.dataset(), runId).stream().map(this::flow).toList(),
                events.expectations(run.dataset(), runId),
                log,
                error,
                events.runtime(run.dataset(), runId).orElse(null),
                events.sparkApp(run.dataset(), runId).orElse(null));
    }

    public RunComparison previous(String dataset, String runId) {
        List<EventRun> recent = events.runs(dataset, COMPARISON_WINDOW);
        int at = recent.stream().map(EventRun::runId).toList().indexOf(runId);
        if (at < 0) {
            return RunComparison.NONE;
        }
        return recent.subList(at + 1, recent.size()).stream()
                .filter(run -> COMPLETED.equals(run.status()))
                .findFirst()
                .map(before -> new RunComparison(
                        before.runId(),
                        events.flows(dataset, before.runId()).stream()
                                .map(this::flow)
                                .toList()))
                .orElse(RunComparison.NONE);
    }

    public Map<String, FlowRun> latestFlows(String dataset) {
        return events.latestFlows(dataset).stream()
                .map(this::flow)
                .collect(Collectors.toMap(FlowRun::flow, Function.identity(), (first, second) -> second));
    }

    public List<EventRun> eventRuns(String dataset, int limit) {
        List<EventRun> logged = events.runs(dataset, limit);
        if (logged.isEmpty()) {
            return logged;
        }
        Map<String, String> verdicts;
        try {
            verdicts = verdicts(dataset, limit);
        } catch (TowerException e) {
            return logged;
        }
        return logged.stream()
                .map(run -> verdicts.containsKey(run.runId()) ? run.withStatus(verdicts.get(run.runId())) : run)
                .toList();
    }

    public Map<String, String> verdicts(String dataset, int limit) {
        return runs(dataset, limit).stream()
                .filter(run -> TOWER_VERDICTS.containsKey(run.status()))
                .collect(Collectors.toMap(RunSummary::runId, run -> TOWER_VERDICTS.get(run.status())));
    }

    public Optional<RunRuntime> latestRuntime(String dataset) {
        return events.latestRuntime(dataset);
    }

    public List<FlowCell> flowCells(String dataset, int runs) {
        return events.flowCells(dataset, runs);
    }

    public String launch(String dataset, String partition, String person, boolean fullRefresh) {
        PipelineSpec spec = specs.spec(dataset);
        Map<String, String> tags = startedBy(person);
        tags.put(DATASET_TAG, dataset);
        if (fullRefresh) {
            tags.put("workspace/full-refresh", "true");
        }
        if (spec.partitioned()) {
            if (partition == null || partition.isBlank()) {
                throw new IllegalArgumentException(dataset + " is partitioned; pick a partition");
            }
            tags.put(PARTITION_TAG, partition);
        }
        return tower.launch(dataset + JOB_SUFFIX, tags, Map.of());
    }

    public String backfill(String dataset, List<String> partitions, String person) {
        PipelineSpec spec = specs.spec(dataset);
        if (!spec.partitioned()) {
            throw new IllegalArgumentException(dataset + " has no partitions to backfill");
        }
        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalArgumentException("Pick at least one partition");
        }
        return tower.backfill(dataset + JOB_SUFFIX, partitions, startedBy(person));
    }

    private static Map<String, String> startedBy(String person) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("workspace/launched", "true");
        tags.put(PERSON_TAG, person);
        return tags;
    }

    public void terminate(String runId) {
        tower.terminate(runId);
    }

    public Partitions partitions(String dataset) {
        PipelineSpec spec = specs.spec(dataset);
        if (!spec.partitioned()) {
            return Partitions.NONE;
        }
        JsonNode node = tower.partitions(List.of(dataset, "history"));
        List<String> keys = new ArrayList<>();
        node.path("partitionKeys").forEach(key -> keys.add(key.asString()));
        Map<String, String> status = new LinkedHashMap<>();
        keys.forEach(key -> status.put(key, "MISSING"));
        JsonNode statuses = node.path("assetPartitionStatuses");
        for (JsonNode range : statuses.path("ranges")) {
            int from = keys.indexOf(range.path("startKey").asString());
            int to = keys.indexOf(range.path("endKey").asString());
            for (int i = Math.max(from, 0); i <= to && i >= 0; i++) {
                status.put(keys.get(i), range.path("status").asString());
            }
        }
        statuses.path("materializedPartitions").forEach(key -> status.put(key.asString(), "MATERIALIZED"));
        statuses.path("failedPartitions").forEach(key -> status.put(key.asString(), "FAILED"));
        statuses.path("materializingPartitions").forEach(key -> status.put(key.asString(), "MATERIALIZING"));
        return new Partitions(
                true,
                spec.source().partitions().kind().name().toLowerCase(),
                status.entrySet().stream()
                        .map(entry -> new Partitions.Partition(entry.getKey(), entry.getValue()))
                        .toList());
    }

    private FlowRun flow(FlowEvent event) {
        return new FlowRun(
                event.flow(),
                event.status(),
                event.startedAt(),
                event.endedAt(),
                event.durationMs(),
                event.rowsIn(),
                event.rowsOut(),
                event.rowsDropped(),
                details(event.details()));
    }

    private Map<String, Object> details(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            return json.readValue(raw, MAP);
        } catch (JacksonException e) {
            return Map.of("text", raw);
        }
    }
}
