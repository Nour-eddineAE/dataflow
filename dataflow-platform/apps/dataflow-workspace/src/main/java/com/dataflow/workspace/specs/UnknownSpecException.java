package com.dataflow.workspace.specs;

public class UnknownSpecException extends RuntimeException {

    public UnknownSpecException(String dataset) {
        super("No pipeline spec named '" + dataset + "'");
    }
}
