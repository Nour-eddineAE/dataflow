CREATE TABLE git_credential (
    id         uuid        PRIMARY KEY,
    owner      text        NOT NULL,
    name       text        NOT NULL,
    username   text        NOT NULL,
    secret     text        NOT NULL,
    created_at timestamptz NOT NULL,
    UNIQUE (owner, name)
);

CREATE TABLE git_folder (
    id            uuid        PRIMARY KEY,
    path          text        NOT NULL UNIQUE,
    remote_url    text        NOT NULL,
    branch        text        NOT NULL,
    credential_id uuid        REFERENCES git_credential (id) ON DELETE SET NULL,
    owner         text        NOT NULL,
    created_at    timestamptz NOT NULL
);

CREATE TABLE draft (
    folder_id   uuid        NOT NULL REFERENCES git_folder (id) ON DELETE CASCADE,
    branch      text        NOT NULL,
    file_path   text        NOT NULL,
    content     text,
    base_commit text        NOT NULL,
    saved_by    text        NOT NULL,
    saved_at    timestamptz NOT NULL,
    PRIMARY KEY (folder_id, branch, file_path)
);

CREATE TABLE release (
    project     text        NOT NULL,
    target      text        NOT NULL,
    commit      text        NOT NULL,
    folder_id   uuid        REFERENCES git_folder (id) ON DELETE SET NULL,
    released_by text        NOT NULL,
    released_at timestamptz NOT NULL,
    PRIMARY KEY (project, target, released_at)
);
