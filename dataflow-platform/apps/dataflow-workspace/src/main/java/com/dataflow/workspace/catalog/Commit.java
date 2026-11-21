package com.dataflow.workspace.catalog;

public record Commit(
        long version, String timestamp, String operation, String parameters, String metrics, String engine) {}
