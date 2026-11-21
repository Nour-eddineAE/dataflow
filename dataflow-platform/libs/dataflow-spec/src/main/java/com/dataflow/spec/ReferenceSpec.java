package com.dataflow.spec;

import java.util.Map;

public record ReferenceSpec(String name, String url, String path, String format, Map<String, String> options)
        implements FileSource {

    public ReferenceSpec {
        options = options == null ? Map.of() : Map.copyOf(options);
    }
}
