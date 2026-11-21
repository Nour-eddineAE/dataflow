package com.dataflow.engine.release;

import com.dataflow.spec.PipelineSpecs;

import java.nio.file.Path;

public record LoadedProject(Path root, PipelineSpecs pipelines) {}
