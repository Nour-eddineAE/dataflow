import dagster as dg
import os
from dagster_docker import PipesDockerClient


@dg.asset(check_specs=[dg.AssetCheckSpec("rows_positive", asset="java_hello")])
def java_hello(context: dg.AssetExecutionContext, pipes_docker_client: PipesDockerClient):
    return pipes_docker_client.run(
        context=context,
        image="eclipse-temurin:21-jre",
        command=["java", "-cp", "/app/classes", "Main"],
        container_kwargs={"volumes": {os.environ["HOST_JAVA_DIR"]: {"bind": "/app", "mode": "ro"}}},
    ).get_results()


hello_job = dg.define_asset_job("hello_job", selection=[java_hello], tags={"dataset": "demo"})

defs = dg.Definitions(
    assets=[java_hello],
    jobs=[hello_job],
    resources={"pipes_docker_client": PipesDockerClient()},
)
