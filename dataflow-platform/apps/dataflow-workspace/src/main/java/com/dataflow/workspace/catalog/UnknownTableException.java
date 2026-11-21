package com.dataflow.workspace.catalog;

public class UnknownTableException extends RuntimeException {

    public UnknownTableException(String dataset, String table) {
        super("Dataset " + dataset + " has no table '" + table + "'");
    }
}
