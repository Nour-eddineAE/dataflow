package com.dataflow.workspace.runs;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class Automation {

    private static final String RESULTS = "results";

    static final String SCHEDULE_SUFFIX = "_schedule";
    static final String RUNNING = "RUNNING";
    private static final String INPUTS_SUFFIX = "_on_inputs";

    private final Tower tower;

    public Automation(Tower tower) {
        this.tower = tower;
    }

    static List<Trigger> triggers(JsonNode data) {
        return entries(data).stream().map(Entry::trigger).toList();
    }

    private static List<Entry> entries(JsonNode data) {
        List<Entry> triggers = new ArrayList<>();
        for (JsonNode schedule : data.path("schedulesOrError").path(RESULTS)) {
            String name = schedule.path("name").asString();
            JsonNode next = schedule.path("futureTicks").path(RESULTS).path(0).path("timestamp");
            JsonNode state = schedule.path("scheduleState");
            triggers.add(new Entry(trigger(name, Trigger.Kind.SCHEDULE, datasetOf(name, SCHEDULE_SUFFIX),
                    schedule.path("cronSchedule").asString(), schedule.path("executionTimezone").asString(),
                    state, next.isNumber() ? EpochSeconds.instant(next.asDouble()) : null), state.path("id").asString()));
        }
        for (JsonNode sensor : data.path("sensorsOrError").path(RESULTS)) {
            String name = sensor.path("name").asString();
            String dataset = datasetOf(name, INPUTS_SUFFIX);
            JsonNode state = sensor.path("sensorState");
            triggers.add(new Entry(trigger(name, dataset == null ? Trigger.Kind.PLATFORM : Trigger.Kind.INPUTS, dataset,
                    null, null, state, null), state.path("id").asString()));
        }
        return List.copyOf(triggers);
    }

    private static Trigger trigger(
            String name, Trigger.Kind kind, String dataset, String cron, String timezone, JsonNode state, Instant next) {
        JsonNode tick = state.path("ticks").path(0);
        JsonNode at = tick.path("timestamp");
        String message = tick.path("error").path("message").asString(tick.path("skipReason").asString(""));
        return new Trigger(
                name,
                kind,
                dataset,
                cron,
                timezone,
                RUNNING.equals(state.path("status").asString()),
                tick.path("status").asString(null),
                at.isNumber() ? EpochSeconds.instant(at.asDouble()) : null,
                message.isBlank() ? null : message,
                next);
    }

    private static String datasetOf(String name, String suffix) {
        return name.endsWith(suffix) ? name.substring(0, name.length() - suffix.length()) : null;
    }

    public List<Trigger> triggers() {
        return triggers(tower.automation());
    }

    public Trigger find(String name) {
        return entry(name).trigger();
    }

    public Trigger pause(String name) {
        Entry entry = entry(name);
        if (entry.trigger().running()) {
            if (entry.trigger().kind() == Trigger.Kind.SCHEDULE) {
                tower.stopSchedule(entry.stateId());
            } else {
                tower.stopSensor(entry.stateId());
            }
        }
        return find(name);
    }

    public Trigger resume(String name) {
        Entry entry = entry(name);
        if (!entry.trigger().running()) {
            if (entry.trigger().kind() == Trigger.Kind.SCHEDULE) {
                tower.startSchedule(name);
            } else {
                tower.startSensor(name);
            }
        }
        return find(name);
    }

    private Entry entry(String name) {
        return entries(tower.automation()).stream()
                .filter(entry -> entry.trigger().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No schedule or sensor named " + name));
    }

    private record Entry(Trigger trigger, String stateId) {}
}
