package com.dataflow.workspace.serving;

public record QueryRun(
        String time,
        String person,
        String query,
        long durationMs,
        long rowsRead,
        long bytesRead,
        long resultRows,
        String status,
        String error) {}
