package com.dataflow.workspace.runs;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AutomationTest {

    private static List<Trigger> triggers() throws IOException {
        try (InputStream answer = Objects.requireNonNull(AutomationTest.class.getResourceAsStream("/runs/automation.json"))) {
            return Automation.triggers(new ObjectMapper().readTree(answer));
        }
    }

    @Test
    void schedulesKeepTheirDatasetCronLastAndNextTick() throws IOException {
        assertThat(triggers().getFirst()).isEqualTo(new Trigger(
                "earthquakes_schedule", Trigger.Kind.SCHEDULE, "earthquakes", "0 */6 * * *", "UTC", false,
                "SUCCESS", Instant.parse("2026-10-25T00:00:00Z"), null, Instant.parse("2026-10-31T00:00:00Z")));
    }

    @Test
    void sensorsAreInputTriggersOfADatasetOrPlatformSensors() throws IOException {
        assertThat(triggers()).extracting(Trigger::name, Trigger::kind, Trigger::dataset, Trigger::running).containsExactly(
                tuple("earthquakes_schedule", Trigger.Kind.SCHEDULE, "earthquakes", false),
                tuple("orphaned_runs", Trigger.Kind.PLATFORM, null, true),
                tuple("zone_hours_on_inputs", Trigger.Kind.INPUTS, "zone_hours", true),
                tuple("run_failure_email", Trigger.Kind.PLATFORM, null, false));
    }

    @Test
    void theLastTickSaysWhyItSkippedOrFailed() throws IOException {
        List<Trigger> triggers = triggers();

        assertThat(triggers.get(1).lastMessage()).isEqualTo("No orphaned runs");
        assertThat(triggers.get(2)).extracting(Trigger::lastStatus, Trigger::lastMessage)
                .containsExactly("FAILURE", "taxi_trips is unreachable");
        assertThat(triggers.get(3)).extracting(Trigger::lastStatus, Trigger::lastAt, Trigger::lastMessage)
                .containsExactly(null, null, null);
    }
}
