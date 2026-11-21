package com.dataflow.workspace.impact;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Locale;

public enum Area {
    DATASET,
    SCHEDULE,
    SOURCE,
    BRONZE,
    SILVER,
    QUALITY,
    HISTORY,
    REFERENCES,
    GOLD,
    SERVING;

    @JsonValue
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
