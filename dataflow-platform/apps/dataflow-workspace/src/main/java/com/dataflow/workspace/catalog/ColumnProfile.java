package com.dataflow.workspace.catalog;

public record ColumnProfile(String name, String type, long nulls, Long distinct, String min, String max) {}
