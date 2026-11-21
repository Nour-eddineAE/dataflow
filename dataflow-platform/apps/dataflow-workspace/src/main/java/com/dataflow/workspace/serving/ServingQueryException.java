package com.dataflow.workspace.serving;

public class ServingQueryException extends RuntimeException {

    public ServingQueryException(String message) {
        super(message);
    }

    public ServingQueryException(String message, Throwable cause) {
        super(message, cause);
    }
}
