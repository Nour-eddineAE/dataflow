package com.dataflow.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BronzeSpec(SchemaEvolution schemaEvolution, Integer maxFilesPerTrigger) {

    public static final BronzeSpec DEFAULT = new BronzeSpec(null, null);

    private static final int DEFAULT_MAX_FILES_PER_TRIGGER = 10;

    public BronzeSpec {
        schemaEvolution = schemaEvolution == null ? SchemaEvolution.RESCUE : schemaEvolution;
        maxFilesPerTrigger = maxFilesPerTrigger == null ? DEFAULT_MAX_FILES_PER_TRIGGER : maxFilesPerTrigger;
    }

    public enum SchemaEvolution {
        @JsonProperty("rescue")
        RESCUE,
        @JsonProperty("add-new-columns")
        ADD_NEW_COLUMNS
    }
}
