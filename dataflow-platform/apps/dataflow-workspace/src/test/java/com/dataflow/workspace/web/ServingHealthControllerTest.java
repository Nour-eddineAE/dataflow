package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.serving.ServingHealth;
import com.dataflow.workspace.serving.SystemTables;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ServingHealthControllerTest {

    private final Access access = mock(Access.class);
    private final SystemTables system = mock(SystemTables.class);
    private final ServingHealthController controller = new ServingHealthController(access, system);

    @Test
    void administratorsSeeTheHealthOfServing() {
        ServingHealth health = new ServingHealth(new ServingHealth.Activity(1, 0, 2.0, 3), 4, List.of());
        when(system.health()).thenReturn(health);

        assertThat(controller.health()).isEqualTo(health);
    }

    @Test
    void othersAreRefusedBeforeAnyQuery() {
        doThrow(new AccessDeniedException("ada is not an administrator")).when(access).requireAdmin();

        assertThatThrownBy(controller::health).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(system);
    }
}
