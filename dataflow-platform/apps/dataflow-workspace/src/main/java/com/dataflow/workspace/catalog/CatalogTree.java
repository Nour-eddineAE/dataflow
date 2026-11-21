package com.dataflow.workspace.catalog;

import java.util.List;

public record CatalogTree(List<DatasetTables> datasets, List<ServedTable> serving) {

    public record DatasetTables(String dataset, boolean valid, List<LakeTable> tables) {}
}
