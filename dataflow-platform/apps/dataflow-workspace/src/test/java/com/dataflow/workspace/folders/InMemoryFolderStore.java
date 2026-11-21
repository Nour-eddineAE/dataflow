package com.dataflow.workspace.folders;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class InMemoryFolderStore implements FolderStore {

    private final Map<UUID, GitFolder> folders = new LinkedHashMap<>();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Map<UUID, GitCredential> credentials = new LinkedHashMap<>();
    private final Map<UUID, StoredSecret> secrets = new LinkedHashMap<>();

    private static String key(UUID folderId, String branch, String path) {
        return folderId + "|" + branch + "|" + path;
    }

    @Override
    public List<GitFolder> folders() {
        return folders.values().stream()
                .sorted(Comparator.comparing(GitFolder::path))
                .toList();
    }

    @Override
    public Optional<GitFolder> folder(UUID id) {
        return Optional.ofNullable(folders.get(id));
    }

    @Override
    public void insertFolder(GitFolder folder) {
        folders.put(folder.id(), folder);
    }

    @Override
    public void updateBranch(UUID id, String branch) {
        folders.computeIfPresent(id, (key, folder) -> folder.withBranch(branch));
    }

    @Override
    public void deleteFolder(UUID id) {
        folders.remove(id);
        drafts.values().removeIf(draft -> draft.folderId().equals(id));
    }

    @Override
    public List<Draft> drafts(UUID folderId, String branch) {
        return drafts.values().stream()
                .filter(draft ->
                        draft.folderId().equals(folderId) && draft.branch().equals(branch))
                .sorted(Comparator.comparing(Draft::path))
                .toList();
    }

    @Override
    public void saveDraft(Draft draft) {
        drafts.put(key(draft.folderId(), draft.branch(), draft.path()), draft);
    }

    @Override
    public void deleteDrafts(UUID folderId, String branch, Collection<String> paths) {
        paths.forEach(path -> drafts.remove(key(folderId, branch, path)));
    }

    @Override
    public List<GitCredential> credentials(String owner) {
        return new ArrayList<>(credentials.values().stream()
                .filter(c -> c.owner().equals(owner))
                .toList());
    }

    @Override
    public Optional<StoredSecret> secret(UUID credentialId) {
        return Optional.ofNullable(secrets.get(credentialId));
    }

    @Override
    public void insertCredential(GitCredential credential, StoredSecret secret) {
        credentials.put(credential.id(), credential);
        secrets.put(credential.id(), secret);
    }

    @Override
    public void updateSecret(UUID credentialId, StoredSecret secret) {
        secrets.put(credentialId, secret);
    }

    @Override
    public void deleteCredential(UUID id, String owner) {
        credentials.remove(id);
        secrets.remove(id);
    }
}
