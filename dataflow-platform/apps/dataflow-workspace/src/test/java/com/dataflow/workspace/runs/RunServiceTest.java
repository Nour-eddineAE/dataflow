package com.dataflow.workspace.runs;

import com.dataflow.workspace.Workspace;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RunServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final RecordingTower tower = new RecordingTower(json);
    private final RunService runs = new RunService(tower, null, null, json);

    @Test
    void runs_ofADataset_askTheTowerForItsJobOnly() {
        runs.runs("earthquakes", 1);

        assertThat(tower.filter).isEqualTo(Map.of("pipelineName", "earthquakes_job"));
        assertThat(tower.limit).isEqualTo(1);
    }

    @Test
    void runs_ofEveryDataset_leaveEngineTasksOut() {
        List<RunSummary> found = runs.runs(null, 1);

        assertThat(found).extracting(RunSummary::runId).containsExactly("pipeline-run");
        assertThat(tower.limit).isEqualTo(2);
    }

    @Test
    void previous_isTheLastCompletedRunBeforeThisOne() {
        RunService service = new RunService(tower, new FixedEventLog(), null, json);

        RunComparison comparison = service.previous("earthquakes", "run-3");

        assertThat(comparison.previousRunId()).isEqualTo("run-1");
        assertThat(comparison.previousFlows())
                .extracting(FlowRun::flow, FlowRun::rowsOut)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("silver", 9963L));
        assertThat(service.previous("earthquakes", "unknown")).isEqualTo(RunComparison.NONE);
    }

    @Test
    void eventRuns_takeTheTowersVerdictWhenARunFailedAfterTheEngineFinished() {
        Tower failing = new RecordingTower(json) {
            @Override
            public List<JsonNode> runs(Map<String, Object> filter, int limit) {
                return List.of(json.valueToTree(Map.of(
                        "runId", "run-3", "status", "FAILURE", "jobName", "earthquakes_job", "tags", List.of())));
            }
        };
        RunService service = new RunService(failing, new FixedEventLog(), null, json);

        assertThat(service.eventRuns("earthquakes", 3))
                .extracting(EventRun::runId, EventRun::status)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("run-3", "FAILED"),
                        org.assertj.core.groups.Tuple.tuple("run-2", "FAILED"),
                        org.assertj.core.groups.Tuple.tuple("run-1", "COMPLETED"));
    }

    @Test
    void eventRuns_keepTheEventLogsViewWhenTheTowerIsDown() {
        Tower down = new RecordingTower(json) {
            @Override
            public List<JsonNode> runs(Map<String, Object> filter, int limit) {
                throw new TowerException("The tower (Dagster) is not reachable");
            }
        };
        RunService service = new RunService(down, new FixedEventLog(), null, json);

        assertThat(service.eventRuns("earthquakes", 3)).extracting(EventRun::status)
                .containsExactly("COMPLETED", "FAILED", "COMPLETED");
    }

    private static final class FixedEventLog extends EventLog {

        FixedEventLog() {
            super(null, null, null);
        }

        private static EventRun run(String id, String status) {
            return new EventRun(id, status, "manual", null, null, null, null);
        }

        @Override
        public List<EventRun> runs(String dataset, int limit) {
            return List.of(run("run-3", "COMPLETED"), run("run-2", "FAILED"), run("run-1", "COMPLETED"));
        }

        @Override
        public List<FlowEvent> flows(String dataset, String runId) {
            return "run-1".equals(runId)
                    ? List.of(new FlowEvent("silver", "COMPLETED", null, null, 4058L, 9963L, 9963L, 0L, null))
                    : List.of();
        }
    }

    private static class RecordingTower extends Tower {

        private final ObjectMapper json;
        private Map<String, Object> filter;
        private int limit;

        RecordingTower(ObjectMapper json) {
            super(Workspace.properties(), HttpClient.newHttpClient(), json);
            this.json = json;
        }

        @Override
        public List<JsonNode> runs(Map<String, Object> filter, int limit) {
            this.filter = filter;
            this.limit = limit;
            return List.of(
                    run("task-run", "engine_task", List.of(Map.of("key", "workspace/task", "value", "preview"))),
                    run("pipeline-run", "earthquakes_job", List.of()));
        }

        private JsonNode run(String id, String job, List<Map<String, String>> tags) {
            return json.valueToTree(Map.of("runId", id, "status", "SUCCESS", "jobName", job, "tags", tags));
        }
    }

    @Test
    void aRunNamesThePersonWhoStartedIt() {
        JsonNode started = json.valueToTree(Map.of(
                "runId", "run", "status", "SUCCESS", "jobName", "earthquakes_job",
                "tags", List.of(Map.of("key", RunService.PERSON_TAG, "value", "ada"))));
        JsonNode scheduled = json.valueToTree(Map.of(
                "runId", "run", "status", "SUCCESS", "jobName", "earthquakes_job",
                "tags", List.of(Map.of("key", "dagster/schedule_name", "value", "earthquakes_schedule"))));

        assertThat(RunService.summary(started).startedBy()).isEqualTo("ada");
        assertThat(RunService.summary(scheduled).startedBy()).isNull();
    }

    @Test
    void aRunStartedBecauseItsInputsLandedSaysSo() {
        assertThat(RunService.trigger(Map.of("dagster/from_automation_condition", "true", "dagster/sensor_name", "x")))
                .isEqualTo("inputs");
        assertThat(RunService.trigger(Map.of("dagster/sensor_name", "x"))).isEqualTo("sensor");
        assertThat(RunService.trigger(Map.of())).isEqualTo("manual");
    }
}
