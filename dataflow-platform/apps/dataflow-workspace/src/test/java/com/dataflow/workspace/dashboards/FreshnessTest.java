package com.dataflow.workspace.dashboards;

import com.dataflow.workspace.runs.EventRun;
import com.dataflow.workspace.runs.ScheduleState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessTest {

    private static final ScheduleState EVERY_SIX_HOURS = new ScheduleState("0 */6 * * *", "UTC", true, null);
    private static final List<EventRun> RUNS = List.of(
            new EventRun(
                    "r2",
                    "FAILED",
                    "schedule",
                    null,
                    "2026-10-25 12:00:00.000000",
                    "2026-10-25 12:01:00.000000",
                    60_000L),
            new EventRun(
                    "r1",
                    "COMPLETED",
                    "schedule",
                    null,
                    "2026-10-25 06:00:00.000000",
                    "2026-10-25 06:00:50.000000",
                    50_000L));

    private static PipelineOverviews at(String now) {
        return new PipelineOverviews(
                null, null, null, null, null, null, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    @Test
    void aRunMissedPastItsNextSlotAndTheGrace_isLate() {
        Freshness freshness = at("2026-10-25T13:30:00Z").freshness(EVERY_SIX_HOURS, RUNS);

        assertThat(freshness.state()).isEqualTo("late");
        assertThat(freshness.lastSuccess()).isEqualTo("2026-10-25T06:00:50Z");
        assertThat(freshness.expectedBy()).isEqualTo("2026-10-25T13:00:00Z");
    }

    @Test
    void withinTheGrace_isFresh() {
        assertThat(at("2026-10-25T12:30:00Z").freshness(EVERY_SIX_HOURS, RUNS).state())
                .isEqualTo("fresh");
    }

    @Test
    void aPausedSchedule_isNeverLate() {
        assertThat(at("2026-10-27T00:00:00Z")
                        .freshness(new ScheduleState("0 */6 * * *", "UTC", false, null), RUNS)
                        .state())
                .isEqualTo("paused");
    }

    @Test
    void noScheduleOrNoSuccess() {
        assertThat(at("2026-10-25T12:30:00Z").freshness(null, RUNS).state()).isEqualTo("manual");
        assertThat(at("2026-10-25T12:30:00Z")
                        .freshness(EVERY_SIX_HOURS, RUNS.subList(0, 1))
                        .state())
                .isEqualTo("never");
    }
}
