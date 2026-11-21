package com.dataflow.engine.cli;

import com.dataflow.engine.core.run.FlowStatus;
import com.dataflow.engine.core.run.RunReport;
import com.dataflow.spec.PipelineSpecLoader;
import com.dataflow.spec.PipelineSpecs;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineCommandTest {

    private static final Path SHIPPED_PLANS = Path.of("../../libs/dataflow-spec/src/test/resources/plans/examples");

    private final PipelineSpecs plans = new PipelineSpecLoader().loadAll(SHIPPED_PLANS);
    private final RunLauncher launcher = mock(RunLauncher.class);
    private final TaskLauncher tasks = mock(TaskLauncher.class);
    private final EngineCommand command = new EngineCommand(plans, launcher, tasks);

    private static RunReport report(FlowStatus status) {
        return new RunReport("f", "d", status, List.of());
    }

    @Test
    void run_executesThePlanAndExitsZeroWhenItCompletes() {
        when(launcher.launch("taxi_trips", "2024-01", "schedule", false)).thenReturn(report(FlowStatus.COMPLETED));

        command.run(new DefaultApplicationArguments(
                "run", "--dataset=taxi_trips", "--partition=2024-01", "--trigger=schedule"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.SUCCESS);
    }

    @Test
    void run_passesAFullRefreshOn() {
        when(launcher.launch("earthquakes", null, "manual", true)).thenReturn(report(FlowStatus.COMPLETED));

        command.run(new DefaultApplicationArguments("run", "--dataset=earthquakes", "--full-refresh"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.SUCCESS);
    }

    @Test
    void run_exitsOneWhenTheRunFails() {
        when(launcher.launch("earthquakes", null, "manual", false)).thenReturn(report(FlowStatus.FAILED));

        command.run(new DefaultApplicationArguments("run", "--dataset=earthquakes"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.FAILURE);
    }

    @Test
    void run_withoutADataset_isAUsageError() {
        command.run(new DefaultApplicationArguments("run"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.USAGE);
        verify(launcher, never()).launch(any(), any(), any(), anyBoolean());
    }

    @Test
    void preview_handsTheDraftToTheTaskLauncher() {
        when(tasks.launch(TaskKind.PREVIEW, "earthquakes", "t1")).thenReturn(true);

        command.run(new DefaultApplicationArguments("preview", "--dataset=earthquakes", "--task=t1"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.SUCCESS);
    }

    @Test
    void impact_exitsOneWhenTheTaskFails() {
        when(tasks.launch(TaskKind.IMPACT, "earthquakes", "t2")).thenReturn(false);

        command.run(new DefaultApplicationArguments("impact", "--dataset=earthquakes", "--task=t2"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.FAILURE);
    }

    @Test
    void task_withoutAnId_isAUsageError() {
        command.run(new DefaultApplicationArguments("preview", "--dataset=earthquakes"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.USAGE);
        verify(tasks, never()).launch(any(), any(), any());
    }

    @Test
    void infer_handsTheSourceToTheTaskLauncher() {
        when(tasks.infer("quakes", "t3", "https://example.org/all_month.csv")).thenReturn(true);

        command.run(new DefaultApplicationArguments(
                "infer", "--dataset=quakes", "--task=t3", "--source=https://example.org/all_month.csv"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.SUCCESS);
    }

    @Test
    void infer_withoutASource_isAUsageError() {
        command.run(new DefaultApplicationArguments("infer", "--dataset=quakes", "--task=t3"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.USAGE);
        verify(tasks, never()).infer(any(), any(), any());
    }

    @Test
    void unknownCommand_isAUsageError() {
        command.run(new DefaultApplicationArguments("deploy"));

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.USAGE);
    }

    @Test
    void validate_isTheDefaultCommand() {
        command.run(new DefaultApplicationArguments());

        assertThat(command.getExitCode()).isEqualTo(EngineCommand.SUCCESS);
        verify(launcher, never()).launch(any(), any(), any(), anyBoolean());
    }
}
