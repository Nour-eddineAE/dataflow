package com.dataflow.workspace.housekeeping;

import java.util.List;

public record Candidate(Kind kind, String location, long files, long bytes, List<String> keys) {

    public enum Kind {
        PREVIEWS,
        EVENT_LOGS,
        CHECKPOINTS,
        UPLOADS,
        RELEASES,
        CATALOGS
    }
}
