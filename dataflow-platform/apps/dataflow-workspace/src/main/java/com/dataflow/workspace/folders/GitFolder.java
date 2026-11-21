package com.dataflow.workspace.folders;

import java.time.Instant;
import java.util.UUID;

public record GitFolder(
        UUID id, String path, String remoteUrl, String branch, UUID credentialId, String owner, Instant createdAt) {

    public GitFolder withBranch(String other) {
        return new GitFolder(id, path, remoteUrl, other, credentialId, owner, createdAt);
    }
}
