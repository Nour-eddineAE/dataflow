package com.dataflow.workspace.catalog;

import java.time.Instant;
import java.util.List;

public record TableStorage(
        String table,
        String layer,
        long commits,
        long files,
        long bytes,
        long smallFiles,
        long waitingBytes,
        long reclaimedBytes,
        Instant updatedAt,
        List<SizeChange> sizeChanges) {}
