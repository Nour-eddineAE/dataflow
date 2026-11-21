package com.dataflow.workspace.access;

import java.time.Instant;

public record AuditEntry(
        Instant at, String actor, String action, String securable, String principal, String privilege) {}
