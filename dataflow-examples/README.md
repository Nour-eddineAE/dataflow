# DataFlow examples

A DataFlow project: the pipelines and transformations of two public datasets, and a pipeline derived from one of them.

| Dataset | Source | What it shows |
|:--|:--|:--|
| `earthquakes` | USGS feed of the last 30 days, every 6 hours | Revised records kept as history (SCD type 2), quality rules, gold summaries |
| `taxi_trips` | NYC yellow taxi trips, one Parquet file per month | Monthly partitions, a lookup table (`taxi_zones`), a published copy |
| `zone_hours` | No source: reads `taxi_trips` | A derived pipeline: inputs from another pipeline, a table built step by step (join, add columns, filter, group), an unstored intermediate table, SQL on top; runs when `taxi_trips` lands |

## Layout

```
dataflow.yml                     the project: its name and its targets
pipelines/<dataset>/pipeline.yml one pipeline spec per dataset
```

`dataflow.yml` names the project and its targets: `dev` runs from a person's branch and writes to an isolated area, `prod` runs what is on `main`.

## Using it

Connect this repository in the DataFlow Workspace (Git folders), edit there or here, commit and push. Releasing `main` to `prod` makes the platform run exactly that commit. The spec format is described in `dataflow-platform/docs/pipeline-specs.md`.
