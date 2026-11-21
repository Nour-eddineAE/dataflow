package com.dataflow.workspace.tasks;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.serving.LakeFunctions;
import com.dataflow.workspace.serving.RecordingClickHouse;
import com.dataflow.workspace.specs.Parsing;
import com.dataflow.workspace.specs.SpecStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStatesTest {

    private static final String TASK = "0f0e0d0c-0b0a-4908-8706-050403020100";

    private final ObjectMapper json = new ObjectMapper();
    private final Parsing parsing = new Parsing();
    private final SpecStore specs = new SpecStore(Workspace.deployed(), new PipelineSpecLoader(), parsing);
    private final RecordingTower tower = new RecordingTower(json);
    private final RecordingClickHouse serving = new RecordingClickHouse();
    private final TaskStates states = new TaskStates(
            tower, new RunService(tower, null, specs, json), serving, new LakeFunctions(Workspace.properties()), json);

    @AfterEach
    void stop() {
        parsing.stop();
    }

    @Test
    void state_readsTheResultOnceTheRunFinished() {
        serving.answer(sql -> sql.contains("format = 'RawBLOB'"), List.of("raw_blob"),
                List.of(List.of("{\"status\":\"done\",\"result\":{\"rows\":42}}")));

        tower.tags = Map.of("workspace/task-id", TASK, "workspace/person", "bob");

        TaskState state = states.state(TASK, "run-1", "bob");

        assertThat(state.status()).isEqualTo("SUCCESS");
        assertThat(state.result().path("result").path("rows").asInt()).isEqualTo(42);
        assertThat(serving.statements()).singleElement().asString().contains("filename = 'system/tasks/" + TASK + ".json'");
    }

    @Test
    void state_rejectsSomethingThatIsNotATaskId() {
        assertThatThrownBy(() -> states.state("../x", "run-1", null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void state_refusesAnotherPersonsTask() {
        tower.tags = Map.of("workspace/task-id", TASK, "workspace/person", "ada");

        assertThatThrownBy(() -> states.state(TASK, "run-1", "bob")).isInstanceOf(AccessDeniedException.class);
        assertThat(serving.statements()).isEmpty();
    }

    @Test
    void state_refusesATaskIdFromAnotherRun() {
        tower.tags = Map.of("workspace/task-id", "another", "workspace/person", "bob");

        assertThatThrownBy(() -> states.state(TASK, "run-1", "bob")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void state_letsAnAdministratorReadAnyTask() {
        tower.tags = Map.of("workspace/task-id", TASK, "workspace/person", "ada");

        assertThat(states.state(TASK, "run-1", null).status()).isEqualTo("SUCCESS");
    }
}
