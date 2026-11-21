package com.dataflow.engine.core.transform;

import com.dataflow.spec.StepSpec;

public class StepFailedException extends RuntimeException {

    public StepFailedException(String message) {
        super(message);
    }

    public StepFailedException(String message, Throwable cause) {
        super(message, cause);
    }

    static StepFailedException of(StepSpec step, String problem) {
        return new StepFailedException(message(step, problem));
    }

    static StepFailedException of(StepSpec step, String problem, Throwable cause) {
        return new StepFailedException(message(step, problem), cause);
    }

    private static String message(StepSpec step, String problem) {
        return "Step " + step.name() + ": " + problem;
    }
}
