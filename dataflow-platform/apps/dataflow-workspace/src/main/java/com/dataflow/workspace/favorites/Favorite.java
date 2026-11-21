package com.dataflow.workspace.favorites;

import java.time.Instant;

public record Favorite(Kind kind, String ref, Instant createdAt) {

    public enum Kind {
        PIPELINE,
        TABLE,
        SERVED,
        QUERY
    }
}
