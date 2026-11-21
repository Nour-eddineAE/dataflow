ALTER TABLE git_credential
    ADD COLUMN provider       text,
    ADD COLUMN refresh_secret text,
    ADD COLUMN expires_at     timestamptz;
