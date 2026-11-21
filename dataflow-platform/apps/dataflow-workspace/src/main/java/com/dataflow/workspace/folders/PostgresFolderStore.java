package com.dataflow.workspace.folders;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PostgresFolderStore implements FolderStore {

    private final DSLContext sql;

    public PostgresFolderStore(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    private static GitFolder folder(Record row) {
        return new GitFolder(
                row.get(Tables.GitFolder.ID),
                row.get(Tables.GitFolder.PATH),
                row.get(Tables.GitFolder.REMOTE_URL),
                row.get(Tables.GitFolder.BRANCH),
                row.get(Tables.GitFolder.CREDENTIAL_ID),
                row.get(Tables.GitFolder.OWNER),
                instant(row.get(Tables.GitFolder.CREATED_AT)));
    }

    private static Draft draft(Record row) {
        return new Draft(
                row.get(Tables.Draft.FOLDER_ID),
                row.get(Tables.Draft.BRANCH),
                row.get(Tables.Draft.FILE_PATH),
                row.get(Tables.Draft.CONTENT),
                row.get(Tables.Draft.BASE_COMMIT),
                row.get(Tables.Draft.SAVED_BY),
                instant(row.get(Tables.Draft.SAVED_AT)));
    }

    private static GitCredential credential(Record row) {
        return new GitCredential(
                row.get(Tables.GitCredential.ID),
                row.get(Tables.GitCredential.OWNER),
                row.get(Tables.GitCredential.NAME),
                row.get(Tables.GitCredential.USERNAME),
                row.get(Tables.GitCredential.PROVIDER),
                instant(row.get(Tables.GitCredential.CREATED_AT)));
    }

    static OffsetDateTime offset(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    static Instant instant(OffsetDateTime time) {
        return time == null ? null : time.toInstant();
    }

    @Override
    public List<GitFolder> folders() {
        return sql.select()
                .from(Tables.GitFolder.TABLE)
                .orderBy(Tables.GitFolder.PATH)
                .fetch(PostgresFolderStore::folder);
    }

    @Override
    public Optional<GitFolder> folder(UUID id) {
        return sql.select()
                .from(Tables.GitFolder.TABLE)
                .where(Tables.GitFolder.ID.eq(id))
                .fetchOptional(PostgresFolderStore::folder);
    }

    @Override
    public void insertFolder(GitFolder folder) {
        sql.insertInto(Tables.GitFolder.TABLE)
                .set(Tables.GitFolder.ID, folder.id())
                .set(Tables.GitFolder.PATH, folder.path())
                .set(Tables.GitFolder.REMOTE_URL, folder.remoteUrl())
                .set(Tables.GitFolder.BRANCH, folder.branch())
                .set(Tables.GitFolder.CREDENTIAL_ID, folder.credentialId())
                .set(Tables.GitFolder.OWNER, folder.owner())
                .set(Tables.GitFolder.CREATED_AT, offset(folder.createdAt()))
                .execute();
    }

    @Override
    public void updateBranch(UUID id, String branch) {
        sql.update(Tables.GitFolder.TABLE)
                .set(Tables.GitFolder.BRANCH, branch)
                .where(Tables.GitFolder.ID.eq(id))
                .execute();
    }

    @Override
    public void deleteFolder(UUID id) {
        sql.deleteFrom(Tables.GitFolder.TABLE).where(Tables.GitFolder.ID.eq(id)).execute();
    }

    @Override
    public List<Draft> drafts(UUID folderId, String branch) {
        return sql.select()
                .from(Tables.Draft.TABLE)
                .where(Tables.Draft.FOLDER_ID.eq(folderId))
                .and(Tables.Draft.BRANCH.eq(branch))
                .orderBy(Tables.Draft.FILE_PATH)
                .fetch(PostgresFolderStore::draft);
    }

    @Override
    public void saveDraft(Draft draft) {
        sql.insertInto(Tables.Draft.TABLE)
                .set(Tables.Draft.FOLDER_ID, draft.folderId())
                .set(Tables.Draft.BRANCH, draft.branch())
                .set(Tables.Draft.FILE_PATH, draft.path())
                .set(Tables.Draft.CONTENT, draft.content())
                .set(Tables.Draft.BASE_COMMIT, draft.baseCommit())
                .set(Tables.Draft.SAVED_BY, draft.savedBy())
                .set(Tables.Draft.SAVED_AT, offset(draft.savedAt()))
                .onConflict(Tables.Draft.FOLDER_ID, Tables.Draft.BRANCH, Tables.Draft.FILE_PATH)
                .doUpdate()
                .set(Tables.Draft.CONTENT, draft.content())
                .set(Tables.Draft.SAVED_BY, draft.savedBy())
                .set(Tables.Draft.SAVED_AT, offset(draft.savedAt()))
                .execute();
    }

    @Override
    public void deleteDrafts(UUID folderId, String branch, Collection<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        sql.deleteFrom(Tables.Draft.TABLE)
                .where(Tables.Draft.FOLDER_ID.eq(folderId))
                .and(Tables.Draft.BRANCH.eq(branch))
                .and(Tables.Draft.FILE_PATH.in(paths))
                .execute();
    }

    @Override
    public List<GitCredential> credentials(String owner) {
        return sql.select()
                .from(Tables.GitCredential.TABLE)
                .where(Tables.GitCredential.OWNER.eq(owner))
                .orderBy(Tables.GitCredential.NAME)
                .fetch(PostgresFolderStore::credential);
    }

    @Override
    public Optional<StoredSecret> secret(UUID credentialId) {
        return sql.select(
                        Tables.GitCredential.SECRET,
                        Tables.GitCredential.REFRESH_SECRET,
                        Tables.GitCredential.EXPIRES_AT)
                .from(Tables.GitCredential.TABLE)
                .where(Tables.GitCredential.ID.eq(credentialId))
                .fetchOptional(row -> new StoredSecret(
                        row.get(Tables.GitCredential.SECRET),
                        row.get(Tables.GitCredential.REFRESH_SECRET),
                        instant(row.get(Tables.GitCredential.EXPIRES_AT))));
    }

    @Override
    public void insertCredential(GitCredential credential, StoredSecret secret) {
        sql.insertInto(Tables.GitCredential.TABLE)
                .set(Tables.GitCredential.ID, credential.id())
                .set(Tables.GitCredential.OWNER, credential.owner())
                .set(Tables.GitCredential.NAME, credential.name())
                .set(Tables.GitCredential.USERNAME, credential.username())
                .set(Tables.GitCredential.PROVIDER, credential.provider())
                .set(Tables.GitCredential.SECRET, secret.secret())
                .set(Tables.GitCredential.REFRESH_SECRET, secret.refreshSecret())
                .set(Tables.GitCredential.EXPIRES_AT, offset(secret.expiresAt()))
                .set(Tables.GitCredential.CREATED_AT, offset(credential.createdAt()))
                .execute();
    }

    @Override
    public void updateSecret(UUID credentialId, StoredSecret secret) {
        sql.update(Tables.GitCredential.TABLE)
                .set(Tables.GitCredential.SECRET, secret.secret())
                .set(Tables.GitCredential.REFRESH_SECRET, secret.refreshSecret())
                .set(Tables.GitCredential.EXPIRES_AT, offset(secret.expiresAt()))
                .where(Tables.GitCredential.ID.eq(credentialId))
                .execute();
    }

    @Override
    public void deleteCredential(UUID id, String owner) {
        sql.deleteFrom(Tables.GitCredential.TABLE)
                .where(Tables.GitCredential.ID.eq(id))
                .and(Tables.GitCredential.OWNER.eq(owner))
                .execute();
    }
}
