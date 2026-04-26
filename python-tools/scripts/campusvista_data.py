from __future__ import annotations

import csv
import json
import math
import re
import shutil
import sqlite3
import struct
from collections import defaultdict, deque
from pathlib import Path
from typing import Any


PYTHON_TOOLS_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = PYTHON_TOOLS_DIR.parent
RAW_DIR = PYTHON_TOOLS_DIR / "data" / "raw"
PROCESSED_DIR = PYTHON_TOOLS_DIR / "data" / "processed"
SEED_DIR = PYTHON_TOOLS_DIR / "data" / "seed"
CONFIG_PATH = PYTHON_TOOLS_DIR / "config.json"
LABELS_PATH = PYTHON_TOOLS_DIR / "data" / "model" / "labels.txt"
ANDROID_ASSETS_DIR = REPO_ROOT / "android-app" / "app" / "src" / "main" / "assets"

CSV_FILES = {
    "checkpoints": "outdoor_checkpoints.csv",
    "places": "places.csv",
    "edges": "edges.csv",
    "crowd_rules": "crowd_rules.csv",
    "outdoor_panos": "outdoor_panos.csv",
    "recognition_refs": "recognition_refs.csv",
    "search_aliases": "search_aliases.csv",
}

REQUIRED_FIELDS = {
    "checkpoints": [
        "checkpoint_id",
        "checkpoint_name",
        "checkpoint_type",
        "x_coord",
        "y_coord",
        "latitude",
        "longitude",
        "description",
        "orientation",
    ],
    "places": [
        "place_id",
        "place_name",
        "place_type",
        "checkpoint_id",
        "description",
        "keywords",
    ],
    "edges": [
        "edge_id",
        "from_checkpoint_id",
        "to_checkpoint_id",
        "distance_meters",
        "is_bidirectional",
        "edge_type",
    ],
    "crowd_rules": [
        "crowd_rule_id",
        "checkpoint_id",
        "day_type",
        "start_time",
        "end_time",
        "crowd_level",
        "penalty_cost",
        "description",
    ],
    "outdoor_panos": [
        "pano_id",
        "checkpoint_id",
        "image_file",
        "thumbnail_file",
        "orientation",
        "description",
    ],
    "recognition_refs": [
        "recognition_id",
        "checkpoint_id",
        "label_name",
        "model_label_index",
        "reference_image_file",
        "confidence_threshold",
    ],
    "search_aliases": [
        "alias_id",
        "place_id",
        "alias_text",
        "alias_type",
    ],
}

VALID_CHECKPOINT_TYPES = {
    "outdoor_path",
    "gate",
    "building_entry",
    "junction",
    "landmark",
    "facility_entry",
    "parking",
}
VALID_EDGE_TYPES = {"outdoor_walk", "entry_transition"}
VALID_PLACE_TYPES = {
    "building",
    "canteen",
    "library",
    "hostel",
    "admin_block",
    "academic_block",
    "parking",
    "gate",
    "landmark",
    "facility",
}
VALID_DAY_TYPES = {"weekday", "weekend"}
VALID_CROWD_LEVELS = {"low", "medium", "high", "very_high"}
VALID_ALIAS_TYPES = {
    "abbreviation",
    "common_name",
    "alternate_spelling",
    "old_name",
    "nickname",
}
TIME_RE = re.compile(r"^([01][0-9]|2[0-3]):[0-5][0-9]$")
MAX_PANO_BYTES = 2 * 1024 * 1024
MAX_TOTAL_PANO_BYTES = 60 * 1024 * 1024
MAX_MAP_BYTES = 8 * 1024 * 1024
SEED_DB_VERSION = 1


class ValidationError(Exception):
    """Raised when source campus data violates the final MVP spec."""


def load_config() -> dict[str, Any]:
    with CONFIG_PATH.open("r", encoding="utf-8") as file:
        return json.load(file)


def load_labels() -> list[str]:
    if not LABELS_PATH.exists():
        return []
    return [
        line.strip()
        for line in LABELS_PATH.read_text(encoding="utf-8").splitlines()
        if line.strip()
    ]


def load_csv(name: str) -> list[dict[str, str]]:
    path = RAW_DIR / CSV_FILES[name]
    with path.open("r", encoding="utf-8-sig", newline="") as file:
        reader = csv.DictReader(file)
        rows: list[dict[str, str]] = []
        for row in reader:
            rows.append(
                {
                    str(key): value.strip() if isinstance(value, str) else ""
                    for key, value in row.items()
                }
            )
        return rows


def load_data() -> dict[str, list[dict[str, str]]]:
    return {name: load_csv(name) for name in CSV_FILES}


def validate_all() -> tuple[dict[str, list[dict[str, str]]], dict[str, Any]]:
    return validate_or_raise("all")


def validate_or_raise(scope: str = "all") -> tuple[dict[str, list[dict[str, str]]], dict[str, Any]]:
    data = load_data()
    config = load_config()
    errors = collect_validation_errors(data, config, scope)
    if errors:
        detail = "\n".join(f"- {error}" for error in errors)
        raise ValidationError(f"CampusVista data validation failed:\n{detail}")
    return data, config


def collect_validation_errors(
    data: dict[str, list[dict[str, str]]],
    config: dict[str, Any],
    scope: str = "all",
) -> list[str]:
    errors: list[str] = []
    scopes = {
        "all",
        "checkpoints",
        "edges",
        "map_scale",
        "crowd_rules",
        "outdoor_panos",
        "recognition_refs",
        "search_aliases",
        "places",
    }
    if scope not in scopes:
        raise ValueError(f"Unknown validation scope: {scope}")

    validate_config(config, errors)
    validate_checkpoints(data["checkpoints"], errors)
    checkpoint_ids = {row.get("checkpoint_id", "") for row in data["checkpoints"]}
    place_ids = {row.get("place_id", "") for row in data["places"]}

    if scope in {"all", "places"}:
        validate_places(data["places"], checkpoint_ids, errors)

    if scope in {"all", "edges", "map_scale"}:
        validate_edges(data["edges"], data["checkpoints"], config, errors)

    if scope in {"all", "crowd_rules"}:
        validate_crowd_rules(data["crowd_rules"], checkpoint_ids, errors)

    if scope in {"all", "outdoor_panos"}:
        validate_outdoor_panos(data["outdoor_panos"], checkpoint_ids, errors)

    if scope in {"all", "recognition_refs"}:
        validate_recognition_refs(data["recognition_refs"], checkpoint_ids, errors)

    if scope in {"all", "search_aliases"}:
        validate_search_aliases(data["search_aliases"], place_ids, errors)

    return errors


def validate_config(config: dict[str, Any], errors: list[str]) -> None:
    for key in [
        "campus_map_file",
        "campus_map_width_px",
        "campus_map_height_px",
        "meters_per_pixel",
    ]:
        if key not in config:
            errors.append(f"config.json is missing {key}")

    width = _as_float(config.get("campus_map_width_px"), "config.campus_map_width_px", errors)
    height = _as_float(config.get("campus_map_height_px"), "config.campus_map_height_px", errors)
    meters_per_pixel = _as_float(config.get("meters_per_pixel"), "config.meters_per_pixel", errors)

    if width <= 0:
        errors.append("config.campus_map_width_px must be greater than 0")
    if height <= 0:
        errors.append("config.campus_map_height_px must be greater than 0")
    if meters_per_pixel <= 0:
        errors.append("config.meters_per_pixel must be greater than 0")

    campus_map_file = str(config.get("campus_map_file", ""))
    if not campus_map_file.strip():
        errors.append("config.campus_map_file is required")
    if "/" in campus_map_file or "\\" in campus_map_file:
        errors.append("config.campus_map_file must be a filename, not a path")
    if Path(campus_map_file).suffix.lower() != ".png":
        errors.append("config.campus_map_file must be a .png file")
    if campus_map_file and "/" not in campus_map_file and "\\" not in campus_map_file:
        map_path = RAW_DIR / "maps" / campus_map_file
        if not map_path.exists():
            errors.append(f"campus map asset is missing: {map_path}")
        else:
            size = map_path.stat().st_size
            if size == 0:
                errors.append(f"campus map asset is empty: {map_path}")
            if size > MAX_MAP_BYTES:
                errors.append("campus map asset exceeds the 8 MB MVP budget")
            _validate_png_file(
                map_path,
                int(width) if width > 0 else None,
                int(height) if height > 0 else None,
                "campus map asset",
                errors,
            )


def validate_checkpoints(rows: list[dict[str, str]], errors: list[str]) -> None:
    _require_fields("checkpoints", rows, errors)
    _ensure_unique(rows, "checkpoint_id", "checkpoints", errors)
    for row in rows:
        checkpoint_id = row.get("checkpoint_id", "")
        _require_value(row, "checkpoint_id", "checkpoints", errors)
        _require_value(row, "checkpoint_name", f"checkpoint {checkpoint_id}", errors)
        if row.get("checkpoint_type") not in VALID_CHECKPOINT_TYPES:
            errors.append(
                f"checkpoint {checkpoint_id} has invalid checkpoint_type {row.get('checkpoint_type')!r}"
            )
        x_coord = _as_float(row.get("x_coord"), f"checkpoint {checkpoint_id} x_coord", errors)
        y_coord = _as_float(row.get("y_coord"), f"checkpoint {checkpoint_id} y_coord", errors)
        if x_coord < 0 or y_coord < 0:
            errors.append(f"checkpoint {checkpoint_id} coordinates must be non-negative")
        if row.get("latitude"):
            _as_float(row.get("latitude"), f"checkpoint {checkpoint_id} latitude", errors)
        if row.get("longitude"):
            _as_float(row.get("longitude"), f"checkpoint {checkpoint_id} longitude", errors)


def validate_places(
    rows: list[dict[str, str]], checkpoint_ids: set[str], errors: list[str]
) -> None:
    _require_fields("places", rows, errors)
    _ensure_unique(rows, "place_id", "places", errors)
    for row in rows:
        place_id = row.get("place_id", "")
        _require_value(row, "place_name", f"place {place_id}", errors)
        if row.get("place_type") not in VALID_PLACE_TYPES:
            errors.append(f"place {place_id} has invalid place_type {row.get('place_type')!r}")
        if row.get("checkpoint_id") not in checkpoint_ids:
            errors.append(
                f"place {place_id} references unknown checkpoint_id {row.get('checkpoint_id')!r}"
            )


def validate_edges(
    rows: list[dict[str, str]],
    checkpoint_rows: list[dict[str, str]],
    config: dict[str, Any],
    errors: list[str],
) -> None:
    _require_fields("edges", rows, errors)
    _ensure_unique(rows, "edge_id", "edges", errors)
    checkpoints = {row.get("checkpoint_id", ""): row for row in checkpoint_rows}
    meters_per_pixel = _as_float(config.get("meters_per_pixel"), "config.meters_per_pixel", errors)

    for row in rows:
        edge_id = row.get("edge_id", "")
        from_id = row.get("from_checkpoint_id", "")
        to_id = row.get("to_checkpoint_id", "")
        if from_id not in checkpoints:
            errors.append(f"edge {edge_id} references unknown from_checkpoint_id {from_id!r}")
        if to_id not in checkpoints:
            errors.append(f"edge {edge_id} references unknown to_checkpoint_id {to_id!r}")
        if from_id == to_id:
            errors.append(f"edge {edge_id} cannot connect a checkpoint to itself")
        distance = _as_float(row.get("distance_meters"), f"edge {edge_id} distance_meters", errors)
        if distance <= 0:
            errors.append(f"edge {edge_id} distance_meters must be greater than 0")
        if row.get("is_bidirectional") not in {"0", "1"}:
            errors.append(f"edge {edge_id} is_bidirectional must be 0 or 1")
        if row.get("edge_type") not in VALID_EDGE_TYPES:
            errors.append(f"edge {edge_id} has invalid edge_type {row.get('edge_type')!r}")
        if from_id in checkpoints and to_id in checkpoints:
            pixel_distance = checkpoint_pixel_distance(checkpoints[from_id], checkpoints[to_id])
            heuristic_distance = pixel_distance * meters_per_pixel
            if pixel_distance <= 0:
                errors.append(f"edge {edge_id} pixel distance must be greater than 0")
            if heuristic_distance > distance + 1e-9:
                errors.append(
                    "Scale calibration error: "
                    f"edge {edge_id} heuristic distance {heuristic_distance:.2f}m "
                    f"exceeds distance_meters {distance:.2f}m"
                )

    _validate_graph_connectivity(rows, checkpoints, errors)


def validate_crowd_rules(
    rows: list[dict[str, str]], checkpoint_ids: set[str], errors: list[str]
) -> None:
    _require_fields("crowd_rules", rows, errors)
    _ensure_unique(rows, "crowd_rule_id", "crowd_rules", errors)
    intervals: dict[tuple[str, str], list[tuple[int, int, str]]] = defaultdict(list)

    for row in rows:
        rule_id = row.get("crowd_rule_id", "")
        checkpoint_id = row.get("checkpoint_id", "")
        day_type = row.get("day_type", "")
        if checkpoint_id not in checkpoint_ids:
            errors.append(f"crowd rule {rule_id} references unknown checkpoint_id {checkpoint_id!r}")
        if day_type not in VALID_DAY_TYPES:
            errors.append(f"crowd rule {rule_id} has invalid day_type {day_type!r}")
        if row.get("crowd_level") not in VALID_CROWD_LEVELS:
            errors.append(f"crowd rule {rule_id} has invalid crowd_level {row.get('crowd_level')!r}")
        start = row.get("start_time", "")
        end = row.get("end_time", "")
        if not TIME_RE.match(start):
            errors.append(f"crowd rule {rule_id} start_time must use HH:MM")
        if not TIME_RE.match(end):
            errors.append(f"crowd rule {rule_id} end_time must use HH:MM")
        start_minutes = _time_to_minutes(start) if TIME_RE.match(start) else -1
        end_minutes = _time_to_minutes(end) if TIME_RE.match(end) else -1
        if start_minutes >= 0 and end_minutes >= 0:
            if start_minutes >= end_minutes:
                errors.append(f"crowd rule {rule_id} must have start_time before end_time")
            intervals[(checkpoint_id, day_type)].append((start_minutes, end_minutes, rule_id))
        penalty = _as_float(row.get("penalty_cost"), f"crowd rule {rule_id} penalty_cost", errors)
        if penalty < 0:
            errors.append(f"crowd rule {rule_id} penalty_cost must be non-negative")

    for (checkpoint_id, day_type), ranges in intervals.items():
        ranges.sort()
        for previous, current in zip(ranges, ranges[1:]):
            if previous[1] > current[0]:
                errors.append(
                    f"crowd rules overlap for {checkpoint_id}/{day_type}: "
                    f"{previous[2]} and {current[2]}"
                )


def validate_outdoor_panos(
    rows: list[dict[str, str]], checkpoint_ids: set[str], errors: list[str]
) -> None:
    _require_fields("outdoor_panos", rows, errors)
    _ensure_unique(rows, "pano_id", "outdoor_panos", errors)
    total_size = 0
    for row in rows:
        pano_id = row.get("pano_id", "")
        if row.get("checkpoint_id") not in checkpoint_ids:
            errors.append(
                f"outdoor pano {pano_id} references unknown checkpoint_id {row.get('checkpoint_id')!r}"
            )
        for field in ["image_file", "thumbnail_file"]:
            filename = row.get(field, "")
            if not filename:
                if field == "image_file":
                    errors.append(f"outdoor pano {pano_id} image_file is required")
                continue
            _validate_filename_only(filename, f"outdoor pano {pano_id} {field}", errors)
            if Path(filename).suffix.lower() not in {".jpg", ".jpeg"}:
                errors.append(f"outdoor pano {pano_id} {field} must be .jpg or .jpeg")
            path = RAW_DIR / "outdoor_panos" / filename
            if not path.exists():
                errors.append(f"outdoor pano {pano_id} missing file {filename}")
                continue
            size = path.stat().st_size
            if size > MAX_PANO_BYTES:
                errors.append(f"outdoor pano {pano_id} {filename} exceeds 2 MB")
            _validate_jpeg_file(path, f"outdoor pano {pano_id} {field}", errors)
            total_size += size
    if total_size > MAX_TOTAL_PANO_BYTES:
        errors.append("outdoor pano assets exceed the 60 MB MVP budget")


def validate_recognition_refs(
    rows: list[dict[str, str]], checkpoint_ids: set[str], errors: list[str]
) -> None:
    _require_fields("recognition_refs", rows, errors)
    _ensure_unique(rows, "recognition_id", "recognition_refs", errors)
    labels = load_labels()
    if not labels:
        errors.append(f"labels.txt is missing or empty at {LABELS_PATH}")

    for row in rows:
        recognition_id = row.get("recognition_id", "")
        if row.get("checkpoint_id") not in checkpoint_ids:
            errors.append(
                "recognition ref "
                f"{recognition_id} references unknown checkpoint_id {row.get('checkpoint_id')!r}"
            )
        label_name = row.get("label_name", "")
        label_index = _as_int(
            row.get("model_label_index"),
            f"recognition ref {recognition_id} model_label_index",
            errors,
        )
        if label_index < 0:
            errors.append(f"recognition ref {recognition_id} model_label_index must be non-negative")
        elif labels and label_index >= len(labels):
            errors.append(f"recognition ref {recognition_id} model_label_index is not in labels.txt")
        elif labels and labels[label_index] != label_name:
            errors.append(
                f"recognition ref {recognition_id} label mismatch: "
                f"labels.txt[{label_index}] is {labels[label_index]!r}, not {label_name!r}"
            )
        threshold = _as_float(
            row.get("confidence_threshold"),
            f"recognition ref {recognition_id} confidence_threshold",
            errors,
        )
        if threshold < 0 or threshold > 1:
            errors.append(
                f"recognition ref {recognition_id} confidence_threshold must be between 0 and 1"
            )
        reference_file = row.get("reference_image_file", "")
        if reference_file:
            _validate_filename_only(
                reference_file,
                f"recognition ref {recognition_id} reference_image_file",
                errors,
            )
            path = RAW_DIR / "recognition_refs" / reference_file
            if not path.exists():
                errors.append(f"recognition ref {recognition_id} missing file {reference_file}")


def validate_search_aliases(
    rows: list[dict[str, str]], place_ids: set[str], errors: list[str]
) -> None:
    _require_fields("search_aliases", rows, errors)
    _ensure_unique(rows, "alias_id", "search_aliases", errors)
    for row in rows:
        alias_id = row.get("alias_id", "")
        if row.get("place_id") not in place_ids:
            errors.append(f"search alias {alias_id} references unknown place_id {row.get('place_id')!r}")
        _require_value(row, "alias_text", f"search alias {alias_id}", errors)
        if row.get("alias_type") not in VALID_ALIAS_TYPES:
            errors.append(f"search alias {alias_id} has invalid alias_type {row.get('alias_type')!r}")


def generate_all() -> dict[str, Path]:
    data, config = validate_all()
    write_processed_outputs(data, config)
    seed_db = SEED_DIR / "campus_seed.db"
    create_seed_db(data, seed_db)
    publish_android_assets(seed_db, config)
    return {
        "seed_db": seed_db,
        "android_seed_db": ANDROID_ASSETS_DIR / "seed" / "campus_seed.db",
        "android_map_config": ANDROID_ASSETS_DIR / "config" / "map_config.json",
        "android_map_asset": ANDROID_ASSETS_DIR / "maps" / str(config["campus_map_file"]),
        "android_labels": ANDROID_ASSETS_DIR / "ml" / "labels.txt",
    }


def write_processed_outputs(
    data: dict[str, list[dict[str, str]]], config: dict[str, Any]
) -> None:
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    _write_json(PROCESSED_DIR / "checkpoints.json", data["checkpoints"])
    _write_json(PROCESSED_DIR / "places.json", data["places"])
    _write_json(PROCESSED_DIR / "edges.json", data["edges"])
    _write_json(PROCESSED_DIR / "map_config.json", config)
    _write_json(PROCESSED_DIR / "search_index.json", build_search_index(data))


def create_seed_db(data: dict[str, list[dict[str, str]]], output_path: Path) -> None:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()

    connection = sqlite3.connect(output_path)
    try:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.executescript(SCHEMA_SQL)
        connection.execute(f"PRAGMA user_version = {SEED_DB_VERSION}")
        _insert_rows(connection, data)
        connection.commit()
    finally:
        connection.close()


def publish_android_assets(seed_db: Path, config: dict[str, Any]) -> None:
    (ANDROID_ASSETS_DIR / "seed").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "config").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "maps").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "ml").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "pano" / "outdoor").mkdir(parents=True, exist_ok=True)

    shutil.copyfile(seed_db, ANDROID_ASSETS_DIR / "seed" / "campus_seed.db")
    _write_json(ANDROID_ASSETS_DIR / "config" / "map_config.json", config)
    map_file = str(config["campus_map_file"])
    shutil.copyfile(RAW_DIR / "maps" / map_file, ANDROID_ASSETS_DIR / "maps" / map_file)
    if LABELS_PATH.exists():
        shutil.copyfile(LABELS_PATH, ANDROID_ASSETS_DIR / "ml" / "labels.txt")

    for image_path in (RAW_DIR / "outdoor_panos").glob("*.jp*g"):
        shutil.copyfile(image_path, ANDROID_ASSETS_DIR / "pano" / "outdoor" / image_path.name)


def build_search_index(data: dict[str, list[dict[str, str]]]) -> list[dict[str, str]]:
    place_by_id = {row["place_id"]: row for row in data["places"]}
    index: list[dict[str, str]] = []
    for place in data["places"]:
        index.append(
            {
                "place_id": place["place_id"],
                "text": place["place_name"],
                "source": "place_name",
                "checkpoint_id": place["checkpoint_id"],
            }
        )
        if place.get("keywords"):
            index.append(
                {
                    "place_id": place["place_id"],
                    "text": place["keywords"],
                    "source": "keywords",
                    "checkpoint_id": place["checkpoint_id"],
                }
            )
    for alias in data["search_aliases"]:
        place = place_by_id.get(alias["place_id"], {})
        index.append(
            {
                "place_id": alias["place_id"],
                "text": alias["alias_text"],
                "source": "alias",
                "checkpoint_id": place.get("checkpoint_id", ""),
            }
        )
    return index


def graph_summary(data: dict[str, list[dict[str, str]]]) -> dict[str, int]:
    graph = build_graph(data)
    return {
        "nodes": len(graph),
        "edge_rows": len(data["edges"]),
        "directed_edges": sum(len(edges) for edges in graph.values()),
    }


def build_graph(data: dict[str, list[dict[str, str]]]) -> dict[str, list[dict[str, Any]]]:
    graph: dict[str, list[dict[str, Any]]] = {
        row["checkpoint_id"]: [] for row in data["checkpoints"]
    }
    for row in data["edges"]:
        distance = float(row["distance_meters"])
        forward_edge = {
            "edge_id": row["edge_id"],
            "to_checkpoint_id": row["to_checkpoint_id"],
            "distance_meters": distance,
            "edge_type": row["edge_type"],
            "reverse": False,
        }
        graph[row["from_checkpoint_id"]].append(forward_edge)
        if row["is_bidirectional"] == "1":
            graph[row["to_checkpoint_id"]].append(
                {
                    "edge_id": row["edge_id"],
                    "to_checkpoint_id": row["from_checkpoint_id"],
                    "distance_meters": distance,
                    "edge_type": row["edge_type"],
                    "reverse": True,
                }
            )
    return graph


def checkpoint_pixel_distance(
    from_checkpoint: dict[str, str], to_checkpoint: dict[str, str]
) -> float:
    return math.hypot(
        float(to_checkpoint["x_coord"]) - float(from_checkpoint["x_coord"]),
        float(to_checkpoint["y_coord"]) - float(from_checkpoint["y_coord"]),
    )


def main_validate(scope: str) -> None:
    validate_or_raise(scope)
    print(f"Validation OK: {scope}")


def _insert_rows(connection: sqlite3.Connection, data: dict[str, list[dict[str, str]]]) -> None:
    connection.executemany(
        """
        INSERT INTO checkpoints (
            checkpoint_id, checkpoint_name, checkpoint_type, x_coord, y_coord,
            latitude, longitude, description, orientation
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["checkpoint_id"],
                row["checkpoint_name"],
                row["checkpoint_type"],
                float(row["x_coord"]),
                float(row["y_coord"]),
                _blank_to_none(row.get("latitude")),
                _blank_to_none(row.get("longitude")),
                _blank_to_none(row.get("description")),
                _blank_to_none(row.get("orientation")),
            )
            for row in data["checkpoints"]
        ],
    )
    connection.executemany(
        """
        INSERT INTO places (
            place_id, place_name, place_type, checkpoint_id, description, keywords
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["place_id"],
                row["place_name"],
                row["place_type"],
                row["checkpoint_id"],
                _blank_to_none(row.get("description")),
                _blank_to_none(row.get("keywords")),
            )
            for row in data["places"]
        ],
    )
    connection.executemany(
        """
        INSERT INTO edges (
            edge_id, from_checkpoint_id, to_checkpoint_id, distance_meters,
            is_bidirectional, edge_type
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["edge_id"],
                row["from_checkpoint_id"],
                row["to_checkpoint_id"],
                float(row["distance_meters"]),
                int(row["is_bidirectional"]),
                row["edge_type"],
            )
            for row in data["edges"]
        ],
    )
    connection.executemany(
        """
        INSERT INTO crowd_rules (
            crowd_rule_id, checkpoint_id, day_type, start_time, end_time,
            crowd_level, penalty_cost, description
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["crowd_rule_id"],
                row["checkpoint_id"],
                row["day_type"],
                row["start_time"],
                row["end_time"],
                row["crowd_level"],
                float(row["penalty_cost"]),
                _blank_to_none(row.get("description")),
            )
            for row in data["crowd_rules"]
        ],
    )
    connection.executemany(
        """
        INSERT INTO outdoor_panos (
            pano_id, checkpoint_id, image_file, thumbnail_file, orientation, description
        ) VALUES (?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["pano_id"],
                row["checkpoint_id"],
                row["image_file"],
                _blank_to_none(row.get("thumbnail_file")),
                _blank_to_none(row.get("orientation")),
                _blank_to_none(row.get("description")),
            )
            for row in data["outdoor_panos"]
        ],
    )
    connection.executemany(
        """
        INSERT INTO recognition_refs (
            recognition_id, checkpoint_id, label_name, model_label_index,
            confidence_threshold
        ) VALUES (?, ?, ?, ?, ?)
        """,
        [
            (
                row["recognition_id"],
                row["checkpoint_id"],
                row["label_name"],
                int(row["model_label_index"]),
                float(row["confidence_threshold"]),
            )
            for row in data["recognition_refs"]
        ],
    )
    connection.executemany(
        """
        INSERT INTO search_aliases (
            alias_id, place_id, alias_text, alias_type
        ) VALUES (?, ?, ?, ?)
        """,
        [
            (
                row["alias_id"],
                row["place_id"],
                row["alias_text"],
                row["alias_type"],
            )
            for row in data["search_aliases"]
        ],
    )


def _validate_graph_connectivity(
    edge_rows: list[dict[str, str]], checkpoints: dict[str, dict[str, str]], errors: list[str]
) -> None:
    if not checkpoints:
        return
    adjacency: dict[str, set[str]] = {checkpoint_id: set() for checkpoint_id in checkpoints}
    for edge in edge_rows:
        from_id = edge.get("from_checkpoint_id", "")
        to_id = edge.get("to_checkpoint_id", "")
        if from_id in adjacency and to_id in adjacency:
            adjacency[from_id].add(to_id)
            if edge.get("is_bidirectional") == "1":
                adjacency[to_id].add(from_id)

    start = next(iter(checkpoints))
    visited = {start}
    queue: deque[str] = deque([start])
    while queue:
        checkpoint_id = queue.popleft()
        for neighbor in adjacency[checkpoint_id]:
            if neighbor not in visited:
                visited.add(neighbor)
                queue.append(neighbor)

    missing = sorted(set(checkpoints) - visited)
    if missing:
        errors.append(f"graph has disconnected checkpoints: {', '.join(missing)}")


def _require_fields(name: str, rows: list[dict[str, str]], errors: list[str]) -> None:
    required = REQUIRED_FIELDS[name]
    if not rows:
        errors.append(f"{CSV_FILES[name]} has no rows")
        return
    present = set(rows[0])
    for field in required:
        if field not in present:
            errors.append(f"{CSV_FILES[name]} is missing required column {field}")


def _ensure_unique(
    rows: list[dict[str, str]], field: str, label: str, errors: list[str]
) -> None:
    seen: set[str] = set()
    for row in rows:
        value = row.get(field, "")
        if not value:
            errors.append(f"{label} row is missing {field}")
            continue
        if value in seen:
            errors.append(f"{label} has duplicate {field} {value!r}")
        seen.add(value)


def _require_value(
    row: dict[str, str], field: str, label: str, errors: list[str]
) -> None:
    if not row.get(field):
        errors.append(f"{label} is missing {field}")


def _as_float(value: Any, label: str, errors: list[str]) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        errors.append(f"{label} must be numeric")
        return 0.0


def _as_int(value: Any, label: str, errors: list[str]) -> int:
    try:
        return int(str(value))
    except (TypeError, ValueError):
        errors.append(f"{label} must be an integer")
        return -1


def _time_to_minutes(value: str) -> int:
    hour, minute = value.split(":")
    return int(hour) * 60 + int(minute)


def _validate_filename_only(filename: str, label: str, errors: list[str]) -> None:
    if "/" in filename or "\\" in filename or Path(filename).name != filename:
        errors.append(f"{label} must be a filename only")


def _validate_jpeg_file(path: Path, label: str, errors: list[str]) -> None:
    try:
        data = path.read_bytes()
    except OSError as exception:
        errors.append(f"{label} could not be read: {exception}")
        return

    if len(data) < 4 or data[:2] != b"\xff\xd8" or data[-2:] != b"\xff\xd9":
        errors.append(f"{label} must be a valid JPEG file")


def _validate_png_file(
    path: Path,
    expected_width: int | None,
    expected_height: int | None,
    label: str,
    errors: list[str],
) -> None:
    try:
        header = path.read_bytes()[:24]
    except OSError as exception:
        errors.append(f"{label} could not be read: {exception}")
        return

    if len(header) < 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        errors.append(f"{label} must be a valid PNG file")
        return

    width, height = struct.unpack(">II", header[16:24])
    if expected_width is not None and width != expected_width:
        errors.append(f"{label} width {width}px does not match config width {expected_width}px")
    if expected_height is not None and height != expected_height:
        errors.append(f"{label} height {height}px does not match config height {expected_height}px")


def _blank_to_none(value: Any) -> Any:
    if value is None:
        return None
    value = str(value)
    return value if value else None


def _write_json(path: Path, payload: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, indent=2, sort_keys=True) + "\n", encoding="utf-8")


SCHEMA_SQL = """
CREATE TABLE checkpoints (
    checkpoint_id TEXT PRIMARY KEY,
    checkpoint_name TEXT NOT NULL,
    checkpoint_type TEXT NOT NULL,
    x_coord REAL NOT NULL,
    y_coord REAL NOT NULL,
    latitude REAL,
    longitude REAL,
    description TEXT,
    orientation TEXT
);

CREATE TABLE places (
    place_id TEXT PRIMARY KEY,
    place_name TEXT NOT NULL,
    place_type TEXT NOT NULL,
    checkpoint_id TEXT NOT NULL,
    description TEXT,
    keywords TEXT,
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id)
);

CREATE TABLE edges (
    edge_id TEXT PRIMARY KEY,
    from_checkpoint_id TEXT NOT NULL,
    to_checkpoint_id TEXT NOT NULL,
    distance_meters REAL NOT NULL,
    is_bidirectional INTEGER NOT NULL DEFAULT 1,
    edge_type TEXT,
    FOREIGN KEY (from_checkpoint_id) REFERENCES checkpoints(checkpoint_id),
    FOREIGN KEY (to_checkpoint_id) REFERENCES checkpoints(checkpoint_id)
);

CREATE TABLE crowd_rules (
    crowd_rule_id TEXT PRIMARY KEY,
    checkpoint_id TEXT NOT NULL,
    day_type TEXT NOT NULL,
    start_time TEXT NOT NULL,
    end_time TEXT NOT NULL,
    crowd_level TEXT NOT NULL,
    penalty_cost REAL NOT NULL,
    description TEXT,
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id)
);

CREATE TABLE outdoor_panos (
    pano_id TEXT PRIMARY KEY,
    checkpoint_id TEXT NOT NULL,
    image_file TEXT NOT NULL,
    thumbnail_file TEXT,
    orientation TEXT,
    description TEXT,
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id)
);

CREATE TABLE recognition_refs (
    recognition_id TEXT PRIMARY KEY,
    checkpoint_id TEXT NOT NULL,
    label_name TEXT NOT NULL,
    model_label_index INTEGER NOT NULL,
    confidence_threshold REAL DEFAULT 0.70,
    FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id)
);

CREATE TABLE search_aliases (
    alias_id TEXT PRIMARY KEY,
    place_id TEXT NOT NULL,
    alias_text TEXT NOT NULL,
    alias_type TEXT,
    FOREIGN KEY (place_id) REFERENCES places(place_id)
);

CREATE INDEX idx_edges_from
ON edges(from_checkpoint_id);

CREATE INDEX idx_edges_to
ON edges(to_checkpoint_id);

CREATE INDEX idx_places_type
ON places(place_type);

CREATE INDEX idx_places_checkpoint
ON places(checkpoint_id);

CREATE INDEX idx_crowd_checkpoint_time
ON crowd_rules(checkpoint_id, day_type, start_time, end_time);

CREATE INDEX idx_panos_checkpoint
ON outdoor_panos(checkpoint_id);

CREATE INDEX idx_recognition_label
ON recognition_refs(model_label_index);

CREATE INDEX idx_alias_text
ON search_aliases(alias_text);
"""
