CREATE TABLE saved_query (
    id         uuid        PRIMARY KEY,
    owner      text        NOT NULL,
    name       text        NOT NULL,
    sql        text        NOT NULL,
    shared     boolean     NOT NULL,
    updated_at timestamptz NOT NULL
);

CREATE INDEX saved_query_owner ON saved_query (owner);
