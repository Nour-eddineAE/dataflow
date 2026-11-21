package com.dataflow.engine.core.draft;

import java.util.List;
import java.util.Map;

public record Inference(
        String dataset,
        String format,
        Map<String, String> options,
        long sampledRows,
        List<Guess> columns,
        List<String> keys,
        String sequenceBy,
        String schedule,
        String spec,
        List<Reason> reasons,
        List<String> problems) {

    public record Guess(
            String source,
            String name,
            String type,
            String from,
            boolean required,
            long nulls,
            long distinct,
            String min,
            String max) {}

    public record Reason(String choice, String why) {}
}
