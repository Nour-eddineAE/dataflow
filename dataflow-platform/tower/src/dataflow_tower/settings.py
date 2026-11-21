import json
from collections.abc import Iterator
from typing import Any, get_args

import dagster as dg
from pydantic import BaseModel

RESOURCE = "tower_settings"
PREFIX = "tower"


def settings_resource(settings: BaseModel) -> dg.ResourceDefinition:
    return dg.ResourceDefinition(
        resource_fn=lambda _: None,
        description="The tower's settings in effect, read by the Workspace's Configuration page",
        config_schema={name: dg.Field(config_type, default_value=value, is_required=False, description=description)
                       for name, config_type, value, description in flattened(settings, PREFIX)},
    )


def flattened(model: BaseModel, prefix: str) -> Iterator[tuple[str, Any, Any, str]]:
    for name, field in type(model).model_fields.items():
        value = getattr(model, name)
        key = f"{prefix}.{name}"
        if isinstance(value, BaseModel):
            yield from flattened(value, key)
        elif value is not None or not _holds_model(field.annotation):
            yield key, *_typed(value), field.description or ""


def _holds_model(annotation: Any) -> bool:
    return any(isinstance(kind, type) and issubclass(kind, BaseModel) for kind in (annotation, *get_args(annotation)))


def _typed(value: Any) -> tuple[Any, Any]:
    if isinstance(value, bool):
        return dg.Bool, value
    if isinstance(value, int):
        return dg.Int, value
    if value is None:
        return dg.String, ""
    if isinstance(value, str):
        return dg.String, value
    return dg.String, json.dumps(value, sort_keys=True)
