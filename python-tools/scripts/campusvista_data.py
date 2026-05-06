from __future__ import annotations

import csv
import io
import json
import math
import re
import shutil
import sqlite3
import struct
import sys
import zipfile
from collections import defaultdict, deque
from pathlib import Path
from typing import Any

import numpy as np

PYTHON_COMMON_DIR = Path(__file__).resolve().parents[2] / "python-common"
if str(PYTHON_COMMON_DIR) not in sys.path:
    sys.path.insert(0, str(PYTHON_COMMON_DIR))

from campusvista_recognition import (  # noqa: E402
    HANDCRAFTED_ENCODER_NAME,
    decode_image,
    encoder_from_environment,
    reference_views,
)


PYTHON_TOOLS_DIR = Path(__file__).resolve().parents[1]
REPO_ROOT = PYTHON_TOOLS_DIR.parent
RAW_DIR = PYTHON_TOOLS_DIR / "data" / "raw"
PROCESSED_DIR = PYTHON_TOOLS_DIR / "data" / "processed"
SEED_DIR = PYTHON_TOOLS_DIR / "data" / "seed"
CONFIG_PATH = PYTHON_TOOLS_DIR / "config.json"
RECOGNITION_DIR = PYTHON_TOOLS_DIR / "data" / "recognition"
RECOGNITION_INDEX_FILENAME = "recognition_index.npz"
RECOGNITION_ANDROID_INDEX_FILENAME = "recognition_index.bin"
RECOGNITION_ANDROID_LABELS_FILENAME = "recognition_index_labels.csv"
RECOGNITION_METADATA_FILENAME = "recognition_metadata.json"
ANDROID_ASSETS_DIR = REPO_ROOT / "android-app" / "app" / "src" / "main" / "assets"
BACKEND_DATA_DIR = REPO_ROOT / "python-backend" / "data"

CSV_FILES = {
    "checkpoints": "outdoor_checkpoints.csv",
    "places": "places.csv",
    "edges": "edges.csv",
    "crowd_rules": "crowd_rules.csv",
    "outdoor_panos": "outdoor_panos.csv",
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
    "corner",
    "hostel_entry",
    "lawn",
    "narrow_passage",
    "oat_front",
    "passage",
    "retail_store",
    "sports_complex",
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
    "entertainment",
    "fitness",
    "food hall",
    "food_hall",
    "lab",
    "laundry",
    "meeting",
    "play area",
    "play_area",
    "stage",
    "worship",
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
SEED_DB_VERSION = 3


class ValidationError(Exception):
    """Raised when source campus data violates the final MVP spec."""


def load_config() -> dict[str, Any]:
    with CONFIG_PATH.open("r", encoding="utf-8") as file:
        return json.load(file)


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


def import_real_data(
    workbook_path: Path | str,
    map_pdf_path: Path | str,
    panos_zip_path: Path | str,
) -> dict[str, int]:
    workbook_path = Path(workbook_path)
    map_pdf_path = Path(map_pdf_path)
    panos_zip_path = Path(panos_zip_path)

    _reset_raw_data_dirs()
    rows = _load_workbook_rows(workbook_path)
    markers = _extract_pdf_markers(map_pdf_path)
    map_image, map_width, map_height = _extract_pdf_map_image(map_pdf_path)
    _draw_markers_on_map(map_image, markers)

    config = _config_from_excel(rows.get("config", []))
    config["campus_map_file"] = "campus_map.png"
    config["campus_map_width_px"] = map_width
    config["campus_map_height_px"] = map_height

    pano_file_by_checkpoint = _extract_available_panos(panos_zip_path)
    checkpoint_rows = _build_checkpoint_rows(rows.get("checkpoints", []), markers)
    checkpoint_ids = {row["checkpoint_id"] for row in checkpoint_rows}
    place_rows = _build_place_rows(rows.get("places", []), checkpoint_ids)
    edge_rows = _build_edge_rows(
        rows.get("edges", []),
        {row["checkpoint_id"]: row for row in checkpoint_rows},
        float(config["meters_per_pixel"]),
    )
    pano_rows = _build_pano_rows(rows.get("panos", []), checkpoint_ids, pano_file_by_checkpoint)
    crowd_rows = _build_crowd_rows(rows.get("crowd_rules", []), checkpoint_ids)
    alias_rows = _build_search_alias_rows(
        rows.get("search_aliases", []),
        place_rows,
        checkpoint_rows,
    )

    _write_json(CONFIG_PATH, config)
    (RAW_DIR / "maps").mkdir(parents=True, exist_ok=True)
    map_image.save(RAW_DIR / "maps" / "campus_map.png", optimize=True)
    _write_csv(RAW_DIR / CSV_FILES["checkpoints"], checkpoint_rows)
    _write_csv(RAW_DIR / CSV_FILES["places"], place_rows)
    _write_csv(RAW_DIR / CSV_FILES["edges"], edge_rows)
    _write_csv(RAW_DIR / CSV_FILES["outdoor_panos"], pano_rows)
    _write_csv(RAW_DIR / CSV_FILES["crowd_rules"], crowd_rows)
    _write_csv(RAW_DIR / CSV_FILES["search_aliases"], alias_rows)

    return {
        "checkpoints": len(checkpoint_rows),
        "edges": len(edge_rows),
        "places": len(place_rows),
        "panos": len(pano_rows),
        "crowd_rules": len(crowd_rows),
        "search_aliases": len(alias_rows),
    }


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
        if row.get("raw_map_x"):
            _as_float(row.get("raw_map_x"), f"checkpoint {checkpoint_id} raw_map_x", errors)
        if row.get("raw_map_y"):
            _as_float(row.get("raw_map_y"), f"checkpoint {checkpoint_id} raw_map_y", errors)
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
        place_checkpoint_ids = _checkpoint_ids_for_place(row)
        if not place_checkpoint_ids:
            errors.append(f"place {place_id} must reference at least one checkpoint")
        for checkpoint_id in place_checkpoint_ids:
            if checkpoint_id not in checkpoint_ids:
                errors.append(
                    f"place {place_id} references unknown checkpoint_id {checkpoint_id!r}"
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
            if heuristic_distance > distance + 1e-6:
                errors.append(
                    "Scale calibration error: "
                    f"edge {edge_id} heuristic distance {heuristic_distance:.2f}m "
                    f"exceeds distance_meters {distance:.2f}m"
                )

    _validate_graph_connectivity(rows, checkpoints, errors)


def validate_crowd_rules(
    rows: list[dict[str, str]], checkpoint_ids: set[str], errors: list[str]
) -> None:
    if not rows:
        return
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
    if not rows:
        return
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


def validate_search_aliases(
    rows: list[dict[str, str]], place_ids: set[str], errors: list[str]
) -> None:
    if not rows:
        return
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
    data["recognition_refs"] = build_recognition_refs(data)
    write_processed_outputs(data, config)
    recognition_index, recognition_metadata, android_index, android_labels = build_recognition_index(data)
    seed_db = SEED_DIR / "campus_seed.db"
    create_seed_db(data, seed_db)
    publish_backend_assets(seed_db, config, recognition_index, recognition_metadata)
    publish_android_assets(seed_db, config, android_index, android_labels)
    outputs = {
        "seed_db": seed_db,
        "recognition_index": recognition_index,
        "recognition_metadata": recognition_metadata,
        "backend_seed_db": BACKEND_DATA_DIR / "campus_seed.db",
        "backend_recognition_index": BACKEND_DATA_DIR / "recognition" / RECOGNITION_INDEX_FILENAME,
        "backend_recognition_metadata": BACKEND_DATA_DIR / "recognition" / RECOGNITION_METADATA_FILENAME,
        "backend_map_config": BACKEND_DATA_DIR / "map_config.json",
        "android_seed_db": ANDROID_ASSETS_DIR / "seed" / "campus_seed.db",
        "android_recognition_index": ANDROID_ASSETS_DIR / "ml" / RECOGNITION_ANDROID_INDEX_FILENAME,
        "android_recognition_labels": ANDROID_ASSETS_DIR / "ml" / RECOGNITION_ANDROID_LABELS_FILENAME,
        "android_map_config": ANDROID_ASSETS_DIR / "config" / "map_config.json",
        "android_map_asset": ANDROID_ASSETS_DIR / "maps" / str(config["campus_map_file"]),
    }
    if android_index is not None and android_labels is not None:
        outputs["android_recognition_index_source"] = android_index
        outputs["android_recognition_labels_source"] = android_labels
        outputs["android_recognition_index"] = ANDROID_ASSETS_DIR / "ml" / RECOGNITION_ANDROID_INDEX_FILENAME
        outputs["android_recognition_labels"] = ANDROID_ASSETS_DIR / "ml" / RECOGNITION_ANDROID_LABELS_FILENAME
    return outputs


def write_processed_outputs(
    data: dict[str, list[dict[str, str]]], config: dict[str, Any]
) -> None:
    PROCESSED_DIR.mkdir(parents=True, exist_ok=True)
    _write_json(PROCESSED_DIR / "checkpoints.json", data["checkpoints"])
    _write_json(PROCESSED_DIR / "places.json", data["places"])
    _write_json(PROCESSED_DIR / "place_checkpoints.json", _place_checkpoint_rows(data["places"]))
    _write_json(PROCESSED_DIR / "edges.json", data["edges"])
    _write_json(PROCESSED_DIR / "crowd_rules.json", data["crowd_rules"])
    _write_json(PROCESSED_DIR / "outdoor_panos.json", data["outdoor_panos"])
    _write_json(PROCESSED_DIR / "recognition_refs.json", data["recognition_refs"])
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


def publish_android_assets(
    seed_db: Path,
    config: dict[str, Any],
    recognition_index: Path | None,
    recognition_labels: Path | None,
) -> None:
    (ANDROID_ASSETS_DIR / "seed").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "config").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "maps").mkdir(parents=True, exist_ok=True)
    (ANDROID_ASSETS_DIR / "ml").mkdir(parents=True, exist_ok=True)
    android_pano_dir = ANDROID_ASSETS_DIR / "pano" / "outdoor"
    if android_pano_dir.exists():
        shutil.rmtree(android_pano_dir)
    android_pano_dir.mkdir(parents=True, exist_ok=True)

    shutil.copyfile(seed_db, ANDROID_ASSETS_DIR / "seed" / "campus_seed.db")
    _write_json(ANDROID_ASSETS_DIR / "config" / "map_config.json", config)
    map_file = str(config["campus_map_file"])
    shutil.copyfile(RAW_DIR / "maps" / map_file, ANDROID_ASSETS_DIR / "maps" / map_file)

    for image_path in (RAW_DIR / "outdoor_panos").glob("*.jp*g"):
        shutil.copyfile(image_path, android_pano_dir / image_path.name)
    if recognition_index is not None and recognition_labels is not None:
        shutil.copyfile(recognition_index, ANDROID_ASSETS_DIR / "ml" / RECOGNITION_ANDROID_INDEX_FILENAME)
        shutil.copyfile(recognition_labels, ANDROID_ASSETS_DIR / "ml" / RECOGNITION_ANDROID_LABELS_FILENAME)


def publish_backend_assets(
    seed_db: Path,
    config: dict[str, Any],
    recognition_index: Path,
    recognition_metadata: Path,
) -> None:
    BACKEND_DATA_DIR.mkdir(parents=True, exist_ok=True)
    backend_recognition_dir = BACKEND_DATA_DIR / "recognition"
    backend_pano_dir = BACKEND_DATA_DIR / "pano" / "outdoor"
    if backend_pano_dir.exists():
        shutil.rmtree(backend_pano_dir)
    backend_pano_dir.mkdir(parents=True, exist_ok=True)
    backend_recognition_dir.mkdir(parents=True, exist_ok=True)
    (BACKEND_DATA_DIR / "maps").mkdir(parents=True, exist_ok=True)

    shutil.copyfile(seed_db, BACKEND_DATA_DIR / "campus_seed.db")
    shutil.copyfile(recognition_index, backend_recognition_dir / RECOGNITION_INDEX_FILENAME)
    shutil.copyfile(recognition_metadata, backend_recognition_dir / RECOGNITION_METADATA_FILENAME)
    _write_json(BACKEND_DATA_DIR / "map_config.json", config)
    map_file = str(config["campus_map_file"])
    shutil.copyfile(RAW_DIR / "maps" / map_file, BACKEND_DATA_DIR / "maps" / map_file)
    for image_path in (RAW_DIR / "outdoor_panos").glob("*.jp*g"):
        shutil.copyfile(image_path, backend_pano_dir / image_path.name)


def build_recognition_refs(data: dict[str, list[dict[str, str]]]) -> list[dict[str, str]]:
    pano_by_checkpoint = {
        row["checkpoint_id"]: row["image_file"]
        for row in data["outdoor_panos"]
        if row.get("checkpoint_id") and row.get("image_file")
    }
    rows: list[dict[str, str]] = []
    for checkpoint in data["checkpoints"]:
        checkpoint_id = checkpoint["checkpoint_id"]
        image_file = pano_by_checkpoint.get(checkpoint_id, "")
        reference_count = "27" if image_file else "0"
        rows.append(
            {
                "checkpoint_id": checkpoint_id,
                "image_file": image_file,
                "embedding_file": RECOGNITION_INDEX_FILENAME if image_file else "",
                "supported": "1" if image_file else "0",
                "reference_count": reference_count,
            }
        )
    return rows


def build_recognition_index(
    data: dict[str, list[dict[str, str]]],
    output_dir: Path = RECOGNITION_DIR,
) -> tuple[Path, Path, Path | None, Path | None]:
    recognition_dir = Path(output_dir)
    recognition_dir.mkdir(parents=True, exist_ok=True)
    encoder = encoder_from_environment()
    embeddings: list[np.ndarray] = []
    checkpoint_ids: list[str] = []
    image_files: list[str] = []
    view_indexes: list[int] = []
    metadata_rows: list[dict[str, Any]] = []

    for row in data["recognition_refs"]:
        checkpoint_id = row["checkpoint_id"]
        image_file = row["image_file"]
        supported = row["supported"] == "1"
        reference_count = 0
        if supported:
            image_path = RAW_DIR / "outdoor_panos" / image_file
            image = decode_image(image_path.read_bytes())
            view_embeddings = encoder.embeddings_for_views(reference_views(image))
            reference_count = int(view_embeddings.shape[0])
            for index, embedding in enumerate(view_embeddings):
                embeddings.append(embedding)
                checkpoint_ids.append(checkpoint_id)
                image_files.append(image_file)
                view_indexes.append(index)
        metadata_rows.append(
            {
                "checkpoint_id": checkpoint_id,
                "image_file": image_file or None,
                "embedding_file": RECOGNITION_INDEX_FILENAME if supported else None,
                "supported": supported,
                "reference_count": reference_count,
            }
        )

    matrix = (
        np.vstack(embeddings).astype(np.float32)
        if embeddings
        else np.empty((0, encoder.embedding_dimension), dtype=np.float32)
    )
    index_path = recognition_dir / RECOGNITION_INDEX_FILENAME
    android_index_path = (
        recognition_dir / RECOGNITION_ANDROID_INDEX_FILENAME
        if encoder.name == HANDCRAFTED_ENCODER_NAME
        else None
    )
    android_labels_path = (
        recognition_dir / RECOGNITION_ANDROID_LABELS_FILENAME
        if encoder.name == HANDCRAFTED_ENCODER_NAME
        else None
    )
    metadata_path = recognition_dir / RECOGNITION_METADATA_FILENAME
    np.savez_compressed(
        index_path,
        embeddings=matrix,
        checkpoint_ids=np.array(checkpoint_ids),
        image_files=np.array(image_files),
        view_indexes=np.array(view_indexes, dtype=np.int16),
        model_version=np.array(encoder.model_version),
    )
    if android_index_path is not None and android_labels_path is not None:
        with android_index_path.open("wb") as file:
            np.array([matrix.shape[0], matrix.shape[1]], dtype="<i4").tofile(file)
            matrix.astype("<f4").tofile(file)
        with android_labels_path.open("w", encoding="utf-8", newline="") as file:
            writer = csv.writer(file)
            writer.writerow(["checkpoint_id", "image_file"])
            writer.writerows(zip(checkpoint_ids, image_files))
    _write_json(
        metadata_path,
        {
            "model_version": encoder.model_version,
            "encoder": encoder.name,
            "embedding_count": int(matrix.shape[0]),
            "embedding_dimension": int(matrix.shape[1]),
            "confidence_floor": encoder.confidence_floor,
            "confidence_span": encoder.confidence_span,
            "supported_checkpoint_count": sum(1 for row in metadata_rows if row["supported"]),
            "checkpoint_count": len(metadata_rows),
            "rows": metadata_rows,
        },
    )
    return index_path, metadata_path, android_index_path, android_labels_path


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


def _place_checkpoint_rows(places: list[dict[str, str]]) -> list[dict[str, str]]:
    rows: list[dict[str, str]] = []
    seen: set[tuple[str, str]] = set()
    for place in places:
        checkpoint_ids = _checkpoint_ids_for_place(place)
        for index, checkpoint_id in enumerate(checkpoint_ids):
            key = (place["place_id"], checkpoint_id)
            if key in seen:
                continue
            seen.add(key)
            rows.append(
                {
                    "place_id": place["place_id"],
                    "checkpoint_id": checkpoint_id,
                    "entrance_name": (
                        "Primary entrance" if index == 0 else f"Entrance {index + 1}"
                    ),
                    "is_primary": "1" if index == 0 else "0",
                }
            )
    return rows


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


def checkpoint_raw_pixel_distance(
    from_checkpoint: dict[str, str], to_checkpoint: dict[str, str]
) -> float:
    return math.hypot(
        _checkpoint_raw_x(to_checkpoint) - _checkpoint_raw_x(from_checkpoint),
        _checkpoint_raw_y(to_checkpoint) - _checkpoint_raw_y(from_checkpoint),
    )


def _load_workbook_rows(workbook_path: Path) -> dict[str, list[dict[str, str]]]:
    import pandas as pd

    excel = pd.ExcelFile(workbook_path)
    rows_by_sheet: dict[str, list[dict[str, str]]] = {}
    for sheet_name in excel.sheet_names:
        dataframe = pd.read_excel(workbook_path, sheet_name=sheet_name, dtype=str).fillna("")
        dataframe.columns = [str(column).strip() for column in dataframe.columns]
        dataframe = dataframe.loc[
            dataframe.apply(lambda row: any(str(value).strip() for value in row), axis=1)
        ]
        rows_by_sheet[sheet_name.strip().lower()] = [
            {
                str(key).strip(): _clean_cell(value)
                for key, value in row.items()
                if not str(key).startswith("Unnamed:")
            }
            for row in dataframe.to_dict(orient="records")
        ]
    return rows_by_sheet


def _extract_pdf_markers(map_pdf_path: Path) -> dict[str, dict[str, float]]:
    from PIL import Image
    from pypdf import PdfReader
    from pypdf.generic import ContentStream

    reader = PdfReader(str(map_pdf_path))
    page = reader.pages[0]
    page_width = float(page.mediabox.width)
    page_height = float(page.mediabox.height)
    image_width, image_height = _largest_pdf_image_size(page)
    content = ContentStream(page.get_contents(), reader)

    current_color: tuple[float, ...] | None = None
    last_rect: tuple[float, float, float, float] | None = None
    last_marker: tuple[float, float, float, float] | None = None
    markers: dict[str, dict[str, float]] = {}

    for operands, operator in content.operations:
        op = operator.decode("latin1") if isinstance(operator, bytes) else str(operator)
        if op == "sc":
            current_color = tuple(float(value) for value in operands)
        elif op == "re":
            last_rect = tuple(float(value) for value in operands)
        elif op == "f" and current_color and last_rect and _is_marker_green(current_color):
            x_coord, y_coord, width, height = last_rect
            last_marker = (x_coord + width / 2.0, y_coord + height / 2.0, width, height)
        elif op == "Tj" and last_marker:
            label = str(operands[0]).strip()
            if re.fullmatch(r"\d{2}", label):
                pdf_x, pdf_y, marker_width, marker_height = last_marker
                raw_x = pdf_x / page_width * image_width
                raw_y = (page_height - pdf_y) / page_height * image_height
                markers[label] = {
                    "raw_map_x": raw_x,
                    "raw_map_y": raw_y,
                    "marker_width_px": marker_width / page_width * image_width,
                    "marker_height_px": marker_height / page_height * image_height,
                }
                last_marker = None

    if len(markers) < 1:
        raise ValidationError("No numbered checkpoint markers were found in campus_map.pdf")

    expected_labels = {f"{number:02d}" for number in range(1, 76)}
    missing = sorted(expected_labels - set(markers))
    if missing:
        raise ValidationError(
            "campus_map.pdf is missing checkpoint marker labels: " + ", ".join(missing)
        )
    return markers


def _extract_pdf_map_image(map_pdf_path: Path) -> tuple[Any, int, int]:
    from PIL import Image
    from pypdf import PdfReader

    reader = PdfReader(str(map_pdf_path))
    page = reader.pages[0]
    candidates: list[Any] = []
    for image in page.images:
        pil_image = Image.open(io.BytesIO(image.data)).convert("RGB")
        candidates.append(pil_image.copy())
    if not candidates:
        raise ValidationError("campus_map.pdf does not contain an embedded campus map image")
    map_image = max(candidates, key=lambda image: image.width * image.height)
    return map_image, map_image.width, map_image.height


def _largest_pdf_image_size(page: Any) -> tuple[int, int]:
    from PIL import Image

    sizes: list[tuple[int, int]] = []
    for image in page.images:
        pil_image = Image.open(io.BytesIO(image.data))
        sizes.append((pil_image.width, pil_image.height))
    if not sizes:
        raise ValidationError("campus_map.pdf does not contain an embedded image")
    return max(sizes, key=lambda size: size[0] * size[1])


def _draw_markers_on_map(map_image: Any, markers: dict[str, dict[str, float]]) -> None:
    from PIL import ImageDraw, ImageFont

    draw = ImageDraw.Draw(map_image)
    font_size = 12
    font = None
    for font_path in [
        Path("C:/Windows/Fonts/arial.ttf"),
        Path("C:/Windows/Fonts/calibri.ttf"),
    ]:
        if font_path.exists():
            font = ImageFont.truetype(str(font_path), font_size)
            break
    if font is None:
        font = ImageFont.load_default()

    for label, marker in sorted(markers.items()):
        x_coord = marker["raw_map_x"]
        y_coord = marker["raw_map_y"]
        radius = max(11, int(round(marker["marker_width_px"] * 0.65)))
        bbox = [
            x_coord - radius,
            y_coord - radius,
            x_coord + radius,
            y_coord + radius,
        ]
        draw.ellipse(bbox, fill=(192, 235, 117), outline=(44, 92, 32), width=2)
        text_bbox = draw.textbbox((0, 0), label, font=font)
        text_width = text_bbox[2] - text_bbox[0]
        text_height = text_bbox[3] - text_bbox[1]
        draw.text(
            (x_coord - text_width / 2.0, y_coord - text_height / 2.0 - 1),
            label,
            fill=(0, 0, 0),
            font=font,
        )


def _is_marker_green(color: tuple[float, ...]) -> bool:
    if len(color) < 3:
        return False
    red, green, blue = color[:3]
    return green > 0.8 and red > 0.5 and blue < 0.6


def _config_from_excel(rows: list[dict[str, str]]) -> dict[str, Any]:
    config: dict[str, Any] = {
        "campus_map_file": "campus_map.png",
        "campus_map_width_px": 0,
        "campus_map_height_px": 0,
        "meters_per_pixel": 0.2,
    }
    for row in rows:
        key = row.get("key", "").strip()
        if not key:
            continue
        value = row.get("value", "").strip()
        if key in {"campus_map_width_px", "campus_map_height_px"}:
            config[key] = int(float(value)) if value else 0
        elif key == "meters_per_pixel":
            config[key] = float(value) if value else 0.2
        else:
            config[key] = value
    return config


def _build_checkpoint_rows(
    rows: list[dict[str, str]],
    markers: dict[str, dict[str, float]],
) -> list[dict[str, str]]:
    by_id: dict[str, dict[str, str]] = {}
    for row in rows:
        checkpoint_id = _normalize_checkpoint_id(row.get("checkpoint_id", ""))
        if not checkpoint_id:
            continue
        by_id[checkpoint_id] = row

    origin = markers["01"]
    output: list[dict[str, str]] = []
    for label in sorted(markers):
        checkpoint_id = f"OUT_CP{int(label):03d}"
        source = by_id.get(checkpoint_id, {})
        marker = markers[label]
        raw_x = marker["raw_map_x"]
        raw_y = marker["raw_map_y"]
        checkpoint_type = source.get("checkpoint_type", "").strip() or "outdoor_path"
        output.append(
            {
                "checkpoint_id": checkpoint_id,
                "checkpoint_name": _strip_outer_quotes(source.get("checkpoint_name", ""))
                or f"Checkpoint {int(label)}",
                "checkpoint_type": _normalize_type(checkpoint_type),
                "x_coord": _format_number(raw_x - origin["raw_map_x"]),
                "y_coord": _format_number(raw_y - origin["raw_map_y"]),
                "raw_map_x": _format_number(raw_x),
                "raw_map_y": _format_number(raw_y),
                "latitude": source.get("latitude", ""),
                "longitude": source.get("longitude", ""),
                "description": source.get("description", ""),
                "orientation": _normalize_type(source.get("orientation", "")),
            }
        )
    return output


def _build_place_rows(
    rows: list[dict[str, str]],
    checkpoint_ids: set[str],
) -> list[dict[str, str]]:
    output: list[dict[str, str]] = []
    for row in rows:
        place_id = _normalize_place_id(row.get("place_id", ""))
        place_name = _strip_outer_quotes(row.get("place_name", ""))
        checkpoints = [
            checkpoint_id
            for checkpoint_id in _split_checkpoint_ids(row.get("checkpoint_id", ""))
            if checkpoint_id in checkpoint_ids
        ]
        if not place_id or not place_name or not checkpoints:
            continue
        output.append(
            {
                "place_id": place_id,
                "place_name": place_name,
                "place_type": _normalize_type(row.get("place_type", "facility")),
                "checkpoint_id": checkpoints[0],
                "checkpoint_ids": ",".join(checkpoints),
                "description": row.get("description", ""),
                "keywords": row.get("keywords", ""),
            }
        )
    return output


def _build_edge_rows(
    rows: list[dict[str, str]],
    checkpoints: dict[str, dict[str, str]],
    meters_per_pixel: float,
) -> list[dict[str, str]]:
    output: list[dict[str, str]] = []
    for row in rows:
        from_id = _normalize_checkpoint_id(row.get("from_checkpoint_id", ""))
        to_id = _normalize_checkpoint_id(row.get("to_checkpoint_id", ""))
        if not from_id or not to_id or from_id not in checkpoints or to_id not in checkpoints:
            continue
        edge_id = row.get("edge_id", "").strip() or f"E_{from_id}_{to_id}"
        distance = _safe_float(row.get("distance_meters"))
        if distance <= 0:
            distance = checkpoint_pixel_distance(checkpoints[from_id], checkpoints[to_id])
            distance *= meters_per_pixel
        output.append(
            {
                "edge_id": edge_id,
                "from_checkpoint_id": from_id,
                "to_checkpoint_id": to_id,
                "distance_meters": _format_number(distance, precision=9),
                "is_bidirectional": "1" if str(row.get("is_bidirectional", "1")).strip() != "0" else "0",
                "edge_type": _normalize_type(row.get("edge_type") or "outdoor_walk"),
            }
        )
    return output


def _build_pano_rows(
    rows: list[dict[str, str]],
    checkpoint_ids: set[str],
    pano_file_by_checkpoint: dict[str, str],
) -> list[dict[str, str]]:
    by_checkpoint = {
        _normalize_checkpoint_id(row.get("checkpoint_id", "")): row
        for row in rows
        if _normalize_checkpoint_id(row.get("checkpoint_id", ""))
    }
    output: list[dict[str, str]] = []
    for checkpoint_id, filename in sorted(pano_file_by_checkpoint.items()):
        if checkpoint_id not in checkpoint_ids:
            continue
        row = by_checkpoint.get(checkpoint_id, {})
        output.append(
            {
                "pano_id": row.get("pano_id", "").strip() or f"PANO_{checkpoint_id}",
                "checkpoint_id": checkpoint_id,
                "image_file": filename,
                "thumbnail_file": "",
                "orientation": _normalize_type(row.get("orientation", "")),
                "description": row.get("description", ""),
            }
        )
    return output


def _build_crowd_rows(
    rows: list[dict[str, str]],
    checkpoint_ids: set[str],
) -> list[dict[str, str]]:
    output: list[dict[str, str]] = []
    seen: dict[str, int] = defaultdict(int)
    for row in rows:
        checkpoint_id = _normalize_checkpoint_id(row.get("checkpoint_id", ""))
        if checkpoint_id not in checkpoint_ids:
            continue
        base_id = _normalize_identifier(row.get("crowd_rule_id", "")) or "CROWD_RULE"
        seen[base_id] += 1
        output.append(
            {
                "crowd_rule_id": f"{base_id}_{checkpoint_id}_{seen[base_id]:02d}",
                "checkpoint_id": checkpoint_id,
                "day_type": _normalize_type(row.get("day_type", "weekday")),
                "start_time": _normalize_time(row.get("start_time", "")),
                "end_time": _normalize_time(row.get("end_time", "")),
                "crowd_level": _normalize_type(row.get("crowd_level", "medium")),
                "penalty_cost": _format_number(_safe_float(row.get("penalty_cost"))),
                "description": row.get("description", ""),
            }
        )
    return output


def _build_search_alias_rows(
    supplied_rows: list[dict[str, str]],
    places: list[dict[str, str]],
    checkpoints: list[dict[str, str]],
) -> list[dict[str, str]]:
    if supplied_rows:
        output: list[dict[str, str]] = []
        for row in supplied_rows:
            place_id = _normalize_place_id(row.get("place_id", ""))
            alias_text = _normalize_alias_text(row.get("alias_text", ""))
            if place_id and alias_text:
                output.append(
                    {
                        "alias_id": _normalize_identifier(row.get("alias_id", ""))
                        or f"ALIAS_{place_id}_{len(output) + 1:03d}",
                        "place_id": place_id,
                        "alias_text": alias_text,
                        "alias_type": row.get("alias_type", "common_name") or "common_name",
                    }
                )
        if output:
            return output

    checkpoint_by_id = {row["checkpoint_id"]: row for row in checkpoints}
    rows: list[dict[str, str]] = []
    global_seen: set[tuple[str, str]] = set()
    for place in places:
        aliases = _generated_aliases_for_place(place, checkpoint_by_id)
        place_number = _place_number(place["place_id"])
        alias_index = 1
        for alias_type, alias_text in aliases:
            normalized = _normalize_alias_text(alias_text)
            key = (place["place_id"], normalized)
            if not normalized or key in global_seen:
                continue
            global_seen.add(key)
            rows.append(
                {
                    "alias_id": f"ALIAS_PL_{place_number}_{alias_index:03d}",
                    "place_id": place["place_id"],
                    "alias_text": normalized,
                    "alias_type": alias_type,
                }
            )
            alias_index += 1
    return rows


def _generated_aliases_for_place(
    place: dict[str, str],
    checkpoint_by_id: dict[str, dict[str, str]],
) -> list[tuple[str, str]]:
    name = _strip_outer_quotes(place["place_name"])
    aliases: list[tuple[str, str]] = [
        ("common_name", name),
        ("common_name", _normalize_type(name)),
        ("common_name", place.get("place_type", "")),
    ]
    if place.get("keywords"):
        for token in re.split(r"[,;| ]+", place["keywords"]):
            aliases.append(("common_name", token))
    words = re.findall(r"[A-Za-z0-9]+", name)
    if len(words) > 1:
        aliases.append(("abbreviation", "".join(word[0] for word in words if word).lower()))
    for token in re.findall(r"\(([^)]+)\)", name):
        aliases.append(("common_name", token))
    for token in re.split(r"[,/-]+", name):
        aliases.append(("common_name", token))

    lower = name.lower()
    if "cafeteria" in lower:
        aliases.extend([("common_name", "canteen"), ("common_name", "food")])
    if "annapurna" in lower or "mess" in lower:
        aliases.extend([("common_name", "mess"), ("common_name", "food hall")])
    if "open air" in lower or "theater" in lower or "theatre" in lower:
        aliases.extend([("abbreviation", "oat"), ("alternate_spelling", "open air theatre")])
    if "jaypee business school" in lower:
        aliases.extend([("abbreviation", "jbs"), ("common_name", "business school")])
    if "lrc" in lower:
        aliases.extend([("common_name", "library"), ("abbreviation", "lrc")])
    if "abb" in lower:
        aliases.extend([("common_name", "academic block"), ("abbreviation", "abb")])
        match = re.search(r"abb[- ]?(\d+)", lower)
        if match:
            aliases.extend(
                [
                    ("abbreviation", f"abb {match.group(1)}"),
                    ("abbreviation", f"abb{match.group(1)}"),
                ]
            )
    hostel_match = re.search(r"\bh\s*(\d+)\b", lower)
    if hostel_match:
        aliases.append(("abbreviation", f"h{hostel_match.group(1)}"))
    if "a2z" in lower:
        aliases.append(("alternate_spelling", "a to z"))
    if "g.b." in lower or "gb " in lower:
        aliases.append(("abbreviation", "gb pant"))

    for checkpoint_id in _checkpoint_ids_for_place(place):
        checkpoint = checkpoint_by_id.get(checkpoint_id)
        if checkpoint:
            aliases.append(("common_name", checkpoint["checkpoint_name"]))
            aliases.append(("common_name", checkpoint["checkpoint_type"]))
    return aliases


def _extract_available_panos(panos_zip_path: Path) -> dict[str, str]:
    target_dir = RAW_DIR / "outdoor_panos"
    target_dir.mkdir(parents=True, exist_ok=True)
    by_checkpoint: dict[str, str] = {}
    with zipfile.ZipFile(panos_zip_path) as archive:
        for info in archive.infolist():
            if info.is_dir():
                continue
            source_name = Path(info.filename).name
            if not source_name.lower().endswith((".jpg", ".jpeg")):
                continue
            checkpoint_id = _normalize_checkpoint_id(Path(source_name).stem)
            if not checkpoint_id:
                continue
            output_name = f"{checkpoint_id}.jpg"
            output_path = target_dir / output_name
            with archive.open(info) as source, output_path.open("wb") as target:
                shutil.copyfileobj(source, target)
            by_checkpoint[checkpoint_id] = output_name
    return by_checkpoint


def _reset_raw_data_dirs() -> None:
    for path in [
        RAW_DIR / "outdoor_panos",
        RAW_DIR / "maps",
    ]:
        if path.exists():
            shutil.rmtree(path)
        path.mkdir(parents=True, exist_ok=True)


def _write_csv(path: Path, rows: list[dict[str, str]]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if not rows:
        fieldnames: list[str] = []
    else:
        fieldnames = list(rows[0])
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

def _checkpoint_ids_for_place(row: dict[str, str]) -> list[str]:
    value = row.get("checkpoint_ids") or row.get("checkpoint_id", "")
    return _split_checkpoint_ids(value)


def _split_checkpoint_ids(value: str) -> list[str]:
    cleaned = _strip_outer_quotes(value)
    checkpoint_ids: list[str] = []
    for item in cleaned.split(","):
        checkpoint_id = _normalize_checkpoint_id(item)
        if checkpoint_id:
            checkpoint_ids.append(checkpoint_id)
    return checkpoint_ids


def _normalize_checkpoint_id(value: Any) -> str:
    text = _strip_outer_quotes(str(value or "")).strip().upper().replace(" ", "")
    match = re.fullmatch(r"OUT_CP(\d{1,3})", text)
    if not match:
        return ""
    return f"OUT_CP{int(match.group(1)):03d}"


def _normalize_place_id(value: Any) -> str:
    text = _strip_outer_quotes(str(value or "")).strip().upper().replace("-", "_")
    return text


def _normalize_identifier(value: Any) -> str:
    text = _strip_outer_quotes(str(value or "")).strip().upper()
    text = re.sub(r"[^A-Z0-9]+", "_", text).strip("_")
    return text


def _normalize_type(value: Any) -> str:
    text = _strip_outer_quotes(str(value or "")).strip().lower()
    text = text.replace("-", "_").replace(" ", "_")
    text = re.sub(r"[^a-z0-9_]+", "", text)
    text = re.sub(r"_+", "_", text).strip("_")
    return text


def _normalize_alias_text(value: Any) -> str:
    text = _strip_outer_quotes(str(value or "")).lower()
    text = re.sub(r"[^a-z0-9]+", " ", text)
    return re.sub(r"\s+", " ", text).strip()


def _strip_outer_quotes(value: Any) -> str:
    text = str(value or "").strip()
    while len(text) >= 2 and text[0] == text[-1] and text[0] in {"'", '"'}:
        text = text[1:-1].strip()
    return text


def _clean_cell(value: Any) -> str:
    text = "" if value is None else str(value).strip()
    if text.endswith(".0") and re.fullmatch(r"-?\d+\.0", text):
        return text[:-2]
    return text


def _normalized_jpg_name(filename: str) -> str:
    path = Path(filename)
    return path.with_suffix(".jpg").name


def _normalize_time(value: Any) -> str:
    text = str(value or "").strip()
    match = re.match(r"^(\d{1,2}):(\d{2})(?::\d{2})?$", text)
    if not match:
        return text
    return f"{int(match.group(1)):02d}:{match.group(2)}"


def _safe_float(value: Any) -> float:
    try:
        return float(str(value).strip())
    except (TypeError, ValueError):
        return 0.0


def _format_number(value: float, precision: int = 3) -> str:
    text = f"{value:.{precision}f}"
    return text.rstrip("0").rstrip(".") if "." in text else text


def _checkpoint_raw_x(row: dict[str, str]) -> float:
    return float(row.get("raw_map_x") or row["x_coord"])


def _checkpoint_raw_y(row: dict[str, str]) -> float:
    return float(row.get("raw_map_y") or row["y_coord"])


def _place_number(place_id: str) -> str:
    match = re.search(r"(\d+)$", place_id)
    return f"{int(match.group(1)):03d}" if match else _normalize_identifier(place_id)


def main_validate(scope: str) -> None:
    validate_or_raise(scope)
    print(f"Validation OK: {scope}")


def _insert_rows(connection: sqlite3.Connection, data: dict[str, list[dict[str, str]]]) -> None:
    recognition_refs = data.get("recognition_refs")
    if recognition_refs is None:
        recognition_refs = build_recognition_refs(data)

    connection.executemany(
        """
        INSERT INTO checkpoints (
            checkpoint_id, checkpoint_name, checkpoint_type, x_coord, y_coord,
            raw_map_x, raw_map_y, latitude, longitude, description, orientation
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        [
            (
                row["checkpoint_id"],
                row["checkpoint_name"],
                row["checkpoint_type"],
                float(row["x_coord"]),
                float(row["y_coord"]),
                float(row.get("raw_map_x") or row["x_coord"]),
                float(row.get("raw_map_y") or row["y_coord"]),
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
        INSERT INTO place_checkpoints (
            place_id, checkpoint_id, entrance_name, is_primary
        ) VALUES (?, ?, ?, ?)
        """,
        [
            (
                row["place_id"],
                row["checkpoint_id"],
                row["entrance_name"],
                int(row["is_primary"]),
            )
            for row in _place_checkpoint_rows(data["places"])
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
            checkpoint_id, image_file, embedding_file, supported, reference_count
        ) VALUES (?, ?, ?, ?, ?)
        """,
        [
            (
                row["checkpoint_id"],
                _blank_to_none(row.get("image_file")),
                _blank_to_none(row.get("embedding_file")),
                int(row["supported"]),
                int(row["reference_count"]),
            )
            for row in recognition_refs
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
    raw_map_x REAL,
    raw_map_y REAL,
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

CREATE TABLE place_checkpoints (
    place_id TEXT NOT NULL,
    checkpoint_id TEXT NOT NULL,
    entrance_name TEXT,
    is_primary INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (place_id, checkpoint_id),
    FOREIGN KEY (place_id) REFERENCES places(place_id),
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
    checkpoint_id TEXT PRIMARY KEY,
    image_file TEXT,
    embedding_file TEXT,
    supported INTEGER NOT NULL DEFAULT 0,
    reference_count INTEGER NOT NULL DEFAULT 0,
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

CREATE INDEX idx_place_checkpoints_place
ON place_checkpoints(place_id);

CREATE INDEX idx_place_checkpoints_checkpoint
ON place_checkpoints(checkpoint_id);

CREATE INDEX idx_crowd_checkpoint_time
ON crowd_rules(checkpoint_id, day_type, start_time, end_time);

CREATE INDEX idx_panos_checkpoint
ON outdoor_panos(checkpoint_id);

CREATE INDEX idx_recognition_supported
ON recognition_refs(supported);

CREATE INDEX idx_alias_text
ON search_aliases(alias_text);
"""
