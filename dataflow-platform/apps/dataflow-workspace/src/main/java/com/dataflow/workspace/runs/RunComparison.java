package com.dataflow.workspace.runs;

import java.util.List;

public record RunComparison(String previousRunId, List<FlowRun> previousFlows) {

    public static final RunComparison NONE = new RunComparison(null, List.of());
}
