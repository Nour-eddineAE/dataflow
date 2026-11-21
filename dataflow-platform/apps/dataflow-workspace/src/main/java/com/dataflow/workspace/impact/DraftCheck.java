package com.dataflow.workspace.impact;

import com.dataflow.workspace.datasets.PipelineGraph;
import com.dataflow.workspace.specs.Problem;

import java.util.List;

public record DraftCheck(
        boolean valid,
        List<Problem> problems,
        List<String> warnings,
        List<Change> impact,
        String dataset,
        PipelineGraph graph) {}
