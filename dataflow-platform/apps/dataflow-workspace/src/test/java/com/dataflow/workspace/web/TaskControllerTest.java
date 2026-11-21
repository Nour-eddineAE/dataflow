package com.dataflow.workspace.web;

import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.DraftAccess;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.config.SourcesProperties;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.specs.SpecCheck;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.tasks.EngineTasks;
import com.dataflow.workspace.tasks.TaskStates;
import com.dataflow.workspace.tasks.TaskKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskControllerTest {

    private static final Caller READER = new Caller("bob", List.of("analysts"), false);
    private static final String STEALS_A_TABLE = """
            dataset: draft
            version: 3
            inputs:
              rows: taxi_trips.current
            gold:
              - name: copy
                sql: SELECT * FROM rows
            """;
    private static final String OWN_RULES = """
            dataset: draft
            version: 3
            source: { url: "https://8.8.8.8/a.csv", format: csv }
            silver:
              columns:
                - { name: id, type: string, required: true, from: id }
            history: { keys: [id] }
            """;

    private final EngineTasks tasks = mock(EngineTasks.class);
    private final Access access = mock(Access.class);
    private final SpecStore specs = mock(SpecStore.class);
    private final TaskController controller = new TaskController(
            tasks,
            mock(TaskStates.class),
            access,
            mock(GitFolders.class),
            specs,
            new DraftAccess(access, new SourcesProperties(List.of(), List.of())));

    private void draft(String content) {
        when(specs.check(content, "draft"))
                .thenReturn(new SpecCheck(new PipelineSpecLoader().parse(content, "draft"), List.of()));
    }

    @BeforeEach
    void signIn() {
        when(access.caller()).thenReturn(READER);
        when(specs.find(anyString())).thenReturn(Optional.empty());
        doThrow(new AccessDeniedException("bob lacks SELECT"))
                .when(access)
                .require(Privilege.SELECT, new Securable(Securable.Kind.TABLE, "taxi_trips.current"));
        doThrow(new AccessDeniedException("bob lacks SELECT"))
                .when(access)
                .require(Privilege.SELECT, Securable.dataset("taxi_trips"));
    }

    @Test
    void aPreviewOfANewPipelineCannotReadATableThePersonMayNot() {
        draft(STEALS_A_TABLE);

        assertThatThrownBy(() -> controller.start(new TaskController.Request("preview", "draft", STEALS_A_TABLE, null, null)))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    void aPreviewThatReadsNothingElseIsLaunched() {
        draft(OWN_RULES);

        controller.start(new TaskController.Request("preview", "draft", OWN_RULES, null, null));

        verify(tasks).start(TaskKind.PREVIEW, "draft", OWN_RULES, null);
    }

    @Test
    void inferringCannotReadAnotherTeamsFiles() {
        assertThatThrownBy(() -> controller.start(
                        new TaskController.Request("infer", "draft", null, null, "silver/taxi_trips/part-0.parquet")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }

    @Test
    void inferringCannotReachAnInternalService() {
        assertThatThrownBy(() -> controller.start(
                        new TaskController.Request("infer", "draft", null, null, "http://clickhouse:8123/?query=select+1")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(tasks);
    }
}
