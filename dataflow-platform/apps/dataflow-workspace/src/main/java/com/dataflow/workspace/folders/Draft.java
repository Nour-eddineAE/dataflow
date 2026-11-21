package com.dataflow.workspace.folders;

import java.time.Instant;
import java.util.UUID;

public record Draft(
        UUID folderId, String branch, String path, String content, String baseCommit, String savedBy, Instant savedAt) {

    public boolean deleted() {
        return content == null;
    }
}
