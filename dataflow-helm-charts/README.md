# DataFlow Helm charts

`charts/dataflow` installs the whole DataFlow platform on Kubernetes with one `helm install`: the Workspace, the tower (Dagster), Spark runs as Kubernetes Jobs, ClickHouse for serving, and, for a self-contained install, Postgres, RustFS and Keycloak. Docker Compose in `dataflow-platform` stays the development stack; this chart is how a team runs DataFlow.

Settings per installation live in `dataflow-environments` and are applied by Argo CD (GitOps). The design is sections 9, 10 and 14 of `dataflow-platform/docs/design/target-architecture.md`; how a run works on Kubernetes is `dataflow-platform/docs/kubernetes.md`.

## What it installs

| Part | What it is | Default |
|:--|:--|:--|
| Workspace | The web app (catalog, SQL, pipelines, runs, Git folders, releases) | on |
| Tower | Dagster 1.13.26, from Dagster's own chart: webserver, daemon, the DataFlow code location; each run is a Kubernetes Job, which starts the Spark driver as a Job of its own | on |
| ClickHouse | Serving: views over the Delta tables, copies, grants | on |
| Postgres | Workspace, Dagster and Keycloak databases | on (`postgres.enabled: false` for a managed database) |
| RustFS | S3-compatible object store for the lake and releases | on (off when the lake is a cloud store) |
| Keycloak | Sign-in for the Workspace, served under `/auth` | off (`keycloak.enabled`) |
| Gateway route | An `HTTPRoute` on a Gateway API gateway: `/` the Workspace, `/tower` the Dagster UI behind sign-in, `/auth` Keycloak | off (`gateway.enabled`) |
| Tower sign-in | oauth2-proxy in front of the Dagster UI at `/tower`, members of `towerSignIn.allowedGroups` only | with the gateway |
| ExternalSecret | Copies the secrets from a vault through External Secrets Operator | off (`externalSecrets.enabled`) |

## Three namespaces

| Namespace | What runs there | Why apart |
|:--|:--|:--|
| the release namespace (`dataflow`) | Workspace, Dagster (webserver, daemon, code location, run workers), sign-in proxy, Keycloak, Postgres | The control plane: small, always on, upgraded together. Postgres holds only its metadata (Workspace, Dagster, Keycloak), so it stays with it |
| `<release namespace>-data` (`dataflow-data`) | ClickHouse, RustFS, the bucket set-up job | Stateful data services with their own volumes and backups. A network policy lets in only the control plane and the runs |
| `<release namespace>-jobs` (`dataflow-jobs`) | Spark drivers and executors of every run | Runs come and go and can be large: their quota and default limits apply here only, so a burst of runs never blocks a control-plane upgrade. Other namespaces cannot reach them |

`namespaces.data` and `namespaces.jobs` rename them; set either to the release namespace to keep things together. The chart creates both namespaces (`namespaces.create`) and keeps them on uninstall, so volumes are never deleted by accident. Runs get only the lake's credentials, not the control plane's secrets.

## Before installing

- Kubernetes 1.29 or later (checked on OrbStack's single-node 1.33), Helm 3.
- The images `dataflow-workspace`, `dataflow-tower-k8s` and `dataflow-engine-k8s` where the cluster can pull them (`./dataflow build workspace tower-k8s engine-k8s` in `dataflow-local`). On OrbStack the images built by `docker build` are already visible to Kubernetes. `dataflow-tower-k8s` is the tower with the Kubernetes client, which the Compose image leaves out.
- A Secret named `dataflow-secrets` in the release namespace, and a Secret named `dataflow-lake-secrets` with only `LAKE_ACCESS_KEY`, `LAKE_SECRET_KEY` and `CLICKHOUSE_PASSWORD` in the data and jobs namespaces (or `externalSecrets.enabled: true`, which creates all three from the vault):

| Key | Used by |
|:--|:--|
| `postgresql-password` | Postgres, Dagster, Workspace, Keycloak |
| `LAKE_ACCESS_KEY`, `LAKE_SECRET_KEY` | Engine, tower, Workspace (releases), ClickHouse, RustFS |
| `CLICKHOUSE_PASSWORD` | ClickHouse, engine, Workspace |
| `JASYPT_ENCRYPTOR_PASSWORD` | Workspace (encrypts stored Git credentials) |
| `keycloak-admin-password` | Keycloak's `admin`, only with `keycloak.enabled` |
| `oauth2-proxy-client-secret`, `oauth2-proxy-cookie-secret` | The Dagster UI sign-in, only with `gateway.enabled` (the cookie secret is 32 random bytes) |

Values in this Secret are plain; Jasypt `ENC(...)` values are only needed on Docker.

## Install

```bash
helm dependency build charts/dataflow
kubectl create namespace dataflow
kubectl create namespace dataflow-data
kubectl create namespace dataflow-jobs
kubectl -n dataflow create secret generic dataflow-secrets --from-env-file=secrets.env
kubectl -n dataflow-data create secret generic dataflow-lake-secrets --from-env-file=lake.env
kubectl -n dataflow-jobs create secret generic dataflow-lake-secrets --from-env-file=lake.env
helm install dataflow charts/dataflow -n dataflow -f values.yaml --set namespaces.create=false --wait
```

A first install with a cloud bucket, the company's login provider and a gateway needs only this:

```yaml
lake:
  root: s3a://acme-lake
  region: eu-west-1
  pathStyle: false
  ssl: true
rustfs:
  enabled: false
auth:
  mode: oidc
  issuer: https://login.acme.com/realms/acme
  clientId: dataflow
gateway:
  enabled: true
  parentName: public
  parentNamespace: gateways
  hostname: dataflow.acme.com
```

Everything else has a default, checked by `values.schema.json` at install time: a lake root that is not `s3a://`, `abfss://` or `gs://`, a memory size like `2gb`, or Keycloak without a host name is refused before anything is created.

## Main settings

| Setting | Default | Meaning |
|:--|:--|:--|
| `lake.root` | `s3a://lake` | Where the lake is; its scheme picks the store's connectors |
| `lake.endpoint` | RustFS inside the release | S3 endpoint; empty for AWS |
| `lake.region`, `lake.pathStyle`, `lake.ssl` | `us-east-1`, `true`, `false` | S3 addressing |
| `releases.bucket`, `releases.target` | `releases`, `prod` | Where the Workspace writes releases, which target the tower runs |
| `auth.mode`, `auth.issuer`, `auth.clientId` | `none` | Sign-in with any OIDC provider; `oidc` is required with a gateway |
| `keycloak.enabled` | `false` | Bundled Keycloak, realm `dataflow` imported, issuer `https://<host>/auth/realms/dataflow` |
| `spark.driverMemory`, `spark.executorMemory`, `spark.memoryOverhead` | `2g`, `2g`, `512m` | Defaults for every run; a spec's `compute:` overrides memory, cores and executors |
| `spark.minExecutors`, `spark.maxExecutors` | `0`, `4` | Dynamic allocation bounds |
| `computePolicy.maxDriverMemory`, `maxDriverCores`, `maxExecutorMemory`, `maxExecutorCores`, `maxExecutors` | empty (no limit) | The most one run may ask for: the Workspace refuses larger sizes, the tower lowers older releases to fit and logs it on the run |
| `spark.driverTemplate`, `spark.executorTemplate` | `{}` | Pod templates: node selectors, tolerations, identity labels |
| `spark.serviceAccountAnnotations` | `{}` | Cloud identity for driver and executors (IRSA role, workload identity) |
| `spark.cpuRequest` | empty (each pod's cores) | What each Spark pod reserves, such as `250m`; it may still use its cores |
| `spark.singlePod` | `false` | Spark runs inside the driver, with no executors, for a small machine |
| `localPorts.enabled` | `false` | Workspace on `localhost:3080`, Dagster on `:3070/tower`, RustFS on `:9100` and `:9101`, ClickHouse on `:8123`, through the cluster's load balancer. Only where nothing else can reach the machine: the Workspace and Dagster may have no sign-in |
| `quota.enabled` | `true` | A ResourceQuota and LimitRange for the jobs namespace (runs only) |
| `namespaces.data`, `namespaces.jobs` | `<release>-data`, `<release>-jobs` | Where data services and runs go; the release namespace keeps them together |
| `namespaces.create` | `true` | Create the data and jobs namespaces (kept on uninstall) |
| `lakeSecretName` | `dataflow-lake-secrets` | The lake-only Secret in the data and jobs namespaces |
| `networkPolicies.enabled` | `true` | Data services reachable only from the control plane and runs; runs and Postgres reachable only from their own namespace |

## Upgrades

`helm upgrade` runs Dagster's database migration as a hook Job; the Workspace migrates its database (Flyway) and re-creates ClickHouse views and roles when it starts. Nothing is done by hand.

## Measured on OrbStack (4 GB VM, one node)

| Step | Time |
|:--|:--|
| `helm install --wait`, empty cluster | 1 min 39 s |
| Release of the examples project from the Workspace | 4 s, tower loaded `earthquakes_job` and `taxi_trips_job` |
| `earthquakes` run: driver, one executor, 9,888 rows through every flow | Dagster run succeeded, 10 materializations and 4 rule checks reported |

A driver and one executor need about 1.8 GiB with the local environment's `512m` heap and `384m` overhead each; on a 4 GB VM that is only free when nothing else runs.

## Not in the chart yet

- Without a gateway the Dagster UI is reachable only with `kubectl port-forward svc/<release>-dagster-webserver 3070:80` (then http://localhost:3070/tower).
- The sign-in proxy renders and lints but has not run behind a real Gateway API gateway yet.
- Grafana and its alert rules (still in `dataflow-monitoring`).
- ClickHouse and the Workspace read the lake with keys; workload identity is wired for Spark only. Azure and Google Cloud Storage lakes are configured but not yet run against a real account.
