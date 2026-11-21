package com.dataflow.workspace.serving;

public class UnknownFunctionException extends RuntimeException {

    public UnknownFunctionException(String name) {
        super("ClickHouse has no function '" + name + "'");
    }
}
