package com.dataflow.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TargetSpec(Mode mode, String branch, ComputeSpec compute) {

    public TargetSpec {
        compute = compute == null ? ComputeSpec.DEFAULT : compute;
    }

    public boolean production() {
        return mode == Mode.PRODUCTION;
    }

    public enum Mode {
        @JsonProperty("development")
        DEVELOPMENT,
        @JsonProperty("production")
        PRODUCTION
    }
}
