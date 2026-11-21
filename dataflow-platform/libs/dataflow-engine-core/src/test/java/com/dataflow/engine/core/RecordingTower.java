package com.dataflow.engine.core;

import com.dataflow.engine.core.tower.Severity;
import com.dataflow.engine.core.tower.TowerReporter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class RecordingTower implements TowerReporter {

    public final List<String> materialized = new ArrayList<>();
    public final List<Check> checks = new ArrayList<>();

    @Override
    public Optional<String> runId() {
        return Optional.empty();
    }

    @Override
    public Optional<String> partitionKey() {
        return Optional.empty();
    }

    @Override
    public synchronized void materialized(String assetKey, Map<String, ?> metadata) {
        materialized.add(assetKey);
    }

    @Override
    public synchronized void checked(
            String assetKey, String check, boolean passed, Severity severity, Map<String, ?> metadata) {
        checks.add(new Check(assetKey, check, passed, severity, metadata));
    }

    @Override
    public void log(String level, String message) {}

    @Override
    public void close() {}

    public record Check(String assetKey, String name, boolean passed, Severity severity, Map<String, ?> metadata) {}
}
