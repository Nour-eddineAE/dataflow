package com.dataflow.workspace.specs;

import com.dataflow.spec.PipelineSpec;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.nio.file.Path;
import java.util.List;

public record StoredSpec(
        String dataset,
        @JsonIgnore Path file,
        String content,
        @JsonIgnore PipelineSpec spec,
        List<Problem> problems) {

    public boolean valid() {
        return spec != null;
    }
}
