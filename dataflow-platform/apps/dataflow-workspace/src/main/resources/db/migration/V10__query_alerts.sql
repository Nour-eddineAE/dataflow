CREATE TABLE query_alert (
    query_id      uuid        PRIMARY KEY REFERENCES saved_query (id) ON DELETE CASCADE,
    owner         text        NOT NULL,
    owner_groups  text        NOT NULL,
    owner_admin   boolean     NOT NULL,
    every_minutes integer     NOT NULL,
    comparison    text        NOT NULL,
    threshold     numeric,
    state         text        NOT NULL,
    checked_at    timestamptz,
    since         timestamptz,
    last_value    text,
    last_error    text
);
