package com.dataflow.spec;

import java.util.List;

public record DedupeSpec(List<String> keys, String latestBy) {

    public DedupeSpec {
        keys = PipelineSpec.orEmpty(keys);
    }
}
