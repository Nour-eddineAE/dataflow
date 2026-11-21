package com.dataflow.workspace.catalog;

import java.time.Instant;

public record TableLog(String key, long commits, Instant updatedAt) {}
