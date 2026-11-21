CREATE TABLE github_app (
    host          text        PRIMARY KEY,
    app_id        bigint      NOT NULL,
    slug          text        NOT NULL,
    client_id     text        NOT NULL,
    client_secret text        NOT NULL,
    private_key   text        NOT NULL,
    created_by    text        NOT NULL,
    created_at    timestamptz NOT NULL
);
