package com.dataflow.spec;

import java.util.Map;

public record SourceSpec(String url, String path, String format, Map<String, String> options, PartitionsSpec partitions)
        implements FileSource {

    public static final String PARTITION_PLACEHOLDER = "{partition}";

    public SourceSpec {
        options = options == null ? Map.of() : Map.copyOf(options);
    }

    private static String fill(String template, String partition) {
        return template == null ? null : template.replace(PARTITION_PLACEHOLDER, partition);
    }

    public boolean partitioned() {
        return partitions != null;
    }

    public SourceSpec forPartition(String partition) {
        if (!partitioned()) {
            return this;
        }
        return new SourceSpec(fill(url, partition), fill(path, partition), format, options, partitions);
    }
}
