package com.dataflow.workspace.folders;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FolderStore {

    List<GitFolder> folders();

    Optional<GitFolder> folder(UUID id);

    void insertFolder(GitFolder folder);

    void updateBranch(UUID id, String branch);

    void deleteFolder(UUID id);

    List<Draft> drafts(UUID folderId, String branch);

    void saveDraft(Draft draft);

    void deleteDrafts(UUID folderId, String branch, Collection<String> paths);

    List<GitCredential> credentials(String owner);

    Optional<StoredSecret> secret(UUID credentialId);

    void insertCredential(GitCredential credential, StoredSecret secret);

    void updateSecret(UUID credentialId, StoredSecret secret);

    void deleteCredential(UUID id, String owner);
}
