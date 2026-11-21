# Command line

`dataflow` is DataFlow from a terminal or a CI job: check specs before committing them, start runs, read their output,
run a query. It is a small Python package (`cli/`, Python 3.12+) that uses only the standard library, so installing it
pulls nothing else:

```bash
pipx install ./cli
```

```bash
dataflow login
dataflow validate pipelines/
dataflow run earthquakes --wait
dataflow logs 6069acc2
dataflow sql "SELECT count() FROM earthquakes.current"
```

| Command                                | What it does                                                                                                                                                                                                   |
|:---------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `login`                                | Signs in through the browser: it prints a link and a short code, you approve in Keycloak, and it keeps the tokens in `~/.config/dataflow/tokens.json` (readable by you only), refreshing them when they expire |
| `logout`, `whoami`                     | Forget the tokens; show who the Workspace sees and their groups                                                                                                                                                |
| `validate <files or folders>`          | Sends each `pipeline.yml` to the Workspace's checker (the same one the editor uses) and prints every problem with its line; exits 1 if any spec is invalid, so a CI step fails                                 |
| `run <dataset> [--partition] [--wait]` | Starts a run through the tower; with `--wait`, follows it and exits 1 if it fails                                                                                                                              |
| `runs [--dataset]`, `logs <run>`       | Recent runs; the engine's output of one run                                                                                                                                                                    |
| `sql "<query>"`                        | Runs a query exactly as the SQL editor does, with your grants                                                                                                                                                  |

- **Signing in without a browser on the same machine.** `login` uses the OAuth device flow (client `dataflow-cli`): the
  link can be opened on any device, which is how a remote shell or a container signs in.
- **Your rights, not more.** Requests carry your access token (`Authorization: Bearer`), so the Workspace applies your
  grants exactly as in the browser: a run of a dataset without `RUN` answers 403, a query on a table without `SELECT` is
  refused by ClickHouse.
- **Settings**: `DATAFLOW_URL` (Workspace, default `http://localhost:3080`), `DATAFLOW_ISSUER` (Keycloak realm, default
  `http://localhost:8180/realms/dataflow`), `DATAFLOW_CONFIG` (where tokens are kept).
- **Without sign-in** (`WORKSPACE_AUTH=none`) no login is needed.

Inside, `config.py` reads the settings, `auth.py` keeps and refreshes the tokens (device sign-in), `workspace.py` is the
Workspace's API, `commands.py` holds one function per command, and `main.py` parses the arguments. Tests: `pytest` in
`cli/` runs it against a fake Workspace (validation output and exit code, `run --wait`, a refused run, `sql`).
