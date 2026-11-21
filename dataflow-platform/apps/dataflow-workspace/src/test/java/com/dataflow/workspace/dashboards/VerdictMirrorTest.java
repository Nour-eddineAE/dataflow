package com.dataflow.workspace.dashboards;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.SpecStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VerdictMirrorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final Parsing parsing = new Parsing();
    private final SpecStore specs = new SpecStore(Workspace.deployed(), new PipelineSpecLoader(), parsing);
    private final RecordingClickHouse serving = new RecordingClickHouse();

    @AfterEach
    void stop() {
        parsing.stop();
    }

    private RunService towerWith(String status) {
        Tower tower = new Tower(Workspace.properties(), HttpClient.newHttpClient(), json) {
            @Override
            public List<JsonNode> runs(Map<String, Object> filter, int limit) {
                String job = (String) filter.get("pipelineName");
                return "earthquakes_job".equals(job)
                        ? List.of(json.valueToTree(
                                Map.of("runId", "run-3", "status", status, "jobName", job, "tags", List.of())))
                        : List.of();
            }
        };
        return new RunService(tower, null, specs, json);
    }

    @Test
    void mirror_writesTheTowersFailureSoBoardsDoNotCallTheRunCompleted() {
        new VerdictMirror(specs, towerWith("FAILURE"), serving).mirror();

        assertThat(serving.statements()).first().asString().contains("run_verdicts", "ReplacingMergeTree");
        assertThat(serving.statements())
                .filteredOn(sql -> sql.startsWith("insert"))
                .singleElement()
                .asString()
                .contains("('run-3', 'earthquakes', 'FAILED')");
    }

    @Test
    void mirror_writesNothingWhenEveryRunSucceeded() {
        new VerdictMirror(specs, towerWith("SUCCESS"), serving).mirror();

        assertThat(serving.statements()).noneMatch(sql -> sql.startsWith("insert"));
    }
}
