package com.dataflow.workspace.catalog;

public record LiveFiles(long files, long bytes, long smallFiles, long reclaimedBytes) {}
