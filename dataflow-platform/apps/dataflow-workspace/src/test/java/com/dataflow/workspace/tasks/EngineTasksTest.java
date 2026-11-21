package com.dataflow.workspace.tasks;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Drafts;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.specs.InvalidDraftException;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.SpecStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EngineTasksTest {

    private static final String TASK = "0f0e0d0c-0b0a-4908-8706-050403020100";

    private final ObjectMapper json = new ObjectMapper();
    private final Parsing parsing = new Parsing();
    private final SpecStore specs = new SpecStore(Workspace.deployed(), new PipelineSpecLoader(), parsing);
    private final RecordingTower tower = new RecordingTower(json);
    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final Access access = mock(Access.class);
    private final EngineTasks tasks =
            new EngineTasks(tower, specs, json, Workspace.properties(), Workspace.deployed(), null, access);

    @BeforeEach
    void signIn() {
        when(access.caller()).thenReturn(new Caller("bob", List.of(), false));
    }

    @AfterEach
    void stop() {
        parsing.stop();
    }

    @Test
    void start_launchesTheTaskJobWithTheDraftAndTags() {
        String draft = Drafts.text("earthquakes");

        TaskStarted started = tasks.start(TaskKind.IMPACT, "earthquakes", draft, null);

        assertThat(started.runId()).isEqualTo("run-1");
        assertThat(tower.job).isEqualTo("engine_task");
        assertThat(tower.tags).containsEntry("workspace/task", "impact").containsEntry("dataset", "earthquakes");
        JsonNode config = json.valueToTree(tower.runConfig).path("ops").path("run_engine_task").path("config");
        assertThat(config.path("command").asString()).isEqualTo("impact");
        assertThat(config.path("task_id").asString()).isEqualTo(started.taskId());
        assertThat(new String(Base64.getDecoder().decode(config.path("draft").asString()), StandardCharsets.UTF_8)).isEqualTo(draft);
    }

    @Test
    void clean_launchesOneTaskWithTheLocationsForThePerson() {
        TaskStarted started = tasks.clean(List.of("system/tasks/a.json", "spark-events/app-1/"), "platform");

        assertThat(tower.tags)
                .containsEntry("workspace/task", "clean")
                .containsEntry("dataset", "platform")
                .containsEntry("workspace/person", "platform");
        JsonNode config = json.valueToTree(tower.runConfig).path("ops").path("run_engine_task").path("config");
        assertThat(config.path("command").asString()).isEqualTo("clean");
        assertThat(config.path("task_id").asString()).isEqualTo(started.taskId());
        assertThat(new String(Base64.getDecoder().decode(config.path("clean").asString()), StandardCharsets.UTF_8))
                .isEqualTo("system/tasks/a.json\nspark-events/app-1/");
    }

    @Test
    void start_rejectsAnInvalidDraftBeforeLaunching() {
        assertThatThrownBy(() -> tasks.start(TaskKind.PREVIEW, "earthquakes", "dataset: earthquakes\nversion: 1\n", null))
                .isInstanceOf(InvalidDraftException.class);
        assertThat(tower.job).isNull();
    }

    @Test
    void start_ofAnImpactForANewDataset_asksForAPreview() {
        String draft = Drafts.text("earthquakes").replace("dataset: earthquakes", "dataset: rainfall")
                .replace("table: earthquakes", "table: rainfall_now").replace("table: earthquakes_", "table: rainfall_");

        assertThatThrownBy(() -> tasks.start(TaskKind.IMPACT, "rainfall", draft, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("preview it instead");
    }

    @Test
    void infer_launchesTheTaskJobWithTheSource() {
        TaskStarted started = tasks.infer("quakes", "https://earthquake.usgs.gov/feed/all_month.csv");

        assertThat(tower.tags).containsEntry("workspace/task", "infer").containsEntry("dataset", "quakes");
        JsonNode config = json.valueToTree(tower.runConfig).path("ops").path("run_engine_task").path("config");
        assertThat(config.path("command").asString()).isEqualTo("infer");
        assertThat(config.path("task_id").asString()).isEqualTo(started.taskId());
        assertThat(config.path("source").asString()).isEqualTo("https://earthquake.usgs.gov/feed/all_month.csv");
        assertThat(config.has("draft")).isFalse();
    }

    @Test
    void infer_acceptsAPathInTheLake() {
        tasks.infer("sensors", "inbox/sensors/readings.csv");

        assertThat(json.valueToTree(tower.runConfig).path("ops").path("run_engine_task").path("config").path("source").asString())
                .isEqualTo("inbox/sensors/readings.csv");
    }

    @Test
    void infer_refusesAnythingButAWebURLOrALakePath() {
        assertThatThrownBy(() -> tasks.infer("quakes", "file:///etc/passwd")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tasks.infer("quakes", "inbox/../system/x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tasks.infer("quakes", "system/tasks/x.json")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tasks.infer("Quakes", "https://example.org/a.csv")).isInstanceOf(IllegalArgumentException.class);
        assertThat(tower.job).isNull();
    }

    @Test
    void start_recordsWhoStartedIt() {
        TaskStarted started = tasks.start(TaskKind.PREVIEW, "earthquakes", Drafts.text("earthquakes"), null);

        assertThat(tower.tags).containsEntry("workspace/person", "bob").containsEntry("workspace/task-id", started.taskId());
    }
}
