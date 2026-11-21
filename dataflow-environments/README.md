# DataFlow environments

The settings of each DataFlow installation, kept in Git and applied by Argo CD (GitOps). One folder per installation and environment:

```
envs/
└── <environment>/
    ├── application.yaml   the Argo CD Application: which chart version, which values
    ├── values.yaml        the settings of this environment (no secrets)
    └── secrets.yaml       External Secrets references: names in the vault, never values
```

Changing an installation means a pull request on its folder. Argo CD notices the merge, renders the chart with these values and makes the cluster match; it also shows any difference made by hand.

Secrets never go in this repository. `secrets.yaml` only names them; the External Secrets Operator copies them from the vault (AWS Secrets Manager, Azure Key Vault, Google Secret Manager, HashiCorp Vault) into the cluster.

The chart itself lives in `dataflow-helm-charts` (`charts/dataflow`); its README lists every setting and the keys of the `dataflow-secrets` Secret (control plane) and the `dataflow-lake-secrets` Secret (lake credentials only, in the `dataflow-data` and `dataflow-jobs` namespaces the chart creates).

## Environments

| Folder | Cluster | Notes |
|:--|:--|:--|
| `envs/local` | OrbStack's Kubernetes on a laptop | Local images, bundled Postgres, RustFS and ClickHouse, sign-in off, small Spark pods (`512m` heap, `384m` overhead, at most 2 executors). The Secret is created by hand, no vault |

## Starting Argo CD on a cluster

Argo CD is installed once per cluster with its own chart, then given the Application of an environment:

```bash
helm install argocd argo-cd --repo https://argoproj.github.io/argo-helm -n argocd --create-namespace
argocd repo add https://dagster-io.github.io/helm --type helm --name dagster
kubectl apply -f envs/local/application.yaml
```

The `repo add` line lets Argo CD fetch the chart's dependency (Dagster), which is not stored in Git.

From then on Argo CD renders the chart with `values.yaml` and `secrets.yaml` of that folder, applies it to the `dataflow` namespace (data services go to `dataflow-data`, Spark runs to `dataflow-jobs`), and corrects any change made by hand (`selfHeal`). An environment follows the chart's `master`; a production environment pins a chart tag instead.

The Application reads this repository at `Nour-eddineAE/dataflow-environments` (renamed from `dataflow-config-repo` on 2026-10-30; GitHub still redirects the old address).
