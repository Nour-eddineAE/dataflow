import dagster as dg
from pathlib import Path


@dg.definitions
def defs() -> dg.Definitions:
    return dg.load_from_defs_folder(path_within_project=Path(__file__).parent)
