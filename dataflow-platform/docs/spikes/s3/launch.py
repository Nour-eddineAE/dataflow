import sys
import time

from dagster_graphql import DagsterGraphQLClient, DagsterGraphQLClientError

client = DagsterGraphQLClient("localhost", port_number=3000)
run_id = sys.argv[1] if len(sys.argv) > 1 else client.submit_job_execution("hello_job",
                                                                           repository_location_name="spike",
                                                                           repository_name="__repository__")
print("run", run_id, flush=True)
for _ in range(120):
    status = client.get_run_status(run_id)
    if status.value in ("SUCCESS", "FAILURE", "CANCELED"):
        break
    time.sleep(2)
print("status", status.value)
q = """query($runId: ID!) { runOrError(runId: $runId) { ... on Run {
  eventConnection(afterCursor: null) { events { __typename ... on MessageEvent { message }
    ... on MaterializationEvent { assetKey { path } metadataEntries { label ... on IntMetadataEntry { intValue } ... on TextMetadataEntry { text } } }
    ... on ExecutionStepFailureEvent { error { message causes { message } } }
    ... on AssetCheckEvaluationEvent { evaluation { checkName success assetKey { path } } }
  } } } } }"""
res = client._execute(q, {"runId": run_id})
for e in res["runOrError"]["eventConnection"]["events"]:
    if e["__typename"] in ("MaterializationEvent", "AssetCheckEvaluationEvent", "LogMessageEvent", "EngineEvent",
                           "RunFailureEvent", "ExecutionStepFailureEvent"):
        print(e["__typename"], {k: v for k, v in e.items() if k not in ("__typename",)})
