package com.dataflow.spec;

public record ExpectationSpec(String name, String check, OnFailure onFailure) {

    public ExpectationSpec {
        onFailure = onFailure == null ? OnFailure.WARN : onFailure;
    }

    public enum OnFailure {
        WARN,
        DROP,
        FAIL
    }
}
