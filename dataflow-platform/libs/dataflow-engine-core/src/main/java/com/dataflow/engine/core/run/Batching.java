package com.dataflow.engine.core.run;

public record Batching(long maxRecordsPerFile, int maxFilesPerBatch) {

    public static final Batching DEFAULT = new Batching(500_000, 2);
}
