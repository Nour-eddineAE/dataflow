package com.dataflow.spec;

import java.util.Collection;

public class UnknownDatasetException extends RuntimeException {

    public UnknownDatasetException(String dataset, Collection<String> known) {
        super("No pipeline spec for dataset '" + dataset + "'; known: " + known);
    }
}
