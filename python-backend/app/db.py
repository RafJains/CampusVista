from __future__ import annotations

import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterable, Iterator


BACKEND_ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = BACKEND_ROOT / "data"
DEFAULT_DB_PATH = DATA_DIR / "campus_seed.db"
DEFAULT_MAP_CONFIG_PATH = DATA_DIR / "map_config.json"


def get_db_path() -> Path:
    configured = os.getenv("CAMPUSVISTA_DB_PATH")
    return Path(configured) if configured else DEFAULT_DB_PATH


@contextmanager
def connect(db_path: Path | str | None = None) -> Iterator[sqlite3.Connection]:
    path = Path(db_path) if db_path is not None else get_db_path()
    connection = sqlite3.connect(path)
    connection.row_factory = sqlite3.Row
    try:
        connection.execute("PRAGMA foreign_keys = ON")
        yield connection
    finally:
        connection.close()


def fetch_all(
    sql: str,
    params: Iterable[Any] = (),
    db_path: Path | str | None = None,
) -> list[dict[str, Any]]:
    with connect(db_path) as connection:
        rows = connection.execute(sql, tuple(params)).fetchall()
    return [dict(row) for row in rows]


def fetch_one(
    sql: str,
    params: Iterable[Any] = (),
    db_path: Path | str | None = None,
) -> dict[str, Any] | None:
    with connect(db_path) as connection:
        row = connection.execute(sql, tuple(params)).fetchone()
    return dict(row) if row is not None else None
