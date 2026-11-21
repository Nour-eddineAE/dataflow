package com.dataflow.workspace.folders;

import com.dataflow.workspace.specs.Problem;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FolderViews {

    private FolderViews() {}

    public enum FileState {
        UNCHANGED,
        MODIFIED,
        ADDED,
        DELETED
    }

    public record FolderSummary(
            UUID id, String path, String remoteUrl, String branch, String owner, String project, int drafts) {}

    public record FolderFile(String path, FileState state) {}

    public record FolderTree(
            UUID id,
            String path,
            String remoteUrl,
            String branch,
            String head,
            String project,
            List<String> targets,
            Integer ahead,
            Integer behind,
            List<FolderFile> files) {}

    public record FileContent(String path, String content, String committed, FileState state, List<Problem> problems) {}

    public record CommitView(String id, String message, String author, Instant time) {}

    public record CreateFolder(
            String name, String remoteUrl, String branch, boolean shared, UUID credentialId, String team) {

        public CreateFolder(String name, String remoteUrl, String branch, boolean shared, UUID credentialId) {
            this(name, remoteUrl, branch, shared, credentialId, null);
        }
}

    public record Snapshot(GitFolder folder, String commit, Map<String, byte[]> files) {}
}
