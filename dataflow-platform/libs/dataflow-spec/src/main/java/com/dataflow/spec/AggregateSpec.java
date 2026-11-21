package com.dataflow.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AggregateSpec(List<String> by, Map<String, String> measures) {

    public AggregateSpec {
        by = PipelineSpec.orEmpty(by);
        measures = measures == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(measures));
    }
}
