package com.dataflow.workspace.specs;

import com.dataflow.spec.PipelineSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

public record SpecCheck(@JsonIgnore PipelineSpec spec, List<Problem> problems) {

    public boolean valid() {
        return problems.isEmpty();
    }
}
