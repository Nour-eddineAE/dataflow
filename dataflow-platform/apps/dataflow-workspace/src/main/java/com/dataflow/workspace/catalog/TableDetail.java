package com.dataflow.workspace.catalog;

import java.util.List;

public record TableDetail(
        String dataset,
        LakeTable table,
        List<ColumnInfo> columns,
        Long rows,
        Long files,
        Long bytes,
        Commit lastCommit) {}
