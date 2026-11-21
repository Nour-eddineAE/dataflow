package com.dataflow.workspace.runs;

import com.dataflow.spec.settings.RecordedSetting;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.templates.Templates;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class Tower {

    private static final String SELECTOR = "selector";
    private static final String TAGS = "tags";
    private static final String KEY = "key";
    private static final String VALUE = "value";
    private static final String RUN_ID = "runId";
    private static final String MESSAGE = "message";
    private static final String COMPUTE_LOGS = "compute_logs";
    private static final String SCHEDULE_CHANGED = "ScheduleStateResult";
    private static final String RUNS = "Runs";
    private static final String RUN = "Run";
    private static final String ASSET_NODE = "AssetNode";
    private static final String SCHEDULES = "Schedules";
    private static final String SCHEDULE = "Schedule";
    private static final String SENSOR = "Sensor";
    private static final String SENSOR_STOPPED = "StopSensorMutationResult";
    private static final String LAUNCHED = "LaunchRunSuccess";
    private static final String BACKFILL_LAUNCHED = "LaunchBackfillSuccess";
    private static final String TERMINATED = "TerminateRunSuccess";
    private static final String LOADED_LOCATION = "RepositoryLocation";
    private static final String LOCATION_ENTRY = "WorkspaceLocationEntry";
    private static final String UNREACHABLE = "DagsterUserCodeUnreachableError";
    private static final String RESOURCE_DETAILS = "ResourceDetails";
    static final String SETTINGS_RESOURCE = "tower_settings";

    private final WorkspaceProperties.Tower settings;
    private final HttpClient http;
    private final ObjectMapper json;
    private final Templates documents;

    public Tower(WorkspaceProperties properties, HttpClient http, ObjectMapper json) {
        this.settings = properties.tower();
        this.http = http;
        this.json = json;
        this.documents = Templates.load("graphql", "graphql");
    }

    private static JsonNode success(JsonNode node, String expected) {
        String type = node.path("__typename").asString();
        if (!expected.equals(type)) {
            throw new TowerException(node.path(MESSAGE).asString(type.isEmpty() ? "No answer from the tower" : type));
        }
        return node;
    }

    public List<JsonNode> runs(Map<String, Object> filter, int limit) {
        JsonNode data = query(withRunFields("runs"), Map.of("filter", filter, "limit", limit));
        JsonNode runs = success(data.path("runsOrError"), RUNS);
        List<JsonNode> results = new ArrayList<>();
        runs.path("results").forEach(results::add);
        return results;
    }

    public JsonNode run(String runId) {
        JsonNode data = query(withRunFields("run"), Map.of(RUN_ID, runId));
        return success(data.path("runOrError"), RUN);
    }

    public Map<String, String> tags(String runId) {
        return tagsOf(run(runId));
    }

    static Map<String, String> tagsOf(JsonNode run) {
        Map<String, String> tags = new HashMap<>();
        run.path(TAGS).forEach(tag -> tags.put(tag.path(KEY).asString(), tag.path(VALUE).asString()));
        return tags;
    }

    public String capturedOutput(String runId, String fileKey, int maxBytes) {
        JsonNode data = query(
                documents.render("captured-logs"),
                Map.of("logKey", List.of(runId, COMPUTE_LOGS, fileKey), "limit", maxBytes));
        return data.path("capturedLogs").path("stdout").asString("");
    }

    public String launch(String job, Map<String, String> tags, Map<String, Object> runConfig) {
        List<Map<String, String>> tagList = tags.entrySet().stream()
                .map(tag -> Map.of(KEY, tag.getKey(), VALUE, tag.getValue()))
                .toList();
        Map<String, Object> params = Map.of(
                SELECTOR, repositorySelector("jobName", job),
                "executionMetadata", Map.of(TAGS, tagList),
                "runConfigData", runConfig);
        JsonNode data = query(documents.render("launch"), Map.of("params", params));
        JsonNode result = data.path("launchRun");
        firstError(result).ifPresent(error -> {
            throw new TowerException("Run config rejected: " + error);
        });
        return success(result, LAUNCHED).path("run").path(RUN_ID).asString();
    }

    public String backfill(String job, List<String> partitions, Map<String, String> tags) {
        List<Map<String, String>> tagList = tags.entrySet().stream()
                .map(tag -> Map.of(KEY, tag.getKey(), VALUE, tag.getValue()))
                .toList();
        Map<String, Object> params = Map.of(
                SELECTOR,
                Map.of(
                        "partitionSetName",
                        job + "_partition_set",
                        "repositorySelector",
                        repositorySelector()),
                "partitionNames",
                partitions,
                "fromFailure",
                false,
                TAGS,
                tagList);
        JsonNode data = query(documents.render("backfill"), Map.of("params", params));
        return success(data.path("launchPartitionBackfill"), BACKFILL_LAUNCHED)
                .path("backfillId")
                .asString();
    }

    public void terminate(String runId) {
        JsonNode data = query(documents.render("terminate"), Map.of(RUN_ID, runId));
        success(data.path("terminateRun"), TERMINATED);
    }

    public boolean reload() {
        JsonNode data = query(documents.render("reload"), Map.of("location", settings.location()));
        return LOADED_LOCATION.equals(success(data.path("reloadRepositoryLocation"), LOCATION_ENTRY)
                .path("locationOrLoadError").path("__typename").asString());
    }

    public boolean reachesCode(String job, Map<String, Object> runConfig) {
        JsonNode data = query(
                documents.render("code-server"),
                Map.of(SELECTOR, repositorySelector("pipelineName", job), "runConfig", runConfig));
        if (data.path("executionPlanOrError").path(MESSAGE).asString().contains(UNREACHABLE)) {
            return false;
        }
        for (JsonNode entry : data.path("workspaceOrError").path("locationEntries")) {
            if (settings.location().equals(entry.path("name").asString())
                    && entry.path("locationOrLoadError").path(MESSAGE).asString().contains(UNREACHABLE)) {
                return false;
            }
        }
        return true;
    }

    public JsonNode partitions(List<String> assetPath) {
        JsonNode data = query(documents.render("partitions"), Map.of("path", assetPath));
        return success(data.path("assetNodeOrError"), ASSET_NODE);
    }

    public List<JsonNode> schedules() {
        JsonNode data = query(documents.render("schedules"), Map.of(SELECTOR, repositorySelector()));
        List<JsonNode> results = new ArrayList<>();
        success(data.path("schedulesOrError"), SCHEDULES).path("results").forEach(results::add);
        return results;
    }

    public JsonNode automation() {
        return query(documents.render("automation"), Map.of(SELECTOR, repositorySelector()));
    }

    public void startSensor(String name) {
        JsonNode data = query(documents.render("start-sensor"), Map.of(SELECTOR, repositorySelector("sensorName", name)));
        success(data.path("startSensor"), SENSOR);
    }

    public void stopSensor(String stateId) {
        JsonNode data = query(documents.render("stop-sensor"), Map.of("id", stateId));
        success(data.path("stopSensor"), SENSOR_STOPPED);
    }

    public JsonNode schedule(String name) {
        JsonNode data = query(documents.render("schedule"), Map.of(SELECTOR, repositorySelector("scheduleName", name)));
        return success(data.path("scheduleOrError"), SCHEDULE);
    }

    public void startSchedule(String name) {
        JsonNode data = query(documents.render("start-schedule"), Map.of(SELECTOR, repositorySelector("scheduleName", name)));
        success(data.path("startSchedule"), SCHEDULE_CHANGED);
    }

    public void stopSchedule(String stateId) {
        JsonNode data = query(documents.render("stop-schedule"), Map.of("id", stateId));
        success(data.path("stopRunningSchedule"), SCHEDULE_CHANGED);
    }

    public List<RecordedSetting> settings() {
        JsonNode data = query(documents.render("resource"),
                Map.of(SELECTOR, repositorySelector("resourceName", SETTINGS_RESOURCE)));
        List<RecordedSetting> settings = new ArrayList<>();
        for (JsonNode node : success(data.path("topLevelResourceDetailsOrError"), RESOURCE_DETAILS).path("configFields")) {
            ConfigField field = json.treeToValue(node, ConfigField.class);
            settings.add(new RecordedSetting(field.name(), field.configTypeKey(),
                    field.description() == null ? "" : field.description(), valueOf(field)));
        }
        return settings;
    }

    private String valueOf(ConfigField field) {
        String value = field.defaultValueAsJson();
        if (value == null || value.isEmpty()) {
            return "";
        }
        try {
            return json.readTree(value).asString();
        } catch (JacksonException e) {
            throw new TowerException("The tower sent setting " + field.name() + " with a value that is not JSON: " + value, e);
        }
    }

    record ConfigField(String name, String description, String configTypeKey, String defaultValueAsJson) {}

    public boolean reachable() {
        return version().isPresent();
    }

    public Optional<String> version() {
        try {
            return Optional.of(query(documents.render("version"), Map.of()).path("version").asString());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private Map<String, Object> repositorySelector() {
        return Map.of("repositoryLocationName", settings.location(), "repositoryName", settings.repository());
    }

    private Map<String, Object> repositorySelector(String key, String value) {
        Map<String, Object> selector = new HashMap<>(repositorySelector());
        selector.put(key, value);
        return selector;
    }

    private static Optional<String> firstError(JsonNode answer) {
        JsonNode errors = answer.path("errors");
        return errors.isArray() && !errors.isEmpty()
                ? Optional.of(errors.get(0).path(MESSAGE).asString())
                : Optional.empty();
    }

    private String withRunFields(String name) {
        return documents.render(name) + "\n" + documents.render("run-fields");
    }

    private JsonNode query(String query, Map<String, Object> variables) {
        ObjectNode body = json.createObjectNode();
        body.put("query", query);
        body.set("variables", json.valueToTree(variables));
        HttpRequest request = HttpRequest.newBuilder(URI.create(settings.url()))
                .timeout(settings.timeout())
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode answer = json.readTree(response.body());
            firstError(answer).ifPresent(error -> {
                throw new TowerException("Tower rejected the request: " + error);
            });
            if (response.statusCode() != HttpStatus.OK.value()) {
                throw new TowerException("Tower answered HTTP " + response.statusCode());
            }
            return answer.path("data");
        } catch (IOException | JacksonException e) {
            throw new TowerException("The tower (Dagster) is not reachable at " + settings.url(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TowerException("Interrupted while calling the tower", e);
        }
    }
}
