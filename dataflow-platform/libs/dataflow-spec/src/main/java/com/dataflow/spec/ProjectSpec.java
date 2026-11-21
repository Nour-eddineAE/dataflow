package com.dataflow.spec;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public record ProjectSpec(String project, String description, Map<String, TargetSpec> targets) {

    public static final String FILE = "dataflow.yml";
    public static final String PIPELINES = "pipelines";

    public ProjectSpec {
        targets = targets == null ? Map.of() : Collections.unmodifiableMap(new TreeMap<>(targets));
    }

    public Optional<TargetSpec> target(String name) {
        return Optional.ofNullable(name).map(targets::get);
    }
}
