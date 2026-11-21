import os
from collections.abc import Mapping
from dataclasses import dataclass
from typing import Self

import boto3
import fsspec
from botocore.config import Config

from dataflow_tower.jasypt import decrypt


@dataclass(frozen=True)
class Lake:
    endpoint: str | None = None
    access_key: str | None = None
    secret_key: str | None = None
    region: str = "us-east-1"
    path_style: bool = True

    @classmethod
    def from_env(cls, env: Mapping[str, str] = os.environ) -> Self:
        password = env.get("JASYPT_ENCRYPTOR_PASSWORD")
        return cls(
            endpoint=env.get("LAKE_ENDPOINT") or None,
            access_key=decrypt(env.get("LAKE_ACCESS_KEY"), password) or None,
            secret_key=decrypt(env.get("LAKE_SECRET_KEY"), password) or None,
            region=env.get("LAKE_S3_REGION", "us-east-1"),
            path_style=env.get("LAKE_S3_PATH_STYLE", "true").lower() == "true",
        )

    def filesystem(self) -> fsspec.AbstractFileSystem:
        endpoint = {"endpoint_url": self.endpoint} if self.endpoint else {}
        return fsspec.filesystem(
            "s3",
            key=self.access_key,
            secret=self.secret_key,
            client_kwargs={**endpoint, "region_name": self.region},
            config_kwargs={"s3": {"addressing_style": self._addressing}},
        )

    def client(self, endpoint: str | None = None, use_ssl: bool = True):
        return boto3.client(
            "s3",
            endpoint_url=endpoint or self.endpoint,
            region_name=self.region,
            aws_access_key_id=self.access_key,
            aws_secret_access_key=self.secret_key,
            use_ssl=use_ssl,
            config=Config(s3={"addressing_style": self._addressing}),
        )

    @property
    def _addressing(self) -> str:
        return "path" if self.path_style else "virtual"
