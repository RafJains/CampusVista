from __future__ import annotations

import math
import re
from typing import Mapping, Any


WALKING_METERS_PER_MINUTE = 72.0


def normalize_text(value: str | None) -> str:
    if not value:
        return ""
    normalized = re.sub(r"[^a-z0-9]+", " ", value.lower()).strip()
    return re.sub(r"\s+", " ", normalized)


def pixel_distance(a: Mapping[str, Any], b: Mapping[str, Any]) -> float:
    dx = float(a["x_coord"]) - float(b["x_coord"])
    dy = float(a["y_coord"]) - float(b["y_coord"])
    return math.hypot(dx, dy)


def coordinate_distance_pixels(
    x: float,
    y: float,
    checkpoint: Mapping[str, Any],
) -> float:
    checkpoint_x = checkpoint.get("raw_map_x")
    checkpoint_y = checkpoint.get("raw_map_y")
    if checkpoint_x is None or checkpoint_y is None:
        checkpoint_x = checkpoint["x_coord"]
        checkpoint_y = checkpoint["y_coord"]
    return math.hypot(float(checkpoint_x) - x, float(checkpoint_y) - y)


def estimate_walk_time_label(distance_meters: float) -> str:
    minutes = max(1, int(math.ceil(distance_meters / WALKING_METERS_PER_MINUTE)))
    return f"{minutes} min"
