package com.dataflow.workspace.web;

import com.dataflow.workspace.access.Access;
import com.dataflow.workspace.access.AccessDeniedException;
import com.dataflow.workspace.access.Caller;
import com.dataflow.workspace.access.Privilege;
import com.dataflow.workspace.access.Securable;
import com.dataflow.workspace.runs.Automation;
import com.dataflow.workspace.runs.Trigger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AutomationControllerTest {

    private static final String GRANTED = "earthquakes";
    private static final String HIDDEN = "taxi_trips";
    private static final Caller READER = new Caller("bob", List.of("analysts"), false);
    private static final Caller ADMIN = new Caller("local", List.of("admins"), true);
    private static final Trigger GRANTED_SCHEDULE = trigger("earthquakes_schedule", Trigger.Kind.SCHEDULE, GRANTED);
    private static final Trigger HIDDEN_INPUTS = trigger("taxi_trips_on_inputs", Trigger.Kind.INPUTS, HIDDEN);
    private static final Trigger PLATFORM = trigger("orphaned_runs", Trigger.Kind.PLATFORM, null);

    private final Access access = mock(Access.class);
    private final Automation automation = mock(Automation.class);
    private final AutomationController controller = new AutomationController(automation, access);

    private static Trigger trigger(String name, Trigger.Kind kind, String dataset) {
        return new Trigger(name, kind, dataset, null, null, true, null, null, null, null);
    }

    private void signIn(Caller caller) {
        when(access.caller()).thenReturn(caller);
        when(access.sees(eq(caller), anyString())).thenAnswer(call -> caller.admin() || GRANTED.equals(call.getArgument(1)));
        if (!caller.admin()) {
            doThrow(new AccessDeniedException("not an administrator")).when(access).requireAdmin();
            doThrow(new AccessDeniedException("no RUN")).when(access).require(Privilege.RUN, Securable.dataset(HIDDEN));
        }
    }

    @BeforeEach
    void setUp() {
        when(automation.triggers()).thenReturn(List.of(GRANTED_SCHEDULE, HIDDEN_INPUTS, PLATFORM));
        when(automation.find(GRANTED_SCHEDULE.name())).thenReturn(GRANTED_SCHEDULE);
        when(automation.find(HIDDEN_INPUTS.name())).thenReturn(HIDDEN_INPUTS);
        when(automation.find(PLATFORM.name())).thenReturn(PLATFORM);
    }

    @Test
    void aReaderSeesTriggersOfGrantedDatasetsOnly() {
        signIn(READER);

        assertThat(controller.triggers()).containsExactly(GRANTED_SCHEDULE);
    }

    @Test
    void anAdministratorSeesPlatformSensorsToo() {
        signIn(ADMIN);

        assertThat(controller.triggers()).containsExactly(GRANTED_SCHEDULE, HIDDEN_INPUTS, PLATFORM);
    }

    @Test
    void pausingNeedsRunOnTheDatasetOrAdministrationForPlatformSensors() {
        signIn(READER);

        controller.pause(GRANTED_SCHEDULE.name());
        assertThatThrownBy(() -> controller.pause(HIDDEN_INPUTS.name())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.resume(PLATFORM.name())).isInstanceOf(AccessDeniedException.class);
        verify(automation).pause(GRANTED_SCHEDULE.name());
        verify(automation, never()).pause(HIDDEN_INPUTS.name());
        verify(automation, never()).resume(PLATFORM.name());
    }
}
