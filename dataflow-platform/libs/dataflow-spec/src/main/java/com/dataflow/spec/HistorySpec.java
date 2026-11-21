package com.dataflow.spec;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record HistorySpec(
        List<String> keys,
        String sequenceBy,
        StoredAs storedAs,
        List<String> trackHistoryExcept,
        Integer keepClosedDays) {

    public HistorySpec {
        keys = PipelineSpec.orEmpty(keys);
        storedAs = storedAs == null ? StoredAs.SCD2 : storedAs;
        trackHistoryExcept = PipelineSpec.orEmpty(trackHistoryExcept);
    }

    public enum StoredAs {
        @JsonProperty("scd2")
        SCD2
    }
}
