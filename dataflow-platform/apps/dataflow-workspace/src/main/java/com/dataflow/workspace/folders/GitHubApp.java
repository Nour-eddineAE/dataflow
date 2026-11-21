package com.dataflow.workspace.folders;

import java.time.Instant;

public record GitHubApp(
        String host,
        long appId,
        String slug,
        String clientId,
        String clientSecret,
        String privateKey,
        String createdBy,
        Instant createdAt) {}
