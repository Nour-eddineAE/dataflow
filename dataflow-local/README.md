# DataFlow on one machine

Runs the whole DataFlow platform with Docker Compose: the Workspace, the tower, ClickHouse, the object store, sign-in
and dashboards. The images come from the sibling checkout `../dataflow-platform`; Kubernetes is `dataflow-helm-charts`.

Needs Docker (a 4 GB VM is enough), Java 21 and a built `dataflow-platform` (for `./dataflow init`, which encrypts two
secrets with the platform's Jasypt).

```bash
./dataflow init
```

```bash
./dataflow build
```

```bash
./dataflow up
```

Then open http://localhost:3080 and sign in as `local` with `PERSON_INITIAL_PASSWORD` from `.env`; the first sign-in
asks for a new password.

Set `PERSON_NAME` and `PERSON_EMAIL` in your private `.env` to identify the install's owner. Keep `PERSON_ID`
unchanged after creating Workspace folders: it is their owner key. `keycloak-init` reapplies the configured email
and verifies it for this bootstrap administrator on every start; `PERSON_NAME` is used only when the account is
created, so later name changes are made in the account page. Other accounts verify their email through the sign-in
flow.
The sign-in theme uses DataFlow's mark for its logo and browser favicon.

Object-store reads follow Workspace dataset grants. Administrators keep full access; a dataset `SELECT` or `MANAGE`
grant permits read-only browsing of that dataset's paths. Open **Platform apps → RustFS** to find your permitted
paths. RustFS uses actual group membership without merging client roles into groups. Renew or revoke older
storage sessions when applying this identity-mapping change. `STORAGE_ACCESS_ENABLED` enables policy publication;
`STORAGE_LEGACY_POLICIES` also scopes the old local
console policy names so existing credentials cannot retain their previous broad access. This install's original
documents are backed up privately in `.storage-policy-backup/`. Behavior, prerequisites, failure handling and
deliberate rollback: [storage access](../dataflow-platform/docs/storage-access.md).

## Layout

| Path                    | What                                                                                          |
|:------------------------|:----------------------------------------------------------------------------------------------|
| `dataflow`              | The one entry point: `init`, `up`, `down`, `restart`, `status`, `logs`, `build`, `run`, `engine`, `encrypt`, `prune` |
| `compose.yaml`          | Includes the four parts below as one Compose project, `dataflow`                              |
| `settings.env`          | Everything that is not a secret: ports, image versions, memory limits, names                  |
| `.env`                  | Secrets, written by `./dataflow init`; never committed. A value here wins over `settings.env` |
| `storage/`              | RustFS (buckets `lake`, `releases`), Postgres (`dagster`, `workspace`, `keycloak`), ClickHouse |
| `sign-in/`              | Keycloak (realm and clients), the sign-in proxies of the tower and Mailpit                    |
| `platform/`             | The tower (Dagster), the Workspace, the engine image                                          |
| `monitoring/`           | Grafana (boards and alert rules), Mailpit, the optional Spark History Server                  |
| `data/`                 | Downloaded source files the engine caches; never committed                                    |

| URL                        | What                                       |
|:---------------------------|:-------------------------------------------|
| http://localhost:3080      | Workspace                                  |
| http://localhost:3070      | Tower (Dagster), administrators only       |
| http://localhost:3000      | Grafana                                    |
| http://localhost:8025      | Mailpit, administrators only               |
| http://localhost:8180      | Keycloak                                   |
| http://localhost:9101      | Object store console                       |
| http://localhost:8123/play | ClickHouse                                 |

How it all works and what to do when something breaks: `../dataflow-platform/docs/operations.md`.
