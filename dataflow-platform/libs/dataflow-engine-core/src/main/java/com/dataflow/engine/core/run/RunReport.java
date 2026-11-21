package com.dataflow.engine.core.run;

import java.util.List;

public record RunReport(String runId, String dataset, FlowStatus status, List<FlowOutcome> flows) {

    public boolean completed() {
        return status == FlowStatus.COMPLETED;
    }
}
