CREATE TABLE access_grant (
    securable  text        NOT NULL,
    principal  text        NOT NULL,
    privilege  text        NOT NULL,
    granted_by text        NOT NULL,
    granted_at timestamptz NOT NULL,
    PRIMARY KEY (securable, principal, privilege)
);

CREATE TABLE access_audit (
    id        bigserial   PRIMARY KEY,
    at        timestamptz NOT NULL,
    actor     text        NOT NULL,
    action    text        NOT NULL,
    securable text        NOT NULL,
    principal text        NOT NULL,
    privilege text        NOT NULL
);

CREATE INDEX access_audit_at ON access_audit (at DESC);
