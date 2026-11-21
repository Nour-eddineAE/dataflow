package com.dataflow.engine.core.gold;

public class GoldStepException extends RuntimeException {

    public GoldStepException(String message) {
        super(message);
    }

    public GoldStepException(String message, Throwable cause) {
        super(message, cause);
    }
}
