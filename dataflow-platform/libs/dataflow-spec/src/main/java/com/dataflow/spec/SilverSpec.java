package com.dataflow.spec;

import java.util.List;

public record SilverSpec(
        List<ColumnSpec> columns, List<StepSpec> steps, List<ColumnSpec> derive, List<ExpectationSpec> expectations) {

    public SilverSpec {
        columns = PipelineSpec.orEmpty(columns);
        steps = PipelineSpec.orEmpty(steps);
        derive = PipelineSpec.orEmpty(derive);
        expectations = PipelineSpec.orEmpty(expectations);
    }

    public List<ColumnSpec> added() {
        return steps.stream().flatMap(step -> step.adds().stream()).toList();
    }
}
