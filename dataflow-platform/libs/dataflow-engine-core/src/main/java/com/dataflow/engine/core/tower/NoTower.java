package com.dataflow.engine.core.tower;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

@Slf4j
public class NoTower implements TowerReporter {

    @Override
    public Optional<String> runId() {
        return Optional.empty();
    }

    @Override
    public Optional<String> partitionKey() {
        return Optional.empty();
    }

    @Override
    public void materialized(String assetKey, Map<String, ?> metadata) {
        log.debug("Materialized {} {}", assetKey, metadata);
    }

    @Override
    public void checked(String assetKey, String check, boolean passed, Severity severity, Map<String, ?> metadata) {
        log.debug("Check {} on {}: {} {}", check, assetKey, passed ? "passed" : "failed", metadata);
    }

    @Override
    public void log(String level, String message) {
        log.debug(message);
    }

    @Override
    public void close() {}
}
