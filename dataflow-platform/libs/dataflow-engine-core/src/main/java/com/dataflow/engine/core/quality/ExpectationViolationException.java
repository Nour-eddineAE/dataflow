package com.dataflow.engine.core.quality;

import java.util.Map;

public class ExpectationViolationException extends RuntimeException {

    private final transient Map<String, Long> violated;
    private final transient Map<String, Long> failures;
    private final long evaluated;

    public ExpectationViolationException(
            String dataset, Map<String, Long> violated, Map<String, Long> failures, long evaluated) {
        super("Expectations with on-failure: fail broke for " + dataset + ": " + violated);
        this.violated = Map.copyOf(violated);
        this.failures = Map.copyOf(failures);
        this.evaluated = evaluated;
    }

    public Map<String, Long> violated() {
        return violated;
    }

    public Map<String, Long> failures() {
        return failures;
    }

    public long evaluated() {
        return evaluated;
    }
}
