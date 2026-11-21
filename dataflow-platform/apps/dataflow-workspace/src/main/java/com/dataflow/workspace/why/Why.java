package com.dataflow.workspace.why;

import com.dataflow.workspace.lineage.Lineage;
import com.dataflow.workspace.serving.QueryResult;

import java.util.List;
import java.util.Map;

public record Why(
        String dataset,
        String factUniqueId,
        String column,
        String expression,
        Map<String, Object> current,
        QueryResult versions,
        QueryResult arrivals,
        QueryResult quarantined,
        QueryResult raw,
        String rawNote,
        List<Lineage.Edge> upstream,
        List<Lineage.Edge> downstream) {}
