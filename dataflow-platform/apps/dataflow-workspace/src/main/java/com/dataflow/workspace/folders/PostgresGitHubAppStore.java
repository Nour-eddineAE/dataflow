package com.dataflow.workspace.folders;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class PostgresGitHubAppStore implements GitHubAppStore {

    private final DSLContext sql;

    public PostgresGitHubAppStore(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    @Override
    public Optional<GitHubApp> find(String host) {
        return sql.selectFrom(Tables.GitHubApp.TABLE)
                .where(Tables.GitHubApp.HOST.eq(host))
                .fetchOptional(row -> new GitHubApp(
                        row.get(Tables.GitHubApp.HOST),
                        row.get(Tables.GitHubApp.APP_ID),
                        row.get(Tables.GitHubApp.SLUG),
                        row.get(Tables.GitHubApp.CLIENT_ID),
                        row.get(Tables.GitHubApp.CLIENT_SECRET),
                        row.get(Tables.GitHubApp.PRIVATE_KEY),
                        row.get(Tables.GitHubApp.CREATED_BY),
                        PostgresFolderStore.instant(row.get(Tables.GitHubApp.CREATED_AT))));
    }

    @Override
    public void save(GitHubApp app) {
        sql.insertInto(Tables.GitHubApp.TABLE)
                .set(Tables.GitHubApp.HOST, app.host())
                .set(Tables.GitHubApp.APP_ID, app.appId())
                .set(Tables.GitHubApp.SLUG, app.slug())
                .set(Tables.GitHubApp.CLIENT_ID, app.clientId())
                .set(Tables.GitHubApp.CLIENT_SECRET, app.clientSecret())
                .set(Tables.GitHubApp.PRIVATE_KEY, app.privateKey())
                .set(Tables.GitHubApp.CREATED_BY, app.createdBy())
                .set(Tables.GitHubApp.CREATED_AT, PostgresFolderStore.offset(app.createdAt()))
                .onConflict(Tables.GitHubApp.HOST)
                .doUpdate()
                .set(Tables.GitHubApp.APP_ID, app.appId())
                .set(Tables.GitHubApp.SLUG, app.slug())
                .set(Tables.GitHubApp.CLIENT_ID, app.clientId())
                .set(Tables.GitHubApp.CLIENT_SECRET, app.clientSecret())
                .set(Tables.GitHubApp.PRIVATE_KEY, app.privateKey())
                .set(Tables.GitHubApp.CREATED_BY, app.createdBy())
                .set(Tables.GitHubApp.CREATED_AT, PostgresFolderStore.offset(app.createdAt()))
                .execute();
    }
}
