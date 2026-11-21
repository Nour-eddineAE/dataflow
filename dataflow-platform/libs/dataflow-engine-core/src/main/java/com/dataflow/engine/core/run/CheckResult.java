package com.dataflow.engine.core.run;

import com.dataflow.engine.core.tower.Severity;

import java.util.Map;

public record CheckResult(
        String assetKey, String name, boolean passed, Severity severity, Map<String, Object> metadata) {}
