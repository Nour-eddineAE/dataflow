package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.AuditAction;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.runs.RunDetail;
import com.dataflow.workspace.runs.RunOutput;
import com.dataflow.workspace.runs.RunOutputs;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.RunSummary;
import com.dataflow.workspace.runs.Schedules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RunControllerTest {

    private static final String GRANTED = "earthquakes";
    private static final String HIDDEN = "taxi_trips";
    private static final Caller READER = new Caller("bob", List.of("analysts"), false);
    private static final Caller ADMIN = new Caller("local", List.of("admins"), true);
    private static final RunSummary GRANTED_RUN = run("run-granted", GRANTED);
    private static final RunSummary HIDDEN_RUN = run("run-hidden", HIDDEN);
    private static final RunSummary UNTAGGED_RUN = run("run-untagged", null);

    private final Access access = mock(Access.class);
    private final RunService runs = mock(RunService.class);
    private final RunOutputs outputs = mock(RunOutputs.class);
    private final Schedules schedules = mock(Schedules.class);
    private final RunController controller =
            new RunController(runs, outputs, schedules, access, new Visibility(access));

    private static RunSummary run(String runId, String dataset) {
        return new RunSummary(runId, dataset, "job", "SUCCESS", "manual", null, null, null, null, null, null, null);
    }

    private static RunDetail detail(RunSummary run) {
        return new RunDetail(run, List.of(), List.of(), List.of(), null, null, null);
    }

    private void signIn(Caller caller) {
        when(access.caller()).thenReturn(caller);
        if (caller.admin()) {
            when(access.sees(eq(caller), anyString())).thenReturn(true);
            when(access.sees(anyString())).thenReturn(true);
        } else {
            when(access.sees(caller, GRANTED)).thenReturn(true);
            when(access.sees(GRANTED)).thenReturn(true);
            doThrow(new AccessDeniedException(caller.id() + " is not an administrator")).when(access).requireAdmin();
        }
    }

    @BeforeEach
    void setUp() {
        when(runs.runs(null, 50)).thenReturn(List.of(GRANTED_RUN, HIDDEN_RUN, UNTAGGED_RUN));
        when(schedules.running()).thenReturn(Map.of(GRANTED, true, HIDDEN, false));
    }

    @Test
    void aRunIsRecordedOnlyOnceItStarted() {
        signIn(READER);
        when(runs.launch(GRANTED, null, READER.id(), false)).thenReturn("run-1");

        controller.launch(new RunController.Launch(GRANTED, null, false));

        verify(access).record(AuditAction.RUN, Securable.dataset(GRANTED));
    }

    @Test
    void aFullRefreshNeedsEditAndIsRecordedAsSuch() {
        signIn(READER);
        when(runs.launch(GRANTED, null, READER.id(), true)).thenReturn("run-1");

        controller.launch(new RunController.Launch(GRANTED, null, true));

        verify(access).require(Privilege.EDIT, Securable.dataset(GRANTED));
        verify(access).record(AuditAction.FULL_REFRESH, Securable.dataset(GRANTED));
    }

    @Test
    void aPersonWithoutEditCannotFullRefresh() {
        signIn(READER);
        doThrow(new AccessDeniedException("not yours"))
                .when(access)
                .require(Privilege.EDIT, Securable.dataset(GRANTED));

        assertThatThrownBy(() -> controller.launch(new RunController.Launch(GRANTED, null, true)))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(runs);
    }

    @Test
    void aRunAndABackfillNameThePersonWhoStartedThem() {
        signIn(READER);

        controller.launch(new RunController.Launch(GRANTED, "2024-01", false));
        controller.backfill(new RunController.Backfill(GRANTED, List.of("2024-01")));

        verify(runs).launch(GRANTED, "2024-01", READER.id(), false);
        verify(runs).backfill(GRANTED, List.of("2024-01"), READER.id());
    }

    @Test
    void aReaderListsOnlyRunsOfGrantedDatasets() {
        signIn(READER);

        assertThat(controller.runs(null, 50)).containsExactly(GRANTED_RUN);
    }

    @Test
    void anAdministratorListsUntaggedRuns() {
        signIn(ADMIN);

        assertThat(controller.runs(null, 50)).containsExactly(GRANTED_RUN, HIDDEN_RUN, UNTAGGED_RUN);
    }

    @Test
    void aReaderOpensARunOfAGrantedDataset() {
        signIn(READER);
        when(runs.detail(GRANTED_RUN.runId())).thenReturn(detail(GRANTED_RUN));

        assertThat(controller.run(GRANTED_RUN.runId()).run()).isEqualTo(GRANTED_RUN);
    }

    @Test
    void aReaderCannotOpenAnUntaggedRun() {
        signIn(READER);
        when(runs.detail(UNTAGGED_RUN.runId())).thenReturn(detail(UNTAGGED_RUN));

        assertThatThrownBy(() -> controller.run(UNTAGGED_RUN.runId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aReaderCannotOpenARunOfAHiddenDataset() {
        signIn(READER);
        when(runs.detail(HIDDEN_RUN.runId())).thenReturn(detail(HIDDEN_RUN));

        assertThatThrownBy(() -> controller.run(HIDDEN_RUN.runId())).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aReaderReadsTheOutputOfARunOfAGrantedDataset() {
        signIn(READER);
        RunOutput output = new RunOutput(List.of(), false);
        when(runs.summary(GRANTED_RUN.runId())).thenReturn(GRANTED_RUN);
        when(outputs.output(GRANTED_RUN.runId())).thenReturn(output);

        assertThat(controller.output(GRANTED_RUN.runId())).isEqualTo(output);
    }

    @Test
    void aReaderCannotReadTheOutputOfAnUntaggedRun() {
        signIn(READER);
        when(runs.summary(UNTAGGED_RUN.runId())).thenReturn(UNTAGGED_RUN);

        assertThatThrownBy(() -> controller.output(UNTAGGED_RUN.runId())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(outputs);
    }

    @Test
    void anAdministratorReadsTheOutputOfAnUntaggedRun() {
        signIn(ADMIN);
        RunOutput output = new RunOutput(List.of(), false);
        when(runs.summary(UNTAGGED_RUN.runId())).thenReturn(UNTAGGED_RUN);
        when(outputs.output(UNTAGGED_RUN.runId())).thenReturn(output);

        assertThat(controller.output(UNTAGGED_RUN.runId())).isEqualTo(output);
    }

    @Test
    void aReaderSeesOnlySchedulesOfGrantedDatasets() {
        signIn(READER);

        assertThat(controller.schedules()).containsOnlyKeys(GRANTED);
    }

    @Test
    void anAdministratorSeesEverySchedule() {
        signIn(ADMIN);

        assertThat(controller.schedules()).containsOnlyKeys(GRANTED, HIDDEN);
    }
}
