package com.dataflow.workspace.serving;

import java.util.List;

public record QueryResult(
        List<Column> columns,
        List<List<Object>> rows,
        boolean truncated,
        long elapsedMs,
        long rowsRead,
        long bytesRead) {

    public int rowCount() {
        return rows.size();
    }

    public int index(String column) {
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).name().equals(column)) {
                return i;
            }
        }
        throw new IllegalArgumentException("No column " + column + " in " + columns);
    }

    public record Column(String name, String type) {}
}
