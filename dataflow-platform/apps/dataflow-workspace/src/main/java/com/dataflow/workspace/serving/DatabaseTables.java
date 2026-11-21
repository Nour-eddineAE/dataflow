package com.dataflow.workspace.serving;

public record DatabaseTables(String database, long tableCount, long lakeViews) {

    public boolean onlyLakeViews() {
        return tableCount > 0 && tableCount == lakeViews;
    }
}
