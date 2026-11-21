package com.dataflow.engine.core.tower;

import java.util.Map;
import java.util.Optional;

public interface TowerReporter extends AutoCloseable {

    Optional<String> runId();

    Optional<String> partitionKey();

    void materialized(String assetKey, Map<String, ?> metadata);

    void checked(String assetKey, String check, boolean passed, Severity severity, Map<String, ?> metadata);

    void log(String level, String message);

    @Override
    void close();
}
