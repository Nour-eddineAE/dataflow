package com.dataflow.workspace.queries;

import java.time.Instant;
import java.util.UUID;

public record SavedQuery(UUID id, String owner, String name, String sql, boolean shared, Instant updatedAt) {}
