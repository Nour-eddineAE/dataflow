package com.dataflow.workspace.catalog;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DeltaDetails(
        Map<String, String> properties,
        List<String> partitionColumns,
        List<String> clusteringColumns,
        Integer minReaderVersion,
        Integer minWriterVersion,
        List<String> features,
        Instant createdAt) {}
