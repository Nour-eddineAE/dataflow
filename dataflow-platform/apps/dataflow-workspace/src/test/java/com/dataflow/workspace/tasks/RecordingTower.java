package com.dataflow.workspace.tasks;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.runs.Tower;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

final class RecordingTower extends Tower {

    private final ObjectMapper json;
    String job;
    Map<String, String> tags;
    Map<String, Object> runConfig;

    RecordingTower(ObjectMapper json) {
        super(Workspace.properties(), HttpClient.newHttpClient(), json);
        this.json = json;
    }

    @Override
    public String launch(String job, Map<String, String> tags, Map<String, Object> runConfig) {
        this.job = job;
        this.tags = tags;
        this.runConfig = runConfig;
        return "run-1";
    }

    @Override
    public Map<String, String> tags(String runId) {
        return tags;
    }

    @Override
    public JsonNode run(String runId) {
        return json.valueToTree(Map.of("runId", runId, "status", "SUCCESS", "jobName", "engine_task", "tags", List.of()));
    }
}
