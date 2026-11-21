package com.dataflow.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record StepSpec(
        String name,
        String transformation,
        Map<String, String> inputs,
        Map<String, Object> params,
        List<ColumnSpec> adds) {

    public static final String ROWS = "rows";
    public static final String REFERENCE = "reference.";

    public StepSpec {
        inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        adds = PipelineSpec.orEmpty(adds);
    }

    public String transformationName() {
        int at = transformation == null ? -1 : transformation.indexOf('@');
        return at < 0 ? transformation : transformation.substring(0, at);
    }

    public int transformationVersion() {
        int at = transformation == null ? -1 : transformation.indexOf('@');
        return at < 0 ? 0 : Integer.parseInt(transformation.substring(at + 1));
    }

    public List<String> references() {
        return inputs.values().stream()
                .filter(source -> source.startsWith(REFERENCE))
                .map(source -> source.substring(REFERENCE.length()))
                .toList();
    }
}
