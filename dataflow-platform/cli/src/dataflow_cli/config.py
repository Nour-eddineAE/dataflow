import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Self

CLIENT_ID = "dataflow-cli"


@dataclass(frozen=True)
class Config:
    workspace: str
    issuer: str
    home: Path

    @classmethod
    def from_env(cls, env: Mapping[str, str] = os.environ) -> Self:
        return cls(
            workspace=env.get("DATAFLOW_URL", "http://localhost:3080").rstrip("/"),
            issuer=env.get("DATAFLOW_ISSUER", "http://localhost:8180/realms/dataflow").rstrip("/"),
            home=Path(env.get("DATAFLOW_CONFIG", Path.home() / ".config" / "dataflow")),
        )

    @property
    def token_endpoint(self) -> str:
        return f"{self.issuer}/protocol/openid-connect/token"

    @property
    def device_endpoint(self) -> str:
        return f"{self.issuer}/protocol/openid-connect/auth/device"
