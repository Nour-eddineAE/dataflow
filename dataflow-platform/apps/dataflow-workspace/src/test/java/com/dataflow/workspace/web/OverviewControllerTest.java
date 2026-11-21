package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.config.WorkspaceProperties;
import com.dataflow.workspace.config.StorageAccessProperties;
import com.dataflow.workspace.folders.FolderStore;
import com.dataflow.workspace.runs.Tower;
import com.dataflow.workspace.serving.ClickHouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OverviewControllerTest {

    private static final WorkspaceProperties.Links LINKS = new WorkspaceProperties.Links(
            "http://tower", "http://store", "http://serving", "http://boards", "http://mail", "http://spark");
    private static final WorkspaceProperties.Dashboard BOARD =
            new WorkspaceProperties.Dashboard("runs", "Runs", "Pipeline", null, "dataset");

    private final Access access = mock(Access.class);
    private final StorageAccessProperties storage = mock(StorageAccessProperties.class);
    private final OverviewController controller = controller();

    @Test
    void adminsSeeEveryAppAndGrafanaBoard() {
        when(access.caller()).thenReturn(new Caller("nour", List.of("admins"), true));

        assertThat(controller.status().links()).isEqualTo(LINKS);
        assertThat(controller.dashboards()).containsExactly(BOARD);
    }

    @Test
    void othersSeeOnlyTheObjectStoreAndNoGrafanaBoard() {
        when(access.caller()).thenReturn(new Caller("ada", List.of("analysts"), false));

        assertThat(controller.status().links())
                .isEqualTo(new WorkspaceProperties.Links(null, "http://store", null, null, null, null));
        assertThat(controller.dashboards()).isEmpty();
    }

    @Test
    void scopedStorageOpensTheCallersDatasetPaths() {
        when(access.caller()).thenReturn(new Caller("ada", List.of("analysts"), false));
        when(storage.enabled()).thenReturn(true);

        assertThat(controller.status().links().objectStore()).isEqualTo("/storage");
    }

    @SuppressWarnings("unchecked")
    private OverviewController controller() {
        WorkspaceProperties properties = mock(WorkspaceProperties.class);
        when(properties.links()).thenReturn(LINKS);
        when(properties.dashboards()).thenReturn(List.of(BOARD));
        when(properties.auth()).thenReturn(new WorkspaceProperties.Auth(
                WorkspaceProperties.AuthMode.OIDC, null, null, null, null, "groups", "preferred_username", List.of("admins")));
        return new OverviewController(mock(Tower.class), mock(ClickHouse.class), mock(FolderStore.class), properties,
                mock(ObjectProvider.class), access, storage);
    }
}
