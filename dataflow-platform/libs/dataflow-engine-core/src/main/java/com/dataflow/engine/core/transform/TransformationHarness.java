package com.dataflow.engine.core.transform;

import com.dataflow.spec.StepSpec;
import com.dataflow.transformation.Transformation;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TransformationHarness {

    private final Transformation<?, ?> transformation;
    private final Map<String, Dataset<Row>> inputs = new LinkedHashMap<>();
    private final Map<String, Object> params = new LinkedHashMap<>();

    private TransformationHarness(Transformation<?, ?> transformation) {
        this.transformation = transformation;
    }

    public static TransformationHarness of(Transformation<?, ?> transformation) {
        return new TransformationHarness(transformation);
    }

    public TransformationHarness input(String name, Dataset<Row> rows) {
        inputs.put(name, rows);
        return this;
    }

    public TransformationHarness param(String name, Object value) {
        params.put(name, value);
        return this;
    }

    public Result asSilverStep() {
        return run(PlanChecks.Placement.SILVER);
    }

    public Result asGold() {
        return run(PlanChecks.Placement.GOLD);
    }

    private Result run(PlanChecks.Placement placement) {
        Map<String, String> wired = new LinkedHashMap<>();
        inputs.keySet().forEach(name -> wired.put(name, name));
        StepSpec step = new StepSpec("test", transformation.getClass().getSimpleName(), wired, params, List.of());
        Dataset<Row> output = Binding.apply(
                transformation,
                Binding.inputs(transformation, step, inputs::get),
                Binding.params(transformation, step));
        PlanChecks.Findings findings = PlanChecks.check(output.queryExecution().analyzed(), 0, placement);
        return new Result(output, findings.errors(), findings.warnings());
    }

    public record Result(Dataset<Row> output, List<String> errors, List<String> warnings) {

        public boolean passed() {
            return errors.isEmpty();
        }
    }
}
