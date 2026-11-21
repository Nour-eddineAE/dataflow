package com.dataflow.workspace.catalog;

import java.time.Instant;

public record LakeTable(
        String id,
        String layer,
        String label,
        String key,
        String sqlName,
        String description,
        boolean delta,
        boolean exists,
        long commits,
        Instant updatedAt) {

    public LakeTable located(TableLog log) {
        return log == null
                ? new LakeTable(id, layer, label, key, sqlName, description, delta, false, 0, null)
                : new LakeTable(
                        id, layer, label, key, sqlName, description, delta, true, log.commits(), log.updatedAt());
    }
}
