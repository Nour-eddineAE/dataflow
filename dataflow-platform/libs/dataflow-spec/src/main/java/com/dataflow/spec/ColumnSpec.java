package com.dataflow.spec;

public record ColumnSpec(String name, String type, boolean required, String from) {}
