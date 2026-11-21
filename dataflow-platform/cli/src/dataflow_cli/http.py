import json
import urllib.error
import urllib.parse
import urllib.request
from http import HTTPStatus
from typing import Any

FORM_TIMEOUT_SECONDS = 30
CALL_TIMEOUT_SECONDS = 120


class Failure(Exception):
    pass


def post_form(url: str, fields: dict[str, str]) -> dict[str, Any]:
    request = urllib.request.Request(url, data=urllib.parse.urlencode(fields).encode(), method="POST",
                                     headers={"Content-Type": "application/x-www-form-urlencoded"})
    try:
        with urllib.request.urlopen(request, timeout=FORM_TIMEOUT_SECONDS) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        return json.load(error)


def call(method: str, url: str, token: str | None = None, body: Any = None) -> Any:
    headers = {"Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=CALL_TIMEOUT_SECONDS) as response:
            text = response.read().decode()
            return json.loads(text) if text else None
    except urllib.error.HTTPError as error:
        if error.code == HTTPStatus.UNAUTHORIZED:
            raise Failure("Not signed in; run: dataflow login") from None
        raise Failure(f"{error.code}: {_message(error)}") from None


def _message(error: urllib.error.HTTPError) -> str:
    text = error.read().decode()
    try:
        return json.loads(text).get("error", text)
    except ValueError:
        return text or str(error.reason)
