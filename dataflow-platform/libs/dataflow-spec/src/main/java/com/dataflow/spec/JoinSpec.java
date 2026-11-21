package com.dataflow.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public record JoinSpec(String with, Type type, Map<String, String> on, String condition, String prefix) {

    public static final String LEFT = "left";
    public static final String RIGHT = "right";

    public JoinSpec {
        type = type == null ? Type.INNER : type;
        on = on == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(on));
    }

    public String prefixed(String column) {
        return prefix == null ? column : prefix + column.substring(0, 1).toUpperCase(Locale.ROOT) + column.substring(1);
    }

    public enum Type {
        @JsonProperty("inner")
        INNER("inner", true),
        @JsonProperty("left")
        LEFT("left_outer", true),
        @JsonProperty("right")
        RIGHT("right_outer", true),
        @JsonProperty("full")
        FULL("full_outer", true),
        @JsonProperty("semi")
        SEMI("left_semi", false),
        @JsonProperty("anti")
        ANTI("left_anti", false);

        private final String spark;
        private final boolean keepsRight;

        Type(String spark, boolean keepsRight) {
            this.spark = spark;
            this.keepsRight = keepsRight;
        }

        public String spark() {
            return spark;
        }

        public boolean keepsRight() {
            return keepsRight;
        }
    }
}
