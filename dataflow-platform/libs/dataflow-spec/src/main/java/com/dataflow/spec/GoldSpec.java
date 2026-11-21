package com.dataflow.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record GoldSpec(
        String name,
        String sql,
        String transformation,
        Map<String, String> inputs,
        Map<String, Object> params,
        String from,
        List<GoldStepSpec> steps,
        Boolean materialize) {


    public GoldSpec {
        inputs = inputs == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
        params = params == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(params));
        steps = PipelineSpec.orEmpty(steps);
        materialize = materialize == null ? Boolean.TRUE : materialize;
    }

    public GoldSpec(String name, String sql) {
        this(name, sql, null, null, null, null, null, null);
    }

    public boolean usesTransformation() {
        return transformation != null;
    }

    public boolean usesSteps() {
        return from != null;
    }

    public Body body() {
        int declared = (sql == null ? 0 : 1) + (transformation == null ? 0 : 1) + (from == null ? 0 : 1);
        if (declared != 1) {
            return Body.INVALID;
        }
        return sql != null ? Body.SQL : transformation != null ? Body.TRANSFORMATION : Body.STEPS;
    }

    public StepSpec asStep() {
        Map<String, String> wired = new LinkedHashMap<>();
        inputs.forEach((input, source) -> wired.put(input, ServeSpec.CURRENT.equals(source) ? StepSpec.ROWS : source));
        return new StepSpec(name, transformation, wired, params, List.of());
    }

    public List<String> references() {
        return inputs.values().stream()
                .filter(source -> source != null && source.startsWith(StepSpec.REFERENCE))
                .map(source -> source.substring(StepSpec.REFERENCE.length()))
                .toList();
    }

    public enum Body {
        SQL,
        TRANSFORMATION,
        STEPS,
        INVALID
    }
}
