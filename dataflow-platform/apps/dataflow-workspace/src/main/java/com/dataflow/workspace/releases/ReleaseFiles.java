package com.dataflow.workspace.releases;

import com.dataflow.spec.ReleaseId;

import java.time.Instant;
import java.util.List;

public final class ReleaseFiles {

    public static final String TARGETS = "targets";

    private ReleaseFiles() {}

    public static String file(ReleaseId release, String path) {
        return release.folder() + "/" + path;
    }

    public static String manifest(ReleaseId release) {
        return file(release, ReleaseId.MANIFEST);
    }

    public static String pointer(String project, String target) {
        return project + "/" + TARGETS + "/" + target + ".json";
    }

    public record Manifest(
            String project, String commit, String target, Instant releasedAt, String releasedBy, List<String> files) {}

    public record Pointer(String project, String target, String commit, Instant releasedAt, String releasedBy) {

        public ReleaseId id() {
            return new ReleaseId(project, commit);
        }
    }
}
