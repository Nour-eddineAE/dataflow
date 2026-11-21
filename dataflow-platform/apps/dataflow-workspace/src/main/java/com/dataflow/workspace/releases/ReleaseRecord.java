package com.dataflow.workspace.releases;

import java.time.Instant;
import java.util.UUID;

public record ReleaseRecord(
        String project, String target, String commit, UUID folderId, String releasedBy, Instant releasedAt) {}
