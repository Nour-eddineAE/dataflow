CREATE TABLE favorite (
    owner      text        NOT NULL,
    kind       text        NOT NULL,
    ref        text        NOT NULL,
    created_at timestamptz NOT NULL,
    PRIMARY KEY (owner, kind, ref)
);
