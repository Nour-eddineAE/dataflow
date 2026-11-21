package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Visibility;
import com.dataflow.workspace.datasets.DatasetService;
import com.dataflow.workspace.runs.RunRuntime;
import com.dataflow.workspace.runs.RunService;
import com.dataflow.workspace.runs.Schedules;
import com.dataflow.workspace.specs.SpecStore;
import com.dataflow.workspace.why.WhyService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatasetControllerTest {

    private static final String DATASET = "earthquakes";

    private final RunService runs = mock(RunService.class);
    private final DatasetController controller = new DatasetController(mock(DatasetService.class), runs,
            mock(SpecStore.class), mock(WhyService.class), mock(Schedules.class), mock(Access.class), mock(Visibility.class));

    @Test
    void aDatasetWithNoRecordedRuntimeAnswersNoContent() {
        when(runs.latestRuntime(DATASET)).thenReturn(Optional.empty());

        assertThat(controller.runtime(DATASET).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void theLatestRecordedRuntimeIsTheBody() {
        RunRuntime runtime = new RunRuntime("3.0", "4.2.0", "4.4.1", "2.13", "21");
        when(runs.latestRuntime(DATASET)).thenReturn(Optional.of(runtime));

        assertThat(controller.runtime(DATASET).getBody()).isEqualTo(runtime);
    }
}
