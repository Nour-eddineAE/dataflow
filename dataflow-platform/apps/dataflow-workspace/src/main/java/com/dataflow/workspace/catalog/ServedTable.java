package com.dataflow.workspace.catalog;

public record ServedTable(
        String dataset, String table, String sqlName, String from, String mode, String orderBy, boolean exists) {}
