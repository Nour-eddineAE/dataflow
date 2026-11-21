package com.dataflow.spec;

import java.nio.file.Path;

public record Project(Path root, ProjectSpec spec, PipelineSpecs pipelines) {

    public String name() {
        return spec.project();
    }
}
