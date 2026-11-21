package com.dataflow.workspace.runs;

import com.dataflow.workspace.specs.SpecStore;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.stream.Collectors;

@Service
public class Schedules {

    private static final String STATE = "scheduleState";

    private final Tower tower;
    private final SpecStore specs;

    public Schedules(Tower tower, SpecStore specs) {
        this.tower = tower;
        this.specs = specs;
    }

    static ScheduleState state(JsonNode schedule) {
        JsonNode next = schedule.path("futureTicks").path("results").path(0).path("timestamp");
        return new ScheduleState(
                schedule.path("cronSchedule").asString(),
                schedule.path("executionTimezone").asString(),
                Automation.RUNNING.equals(schedule.path(STATE).path("status").asString()),
                next.isNumber() ? EpochSeconds.instant(next.asDouble()) : null);
    }

    public Map<String, Boolean> running() {
        return tower.schedules().stream()
                .filter(schedule -> schedule.path("name").asString().endsWith(Automation.SCHEDULE_SUFFIX))
                .collect(Collectors.toMap(
                        schedule -> schedule.path("name").asString().replaceFirst(Automation.SCHEDULE_SUFFIX + "$", ""),
                        schedule -> Automation.RUNNING.equals(
                                schedule.path(STATE).path("status").asString())));
    }

    public ScheduleState state(String dataset) {
        return state(tower.schedule(name(dataset)));
    }

    public ScheduleState pause(String dataset) {
        JsonNode schedule = tower.schedule(name(dataset));
        if (state(schedule).running()) {
            tower.stopSchedule(schedule.path(STATE).path("id").asString());
        }
        return state(dataset);
    }

    public ScheduleState resume(String dataset) {
        String name = name(dataset);
        if (!state(tower.schedule(name)).running()) {
            tower.startSchedule(name);
        }
        return state(dataset);
    }

    private String name(String dataset) {
        if (specs.spec(dataset).schedule() == null) {
            throw new IllegalArgumentException(dataset + " has no schedule");
        }
        return dataset + Automation.SCHEDULE_SUFFIX;
    }
}
