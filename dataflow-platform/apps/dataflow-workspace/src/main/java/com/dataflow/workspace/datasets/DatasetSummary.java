package com.dataflow.workspace.datasets;

import com.dataflow.workspace.runs.RunSummary;
import com.dataflow.workspace.specs.Problem;

import java.util.List;
import java.util.Map;

public record DatasetSummary(
        String dataset,
        boolean valid,
        Integer version,
        String owner,
        String description,
        String schedule,
        String partitions,
        String source,
        String format,
        Map<String, Integer> counts,
        List<String> keys,
        RunSummary lastRun,
        List<Problem> problems) {}
