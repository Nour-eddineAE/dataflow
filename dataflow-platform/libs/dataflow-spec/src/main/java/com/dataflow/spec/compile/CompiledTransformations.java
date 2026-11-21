package com.dataflow.spec.compile;

import java.nio.file.Path;
import java.util.Map;

public record CompiledTransformations(ClassLoader loader, Map<String, Class<?>> byId, Path artifact) {}
