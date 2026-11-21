package com.dataflow.workspace.runs;

public class TowerException extends RuntimeException {

    public TowerException(String message) {
        super(message);
    }

    public TowerException(String message, Throwable cause) {
        super(message, cause);
    }
}
