package com.dataflow.spec;

import java.util.regex.Pattern;

public record ReleaseId(String project, String commit) {

    public static final String MANIFEST = "release.json";
    private static final int SHORT_COMMIT = 10;
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");

    public ReleaseId {
        if (project == null || !ProjectSpecValidator.PROJECT.matcher(project).matches()) {
            throw new IllegalArgumentException(
                    "Release project must match " + ProjectSpecValidator.PROJECT + ", got '" + project + "'");
        }
        if (commit == null || !COMMIT.matcher(commit).matches()) {
            throw new IllegalArgumentException("Release commit must be a full commit id, got '" + commit + "'");
        }
    }

    public static ReleaseId parse(String value) {
        int at = value == null ? -1 : value.indexOf('@');
        if (at < 0) {
            throw new IllegalArgumentException("A release reads <project>@<commit>, got '" + value + "'");
        }
        return new ReleaseId(value.substring(0, at), value.substring(at + 1));
    }

    public String folder() {
        return project + "/" + commit;
    }

    public String shortCommit() {
        return commit.substring(0, SHORT_COMMIT);
    }

    @Override
    public String toString() {
        return project + "@" + commit;
    }
}
