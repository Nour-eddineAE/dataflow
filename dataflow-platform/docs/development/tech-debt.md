# Technical debt register

What the project carries that it does not need, or carries twice. Each item says what it costs to keep, what it costs
to fix, and a proposal: **remove**, **fold** (merge into what already exists), **replace** (with a platform or library
feature) or **keep** (and say why). The owner picks; fixes then land in small commits per module, each with its tests
and a before and after count.

Inventory of 2026-11-06, read from the code at platform `f9d2a1f`. How each part was searched is at the end, so the
next pass can repeat it.

## Summary

| # | Item | Area | Keep costs | Fix costs | Proposal |
|:--|:--|:--|:--|:--|:--|
| 1 | Run history endpoint nobody calls | Workspace API | 1 endpoint | S | done |
| 2 | Unused UI exports | UI | 5 symbols, 1 file | S | done |
| 3 | Two ways to add a pipeline input | UI | 2 functions, one unused | S | done |
| 4 | API calls outside `api/hooks` | UI | 5 call sites | S | done |
| 5 | Two chart libraries | UI | ECharts plus Mantine Charts (Recharts) | M | done |
| 6 | Kubernetes images built on every CI run | CI, images | 2 image builds per run; about 1 GB of local images | S | done |
| 7 | Kubernetes client inside the Compose engine image | Engine | `spark-kubernetes` and its client in every run's classpath | S | replace |
| 8 | Retired rules still in the rule book | Docs | Readers check rules that cannot apply | S | done |
| 9 | Module table in Internals out of date | Docs | 10 of 26 Workspace packages listed, one that no longer exists | S | done |
| 10 | Hand-made caches | Workspace | 6 classes with their own freshness and locking | M | keep, one exception |
| 11 | Superseded sections in the work queue | Queue | Phases 0 to 3 and "In flight" hold closed or replaced work | S | fold |
| 12 | `dependency:analyze` warnings | Build | 66 warnings hide a new real one | M | done |
| 13 | Uploads never cleaned | Lake | Files in `uploads/<person>/` stay forever | S | done |

## Items

### 1. Run history endpoint nobody calls

`GET /api/datasets/{dataset}/history` (`DatasetController.runHistory`) and `RunService.eventRuns` serve only the UI hook
`useEventRuns` in `api/hooks/catalog.ts`, and no page uses that hook. The Runs tab reads the tower instead, and the
overview reads `EventLog.runs` directly. `EventRun` stays: the overview and the run comparison use it.

Proposal: remove the endpoint, the method and the hook.

Checked again 2026-11-07: `RunService.eventRuns` now has callers (`PipelineOverviews` and `FreshnessMirror` read
FAILURE and CANCELED from the tower through it), and the hook went with item 2. Only the endpoint is left to remove.
Done the same day: the endpoint and its row in `workspace.md` are gone; no test or caller used it.

### 2. Unused UI exports

Exported and used nowhere, not even in their own file:

| Symbol | File | Replaced by |
|:--|:--|:--|
| `useFolderTree` | `api/hooks/folders.ts` | `useFolderTrees`, same query key |
| `useTemplate` | `api/hooks/specs.ts` | `CreateMenu` fetches `/specs/template` itself (item 4) |
| `useEventRuns` | `api/hooks/catalog.ts` | nothing (item 1) |
| `LayerBadge` | `components/LayerBadge.tsx` | nothing |

About 70 more exports are types or constants used only inside their own file (`ActionProps`, `SHUFFLE`, `Viewport`).
They cost nothing at run time; drop the `export` when a file is next touched, not in a sweep.

Proposal: remove the four symbols and the file. Keep `useTemplate` instead if item 4 moves `CreateMenu` onto it.

Done 2026-11-07 (9561255): `useFolderTree`, `useEventRuns` and `LayerBadge` with its file are gone, 16 lines; `useTemplate`
stayed for item 4.

### 3. Two ways to add a pipeline input

`lib/specModel.ts` has `addInputTable` (used by the designer and the canvas, tested) and `addInput` (used nowhere), which
names the alias differently: `addInput` drops `current`, `gold` and `reference` from the name, `addInputTable` keeps the
dataset and the last part. Two rules for one name is the trap.

Proposal: remove `addInput`.

Done 2026-11-07 (9690ff8), 7 lines.

### 4. API calls outside `api/hooks`

The UI standards put data access in `api/hooks`. These call `api` directly:

| File | Call |
|:--|:--|
| `pages/SqlPage.tsx` | `GET /sql/saved/{id}` |
| `pages/ServicesPage.tsx` | `GET /services/serving-health` |
| `pages/StoragePage.tsx` | `GET /storage`, `POST /storage/sync` |
| `pages/workspace/CreateMenu.tsx` | `GET /specs/template` |

Each one repeats a query key and its polling choice that a hook would hold once. `SqlPage` and `StoragePage` have
uncommitted work by another session today; do them after it lands.

Proposal: fold each into a hook beside its siblings.

Done 2026-11-07 (863f952): `useStorage`, `useSyncStorage` and `useServingHealth` in `api/hooks/platform.ts`, and
`useTemplate` returns a loader that `CreateMenu` calls on submit, so typing a name does not fetch a template per key.
Five call sites in pages became one: `pages/sql/queryLink.ts` is a router loader, which runs outside React. The Monaco
providers in `lib/monaco.ts` also call the client; they are not React code either.

### 5. Two chart libraries

Boards (`BoardPage` through `components/EChart.tsx`) draw with ECharts 6; the SQL result chart (`pages/sql/ResultChart.tsx`)
draws with Mantine Charts, which wraps Recharts. Two libraries means two looks for one kind of chart, two sets of colour
and dark-mode wiring, and two bundles in the download.

Keep costs: both libraries in the bundle; the result chart and a board chart of the same query look different. Fix
costs: rewrite one side, about one page of code, plus a check in light and dark. ECharts covers what boards need
(large series, zoom); Mantine Charts matches the Mantine theme with no wiring.

Proposal (decide): draw the SQL result chart with `EChart` and drop `@mantine/charts` and `recharts`, so a query looks
the same in the editor and on a board.

Done 2026-11-07 as the UI review's "one chart engine" row (31ddd3b, 550d1ff): the result chart uses `EChart` and
`resultOption` in `lib/boardOptions.ts`; both packages are gone from `package.json`. Reverting the two commits restores
the old chart.

### 6. Kubernetes images built on every CI run

`.github/workflows/ci.yml` builds `engine-k8s` and `tower-k8s` next to the Compose images on every master and `ci/*`
push, though Kubernetes is parked. Locally the two images take 999 MB beside 873 MB for their Compose twins.

Proposal: build the `-k8s` images only on a manual dispatch or a `k8s/*` branch until Kubernetes is back. Keep the
Dockerfile targets and `kubernetes_engine.py`, `driver_job.py` and their 622 test lines: they are tested and the chart
uses them.

Done 2026-11-07: the two matrix entries are marked `parked` and their build step runs only on `workflow_dispatch` or a
`k8s/*` branch (which the images job now also accepts); 5 image builds per push become 3. Not yet seen on GitHub: the
change is unpushed.

### 7. Kubernetes client inside the Compose engine image

`apps/dataflow-engine/pom.xml` puts `spark-kubernetes` on the runtime classpath for every image, so each Compose run
loads a Kubernetes client it never calls.

Proposal: move the dependency to a Maven profile the `engine-k8s` target turns on. Measure the image and the jar before
and after.

### 8. Retired rules still in the rule book

`engineering-rules.md` keeps struck-through Kafka and actuator rules (`RULE-S05`, `RULE-S06`), and `RULE-T07` asks for
an `AuditReader` API from the notifications module deleted in v3. A reviewer checks every RULE-*, so dead ones cost
attention on every review.

Proposal: remove retired rules from the table and keep one line naming the retired ranges, as the page already does for
K, N and B.

Done 2026-11-07: ten struck-through or dead rows left the tables (S06, S10, SP06, SP07, T07, C01, C04, D01, D03, D04)
for the Retired list, each with what replaced it. RULE-S05 stays, reworded to what holds today: the Workspace exposes
actuator `health`, the engine has no probes.

### 9. Module table in Internals out of date

`internals.md` lists ten Workspace packages, among them `git`, which is now `folders`; the code has 26 (`access`,
`alerts`, `compute`, `controlplane`, `dashboards`, `favorites`, `housekeeping`, `preflight`, `queries`, `releases`,
`templates`, `uploads` and more are missing).

Proposal: one row per package with its one-line job, generated from the package list so it cannot drift silently.

Done 2026-11-07: `internals.md` has a Workspace packages table, 26 rows, and `scripts/package-table.py` (run in CI's
literals job) fails naming the package when the code and the table disagree; it was seen failing with a row removed.
Checked rather than generated: each job is a sentence a person writes.

### 10. Hand-made caches

`LakeIndex` (15 s freshness, `volatile` snapshot), `DeployedSpecs` and `Access` (`AtomicReference` snapshot refreshed on
change), `CatalogViews` (`synchronized` sync), `JavaApi` (lazy `volatile` list) and `GitCache` each hold their own copy
and their own locking.

Most are snapshots replaced on an event, which a cache library does not model better. `LakeIndex` is a time-based cache
with its window as a literal.

Proposal: keep the snapshot holders. Move `LakeIndex`'s freshness window to `dataflow.workspace` settings. If a second
time-based cache appears (one is being written in `dashboards` today), replace both with Caffeine through Spring's cache
abstraction instead of a third hand-made one.

Checked 2026-11-07: the cache written in `dashboards` is `LruCache`, bounded by size (400 entries), not by time, so the
Caffeine trigger has not fired. `LakeIndex`'s window stays `FRESH_FOR`, a named constant beside its owner: no
installation has asked for another window, and a setting nobody changes is one more fact to keep. Revisit when one does.

### 11. Superseded sections in the work queue

"In flight", "v3 follow-ups" and Phases 0 to 3 hold work that is done, retired by v3 or replaced by G1 to G13; Phase 3
says so in its heading. Whoever picks the next task reads the whole queue.

Proposal: move the open items that still matter into the product vision sections and the closed sections to a
`docs/history.md` log, so the queue holds only open work.

### 12. `dependency:analyze` warnings

66 warnings (RULE-M04), mostly starters declared for their transitive libraries. Nobody reads 66 warnings, so a real
unused or undeclared library gets through.

Proposal: declare what the code imports, mark deliberate starters `usedDependency`, as the engine POM already does,
then fail the build on new warnings.

Checked 2026-11-07: `mvn test-compile dependency:analyze` prints no warning on any module, the parent POM sets
`failOnWarning`, and CI runs `dependency:analyze-only` after `verify`, so a new warning already fails the build.

### 13. Uploads never cleaned

File upload writes `uploads/<person>/<id>/` in the lake (up to 200 MB each) and Maintenance offers no cleanup for it.

Proposal: add uploads older than a retention setting to the Maintenance dry run, with the other lake cleanups (G7).

Done 2026-11-09: Maintenance offers uploads older than `HOUSEKEEPING_UPLOAD_DAYS` (30) that no released spec reads, and
the engine's `clean` task removes them ([Housekeeping](../housekeeping.md)).

## Checked and kept

- Java classes with no reference: none. `TransformationHarness` is the documented test API for transformations;
  `EncryptedPropertiesEnvironmentPostProcessor` is registered in `spring.factories`.
- Python: `defs` and `LakeComputeLogManager` look unused but Dagster loads them from `workspace.yaml` and
  `dagster.yaml`.
- Every one of the 109 Workspace endpoints but item 1 has a caller in the UI, the CLI or the tower.
- The Dagster Pipes writer in the engine is custom on purpose: `io.dagster:pipes` writes every message twice.

## Not yet inventoried

Settings declared but never read (102 in `settings.md`), unused `.env.example` knobs in `dataflow-local`, and
lines-per-module trends. They need a read of every properties record and the compose files.

## How this was searched

- UI exports: every `export function|const|class` under `ui/src` whose name appears in no other non-test file and only
  once in its own.
- Endpoints: every `@*Mapping` path whose last literal segment appears in no UI, CLI or tower source.
- Java classes: every class without a Spring stereotype whose name appears in no other main source or resource.
- Docs: every backticked class or file name in `docs/` that matches no tracked file or source text.
