package com.dataflow.workspace.folders;

import java.time.Instant;
import java.util.UUID;

public record GitCredential(UUID id, String owner, String name, String username, String provider, Instant createdAt) {}
