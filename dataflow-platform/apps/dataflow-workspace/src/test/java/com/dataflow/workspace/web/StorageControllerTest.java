package com.dataflow.workspace.web;

import com.dataflow.workspace.Workspace;
import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.RustFsPolicies;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StorageControllerTest {

    private final Access access = mock(Access.class);
    private final RustFsPolicies policies = mock(RustFsPolicies.class);
    private final StorageController controller = new StorageController(access, policies, Workspace.properties());

    @Test
    void pathsAreResolvedForTheAuthenticatedCaller() {
        Caller caller = new Caller("bob", List.of("analysts"), false);
        when(access.caller()).thenReturn(caller);
        List<RustFsPolicies.DatasetPaths> paths = List.of(new RustFsPolicies.DatasetPaths("earthquakes", List.of()));
        when(policies.paths(caller)).thenReturn(paths);

        assertThat(controller.view().datasets()).isEqualTo(paths);
        verify(policies).paths(caller);
    }

    @Test
    void aNonAdministratorCannotPublishPolicies() {
        doThrow(new AccessDeniedException("Administrator required")).when(access).requireAdmin();

        assertThatThrownBy(controller::sync).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(policies);
    }
}
