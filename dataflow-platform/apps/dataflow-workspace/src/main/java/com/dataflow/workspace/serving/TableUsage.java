package com.dataflow.workspace.serving;

public record TableUsage(String table, long queries, String last) {}
