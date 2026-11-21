package com.dataflow.workspace.controlplane;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class Tables {

    private Tables() {}

    private static Field<String> owner() {
        return DSL.field(DSL.name("owner"), SQLDataType.CLOB);
    }

    private static Field<OffsetDateTime> createdAt() {
        return DSL.field(DSL.name("created_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
    }

    public static final class GitCredential {
        public static final Table<?> TABLE = DSL.table(DSL.name("git_credential"));
        public static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
        public static final Field<String> OWNER = owner();
        public static final Field<String> NAME = DSL.field(DSL.name("name"), SQLDataType.CLOB);
        public static final Field<String> USERNAME = DSL.field(DSL.name("username"), SQLDataType.CLOB);
        public static final Field<String> SECRET = DSL.field(DSL.name("secret"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> CREATED_AT = createdAt();
        public static final Field<String> PROVIDER = DSL.field(DSL.name("provider"), SQLDataType.CLOB);
        public static final Field<String> REFRESH_SECRET = DSL.field(DSL.name("refresh_secret"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> EXPIRES_AT =
                DSL.field(DSL.name("expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

        private GitCredential() {}
    }

    public static final class SavedQuery {
        public static final Table<?> TABLE = DSL.table(DSL.name("saved_query"));
        public static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
        public static final Field<String> OWNER = owner();
        public static final Field<String> NAME = DSL.field(DSL.name("name"), SQLDataType.CLOB);
        public static final Field<String> SQL = DSL.field(DSL.name("sql"), SQLDataType.CLOB);
        public static final Field<Boolean> SHARED = DSL.field(DSL.name("shared"), SQLDataType.BOOLEAN);
        public static final Field<OffsetDateTime> UPDATED_AT =
                DSL.field(DSL.name("updated_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

        private SavedQuery() {}
    }

    public static final class AccessGrant {
        public static final Table<?> TABLE = DSL.table(DSL.name("access_grant"));
        public static final Field<String> SECURABLE = DSL.field(DSL.name("securable"), SQLDataType.CLOB);
        public static final Field<String> PRINCIPAL = DSL.field(DSL.name("principal"), SQLDataType.CLOB);
        public static final Field<String> PRIVILEGE = DSL.field(DSL.name("privilege"), SQLDataType.CLOB);
        public static final Field<String> GRANTED_BY = DSL.field(DSL.name("granted_by"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> GRANTED_AT =
                DSL.field(DSL.name("granted_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

        private AccessGrant() {}
    }

    public static final class AccessAudit {
        public static final Table<?> TABLE = DSL.table(DSL.name("access_audit"));
        public static final Field<OffsetDateTime> AT = DSL.field(DSL.name("at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
        public static final Field<String> ACTOR = DSL.field(DSL.name("actor"), SQLDataType.CLOB);
        public static final Field<String> ACTION = DSL.field(DSL.name("action"), SQLDataType.CLOB);
        public static final Field<String> SECURABLE = DSL.field(DSL.name("securable"), SQLDataType.CLOB);
        public static final Field<String> PRINCIPAL = DSL.field(DSL.name("principal"), SQLDataType.CLOB);
        public static final Field<String> PRIVILEGE = DSL.field(DSL.name("privilege"), SQLDataType.CLOB);
        public static final Field<Long> ID = DSL.field(DSL.name("id"), SQLDataType.BIGINT);

        private AccessAudit() {}
    }

    public static final class GitHubSignIn {
        public static final Table<?> TABLE = DSL.table(DSL.name("github_sign_in"));
        public static final Field<String> STATE = DSL.field(DSL.name("state"), SQLDataType.CLOB);
        public static final Field<String> PERSON = DSL.field(DSL.name("person"), SQLDataType.CLOB);
        public static final Field<String> STEP = DSL.field(DSL.name("step"), SQLDataType.CLOB);
        public static final Field<String> REDIRECT_URI = DSL.field(DSL.name("redirect_uri"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> EXPIRES_AT =
                DSL.field(DSL.name("expires_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

        private GitHubSignIn() {}
    }

    public static final class GitHubApp {
        public static final Table<?> TABLE = DSL.table(DSL.name("github_app"));
        public static final Field<String> HOST = DSL.field(DSL.name("host"), SQLDataType.CLOB);
        public static final Field<Long> APP_ID = DSL.field(DSL.name("app_id"), SQLDataType.BIGINT);
        public static final Field<String> SLUG = DSL.field(DSL.name("slug"), SQLDataType.CLOB);
        public static final Field<String> CLIENT_ID = DSL.field(DSL.name("client_id"), SQLDataType.CLOB);
        public static final Field<String> CLIENT_SECRET = DSL.field(DSL.name("client_secret"), SQLDataType.CLOB);
        public static final Field<String> PRIVATE_KEY = DSL.field(DSL.name("private_key"), SQLDataType.CLOB);
        public static final Field<String> CREATED_BY = DSL.field(DSL.name("created_by"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> CREATED_AT = createdAt();

        private GitHubApp() {}
    }

    public static final class GitFolder {
        public static final Table<?> TABLE = DSL.table(DSL.name("git_folder"));
        public static final Field<UUID> ID = DSL.field(DSL.name("id"), SQLDataType.UUID);
        public static final Field<String> PATH = DSL.field(DSL.name("path"), SQLDataType.CLOB);
        public static final Field<String> REMOTE_URL = DSL.field(DSL.name("remote_url"), SQLDataType.CLOB);
        public static final Field<String> BRANCH = DSL.field(DSL.name("branch"), SQLDataType.CLOB);
        public static final Field<UUID> CREDENTIAL_ID = DSL.field(DSL.name("credential_id"), SQLDataType.UUID);
        public static final Field<String> OWNER = owner();
        public static final Field<OffsetDateTime> CREATED_AT = createdAt();

        private GitFolder() {}
    }

    public static final class Draft {
        public static final Table<?> TABLE = DSL.table(DSL.name("draft"));
        public static final Field<UUID> FOLDER_ID = DSL.field(DSL.name("folder_id"), SQLDataType.UUID);
        public static final Field<String> BRANCH = DSL.field(DSL.name("branch"), SQLDataType.CLOB);
        public static final Field<String> FILE_PATH = DSL.field(DSL.name("file_path"), SQLDataType.CLOB);
        public static final Field<String> CONTENT = DSL.field(DSL.name("content"), SQLDataType.CLOB);
        public static final Field<String> BASE_COMMIT = DSL.field(DSL.name("base_commit"), SQLDataType.CLOB);
        public static final Field<String> SAVED_BY = DSL.field(DSL.name("saved_by"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> SAVED_AT =
                DSL.field(DSL.name("saved_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

        private Draft() {}
    }

    public static final class Release {
        public static final Table<?> TABLE = DSL.table(DSL.name("release"));
        public static final Field<String> PROJECT = DSL.field(DSL.name("project"), SQLDataType.CLOB);
        public static final Field<String> TARGET = DSL.field(DSL.name("target"), SQLDataType.CLOB);
        public static final Field<String> COMMIT = DSL.field(DSL.name("commit"), SQLDataType.CLOB);
        public static final Field<UUID> FOLDER_ID = DSL.field(DSL.name("folder_id"), SQLDataType.UUID);
        public static final Field<String> RELEASED_BY = DSL.field(DSL.name("released_by"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> RELEASED_AT =
                DSL.field(DSL.name("released_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);

        private Release() {}
    }

    public static final class Favorite {
        public static final Table<?> TABLE = DSL.table(DSL.name("favorite"));
        public static final Field<String> OWNER = owner();
        public static final Field<String> KIND = DSL.field(DSL.name("kind"), SQLDataType.CLOB);
        public static final Field<String> REF = DSL.field(DSL.name("ref"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> CREATED_AT = createdAt();

        private Favorite() {}
    }

    public static final class QueryAlert {
        public static final Table<?> TABLE = DSL.table(DSL.name("query_alert"));
        public static final Field<UUID> QUERY_ID = DSL.field(DSL.name("query_id"), SQLDataType.UUID);
        public static final Field<String> OWNER = owner();
        public static final Field<String> OWNER_GROUPS = DSL.field(DSL.name("owner_groups"), SQLDataType.CLOB);
        public static final Field<Boolean> OWNER_ADMIN = DSL.field(DSL.name("owner_admin"), SQLDataType.BOOLEAN);
        public static final Field<Integer> EVERY_MINUTES = DSL.field(DSL.name("every_minutes"), SQLDataType.INTEGER);
        public static final Field<String> COMPARISON = DSL.field(DSL.name("comparison"), SQLDataType.CLOB);
        public static final Field<BigDecimal> THRESHOLD = DSL.field(DSL.name("threshold"), SQLDataType.NUMERIC);
        public static final Field<String> STATE = DSL.field(DSL.name("state"), SQLDataType.CLOB);
        public static final Field<OffsetDateTime> CHECKED_AT =
                DSL.field(DSL.name("checked_at"), SQLDataType.TIMESTAMPWITHTIMEZONE);
        public static final Field<OffsetDateTime> SINCE =
                DSL.field(DSL.name("since"), SQLDataType.TIMESTAMPWITHTIMEZONE);
        public static final Field<String> VALUE = DSL.field(DSL.name("last_value"), SQLDataType.CLOB);
        public static final Field<String> ERROR = DSL.field(DSL.name("last_error"), SQLDataType.CLOB);

        private QueryAlert() {}
    }
}
