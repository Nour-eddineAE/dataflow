package com.dataflow.engine.core.run;

import java.util.Map;

public record Materialization(String assetKey, Map<String, Object> metadata) {}
