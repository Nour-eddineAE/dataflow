from dataflow_tower.compute_logs import LakeComputeLogManager
from dataflow_tower.lake import Lake

LAKE = {
    "LAKE_ENDPOINT": "http://rustfs:9000",
    "LAKE_ACCESS_KEY": "dataflow",
    "LAKE_SECRET_KEY": "not-a-real-key",
    "LAKE_S3_REGION": "eu-west-1",
    "LAKE_S3_PATH_STYLE": "true",
}


def test_the_client_reaches_a_path_style_store_with_the_lake_keys():
    client = Lake.from_env(LAKE).client()

    assert client.meta.endpoint_url == "http://rustfs:9000"
    assert client.meta.region_name == "eu-west-1"
    assert client.meta.config.s3["addressing_style"] == "path"
    assert client._request_signer._credentials.access_key == "dataflow"


def test_a_cloud_store_keeps_virtual_hosts_and_the_default_credentials():
    client = Lake.from_env({"LAKE_S3_PATH_STYLE": "false", "LAKE_S3_REGION": "us-east-2"}).client()

    assert client.meta.config.s3["addressing_style"] == "virtual"
    assert client.meta.endpoint_url == "https://s3.us-east-2.amazonaws.com"


def test_the_compute_log_manager_uses_the_lake_client(monkeypatch, tmp_path):
    for name, value in LAKE.items():
        monkeypatch.setenv(name, value)

    manager = LakeComputeLogManager(bucket="releases", local_dir=str(tmp_path), prefix="system/compute-logs",
                                    endpoint_url="http://rustfs:9000", upload_interval=15)

    assert manager._s3_session.meta.config.s3["addressing_style"] == "path"
    assert manager._region == "eu-west-1"
    assert set(LakeComputeLogManager.config_type()) >= {"bucket", "endpoint_url", "prefix", "upload_interval"}
