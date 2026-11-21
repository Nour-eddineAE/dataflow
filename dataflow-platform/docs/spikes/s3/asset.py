from dagster_graphql import DagsterGraphQLClient

c = DagsterGraphQLClient("localhost", port_number=3000)
q = """{ assetOrError(assetKey: {path: ["java_hello"]}) { ... on Asset { assetMaterializations(limit: 1) { runId timestamp }
  definition { assetChecksOrError { ... on AssetChecks { checks { name executionForLatestMaterialization { status } } } } } } } }"""
print(c._execute(q))
