from dagster_aws.s3.compute_log_manager import S3ComputeLogManager

from dataflow_tower.lake import Lake


class LakeComputeLogManager(S3ComputeLogManager):
    def __init__(self, bucket, **kwargs):
        super().__init__(bucket, **kwargs)
        self._s3_session = Lake.from_env().client(kwargs.get("endpoint_url"), kwargs.get("use_ssl", True))
        self._region = self._s3_session.meta.region_name
