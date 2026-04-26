from __future__ import annotations

from datetime import datetime
from pathlib import Path
from typing import Any

from app import db
from app.utils.graph_utils import DirectedEdge


AVOID_CROWDED_MODE = "avoid_crowded"


class CrowdService:
    def __init__(self, db_path: Path | str | None = None) -> None:
        self.db_path = db_path

    def get_penalty_by_checkpoint(self, now: datetime | None = None) -> dict[str, float]:
        current = now or datetime.now()
        day_type = self.day_type(current)
        current_time = current.strftime("%H:%M")
        rows = db.fetch_all(
            """
            SELECT checkpoint_id, penalty_cost
            FROM crowd_rules
            WHERE day_type = ?
              AND start_time <= ?
              AND end_time > ?
            """,
            (day_type, current_time, current_time),
            self.db_path,
        )

        penalties: dict[str, float] = {}
        for row in rows:
            checkpoint_id = str(row["checkpoint_id"])
            penalty = float(row["penalty_cost"])
            penalties[checkpoint_id] = max(penalties.get(checkpoint_id, 0.0), penalty)
        return penalties

    def calculate_edge_cost(
        self,
        edge: DirectedEdge,
        route_mode: str,
        penalty_by_checkpoint: dict[str, float],
    ) -> float:
        if normalize_route_mode(route_mode) != AVOID_CROWDED_MODE:
            return edge.distance_meters
        return edge.distance_meters + penalty_by_checkpoint.get(edge.to_checkpoint_id, 0.0)

    @staticmethod
    def day_type(value: datetime) -> str:
        return "weekend" if value.weekday() >= 5 else "weekday"


def normalize_route_mode(value: str | None) -> str:
    normalized = (value or "shortest").strip().lower()
    if normalized in {"avoid_crowded", "avoid_crowded_path", "avoid-crowded", "crowd"}:
        return AVOID_CROWDED_MODE
    return "shortest"


def parse_now(value: str | None) -> datetime | None:
    if not value:
        return None
    normalized = value.replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(normalized)
    except ValueError:
        return None
