import json
import time
from collections.abc import Callable
from typing import Any

from dataflow_cli.config import CLIENT_ID, Config
from dataflow_cli.http import Failure, post_form

DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
EXPIRY_MARGIN = 30
ACCESS_TOKEN = "access_token"
REFRESH_TOKEN = "refresh_token"
DEVICE_CODE = "device_code"
PRIVATE_FILE = 0o600
POLL_SECONDS = 5
SLOW_DOWN_SECONDS = 5
CODE_LIFETIME_SECONDS = 600


class Tokens:
    def __init__(self, config: Config):
        self._config = config
        self._file = config.home / "tokens.json"

    def access(self) -> str | None:
        if not self._file.exists():
            return None
        saved = json.loads(self._file.read_text())
        if time.time() < saved.get("expires_at", 0):
            return saved[ACCESS_TOKEN]
        refreshed = post_form(self._config.token_endpoint, {
            "grant_type": REFRESH_TOKEN, "client_id": CLIENT_ID, REFRESH_TOKEN: saved.get(REFRESH_TOKEN, ""),
        })
        if ACCESS_TOKEN not in refreshed:
            raise Failure("Your sign-in expired; run: dataflow login")
        self.save(refreshed)
        return refreshed[ACCESS_TOKEN]

    def save(self, tokens: dict[str, Any]) -> None:
        self._file.parent.mkdir(parents=True, exist_ok=True)
        expires_at = time.time() + tokens.get("expires_in", 0) - EXPIRY_MARGIN
        self._file.write_text(json.dumps({**tokens, "expires_at": expires_at}))
        self._file.chmod(PRIVATE_FILE)

    def forget(self) -> None:
        self._file.unlink(missing_ok=True)


def device_login(config: Config, tokens: Tokens, show: Callable[[str], None],
                 sleep: Callable[[float], None] = time.sleep) -> None:
    start = post_form(config.device_endpoint, {"client_id": CLIENT_ID, "scope": "openid profile email"})
    if DEVICE_CODE not in start:
        raise Failure(start.get("error_description", "Device sign-in is not available"))
    show(f"Open {start['verification_uri_complete']}\nand check the code {start['user_code']}.")
    interval = start.get("interval", POLL_SECONDS)
    deadline = time.time() + start.get("expires_in", CODE_LIFETIME_SECONDS)
    while time.time() < deadline:
        sleep(interval)
        answer = post_form(config.token_endpoint, {
            "grant_type": DEVICE_GRANT, "client_id": CLIENT_ID, DEVICE_CODE: start[DEVICE_CODE],
        })
        if ACCESS_TOKEN in answer:
            tokens.save(answer)
            return
        match answer.get("error"):
            case "slow_down":
                interval += SLOW_DOWN_SECONDS
            case "authorization_pending":
                pass
            case error:
                raise Failure(answer.get("error_description", error or "Sign-in failed"))
    raise Failure("The code expired; run dataflow login again")
