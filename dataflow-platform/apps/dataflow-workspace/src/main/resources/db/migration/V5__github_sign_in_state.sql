CREATE TABLE github_sign_in (
    state        text        PRIMARY KEY,
    person       text        NOT NULL,
    step         text        NOT NULL,
    redirect_uri text        NOT NULL,
    expires_at   timestamptz NOT NULL
);
