package com.dataflow.workspace.runs;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulesTest {

    @Test
    void state_readsTheCronTheStatusAndTheNextTick() throws IOException {
        try (InputStream schedule = Objects.requireNonNull(getClass().getResourceAsStream("/runs/schedule.json"))) {
            ScheduleState state = Schedules.state(new ObjectMapper().readTree(schedule));

            assertThat(state)
                    .isEqualTo(new ScheduleState("0 */6 * * *", "UTC", false, Instant.parse("2026-10-25T12:00:00Z")));
        }
    }
}
