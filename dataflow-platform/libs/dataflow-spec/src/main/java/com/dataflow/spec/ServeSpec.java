package com.dataflow.spec;

import com.dataflow.spec.lake.Layer;
import com.fasterxml.jackson.annotation.JsonProperty;

public record ServeSpec(String from, String table, Mode mode, String orderBy) {

    public static final String CURRENT = "current";
    public static final String GOLD_PREFIX = Layer.GOLD.segment() + ".";

    public ServeSpec {
        mode = mode == null ? Mode.VIEW : mode;
    }

    public boolean servesCurrent() {
        return CURRENT.equals(from);
    }

    public String goldName() {
        return from.substring(GOLD_PREFIX.length());
    }

    public enum Mode {
        @JsonProperty("view")
        VIEW,
        @JsonProperty("copy")
        COPY
    }
}
