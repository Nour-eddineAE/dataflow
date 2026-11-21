package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.folders.GitFolders;
import com.dataflow.workspace.folders.People;
import com.dataflow.workspace.releases.ReleaseLog;
import com.dataflow.workspace.releases.ReleaseRecord;
import com.dataflow.workspace.releases.Releases;
import com.dataflow.workspace.releases.Releases.ReleaseView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReleaseControllerTest {

    private static final Caller READER = new Caller("bob", List.of("analysts"), false);
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final ReleaseView SHARED =
            new ReleaseView("shared", "prod", "abc", NOW, "ada", List.of("earthquakes", "taxi_trips"));
    private static final ReleaseView HIDDEN = new ReleaseView("hidden", "prod", "def", NOW, "ada", List.of("payroll"));
    private static final ReleaseRecord RECORD = new ReleaseRecord("hidden", "prod", "def", UUID.randomUUID(), "ada", NOW);

    private final Releases releases = mock(Releases.class);
    private final ReleaseLog releaseLog = mock(ReleaseLog.class);
    private final Access access = mock(Access.class);
    private final ReleaseController controller =
            new ReleaseController(releases, releaseLog, mock(People.class), access, mock(GitFolders.class));

    @BeforeEach
    void setUp() {
        when(access.caller()).thenReturn(READER);
        when(access.sees(READER, "earthquakes")).thenReturn(true);
        when(releases.current()).thenReturn(List.of(SHARED, HIDDEN));
        when(releaseLog.history(eq("hidden"), anyInt())).thenReturn(List.of(RECORD));
    }

    @Test
    void aReaderSeesOnlyReleasesAndDatasetsTheyAreGranted() {
        assertThat(controller.current()).singleElement().satisfies(view -> {
            assertThat(view.project()).isEqualTo("shared");
            assertThat(view.datasets()).containsExactly("earthquakes");
        });
    }

    @Test
    void aReaderGetsNoHistoryOfAProjectTheyCannotSee() {
        assertThat(controller.history("hidden")).isEmpty();
    }

    @Test
    void whoReleasedAProjectStillSeesIt() {
        when(access.caller()).thenReturn(new Caller("ada", List.of(), false));

        assertThat(controller.current()).hasSize(2);
        assertThat(controller.history("hidden")).containsExactly(RECORD);
    }
}
