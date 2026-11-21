package com.dataflow.workspace.runs;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;

public record RunRuntime(String dataflow, String spark, String delta, String scala, String java) {

    static final List<String> KEYS =
            Arrays.stream(RunRuntime.class.getRecordComponents()).map(RecordComponent::getName).toList();
}
