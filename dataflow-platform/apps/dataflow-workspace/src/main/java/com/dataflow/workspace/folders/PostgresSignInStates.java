package com.dataflow.workspace.folders;

import com.dataflow.workspace.controlplane.ControlPlane;
import com.dataflow.workspace.controlplane.Tables;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class PostgresSignInStates implements SignInStates {

    private final DSLContext sql;

    public PostgresSignInStates(ControlPlane controlPlane) {
        this.sql = controlPlane.sql();
    }

    @Override
    public void save(SignInState state, Instant now) {
        sql.deleteFrom(Tables.GitHubSignIn.TABLE)
                .where(Tables.GitHubSignIn.EXPIRES_AT.lt(PostgresFolderStore.offset(now)))
                .execute();
        sql.insertInto(Tables.GitHubSignIn.TABLE)
                .set(Tables.GitHubSignIn.STATE, state.state())
                .set(Tables.GitHubSignIn.PERSON, state.person())
                .set(Tables.GitHubSignIn.STEP, state.step())
                .set(Tables.GitHubSignIn.REDIRECT_URI, state.redirectUri())
                .set(Tables.GitHubSignIn.EXPIRES_AT, PostgresFolderStore.offset(state.expiresAt()))
                .execute();
    }

    @Override
    public Optional<SignInState> take(String state) {
        return sql.deleteFrom(Tables.GitHubSignIn.TABLE)
                .where(Tables.GitHubSignIn.STATE.eq(state))
                .returning(
                        Tables.GitHubSignIn.STATE,
                        Tables.GitHubSignIn.PERSON,
                        Tables.GitHubSignIn.STEP,
                        Tables.GitHubSignIn.REDIRECT_URI,
                        Tables.GitHubSignIn.EXPIRES_AT)
                .fetchOptional()
                .map(row -> new SignInState(
                        row.get(Tables.GitHubSignIn.STATE),
                        row.get(Tables.GitHubSignIn.PERSON),
                        row.get(Tables.GitHubSignIn.STEP),
                        row.get(Tables.GitHubSignIn.REDIRECT_URI),
                        PostgresFolderStore.instant(row.get(Tables.GitHubSignIn.EXPIRES_AT))));
    }
}
