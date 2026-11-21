package com.dataflow.spec;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class PipelineSpecs {

    private final Map<String, PipelineSpec> plans;

    public PipelineSpecs(Map<String, PipelineSpec> plans) {
        this.plans = new TreeMap<>(plans);
        Map<String, String> servedBy = new HashMap<>();
        for (PipelineSpec plan : this.plans.values()) {
            for (ServeSpec serve : plan.serve()) {
                String previous = servedBy.putIfAbsent(serve.table(), plan.dataset());
                if (previous != null) {
                    throw new IllegalStateException("Serving table '" + serve.table() + "' is served by both "
                            + previous + " and " + plan.dataset());
                }
            }
        }
    }

    public PipelineSpec get(String dataset) {
        return Optional.ofNullable(dataset)
                .map(plans::get)
                .orElseThrow(() -> new UnknownDatasetException(dataset, plans.keySet()));
    }

    public Collection<PipelineSpec> all() {
        return plans.values();
    }
}
