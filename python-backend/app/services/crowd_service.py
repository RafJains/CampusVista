from __future__ import annotations

from datetime import datetime
from pathlib import Path
from typing import Any

from app import db
from app.utils.graph_utils import DirectedEdge


class CrowdService:
    def __init__(self, db_path: Path | str | None = None) -> None:
        self.db_path = db_path

    def get_active_rules_by_checkpoint(
        self,
        now: datetime | None = None,
    ) -> dict[str, list[dict[str, Any]]]:
        current = now or datetime.now()
        day_type = self.day_type(current)
        current_time = current.strftime("%H:%M")
        rows = db.fetch_all(
            """
            SELECT crowd_rules.*, checkpoints.checkpoint_name
            FROM crowd_rules
            LEFT JOIN checkpoints
              ON checkpoints.checkpoint_id = crowd_rules.checkpoint_id
            WHERE day_type = ?
              AND start_time <= ?
              AND end_time > ?
            """,
            (day_type, current_time, current_time),
            self.db_path,
        )

        rules: dict[str, list[dict[str, Any]]] = {}
        for row in rows:
            checkpoint_id = str(row["checkpoint_id"])
            rules.setdefault(checkpoint_id, []).append(dict(row))
        return rules

    def get_penalty_by_checkpoint(self, now: datetime | None = None) -> dict[str, float]:
        return {}

    def warnings_for_checkpoints(
        self,
        checkpoint_ids: list[str],
        now: datetime | None = None,
    ) -> list[str]:
        active_rules = self.get_active_rules_by_checkpoint(now)
        warnings: list[str] = []
        seen: set[tuple[str, str, str]] = set()
        for checkpoint_id in checkpoint_ids:
            for rule in active_rules.get(checkpoint_id, []):
                key = (
                    checkpoint_id,
                    str(rule["start_time"]),
                    str(rule["end_time"]),
                )
                if key in seen:
                    continue
                seen.add(key)
                name = rule.get("checkpoint_name") or checkpoint_id
                level = str(rule.get("crowd_level") or "busy").replace("_", " ")
                warnings.append(
                    f"{name} may be {level} between "
                    f"{rule['start_time']} and {rule['end_time']}."
                )
        return warnings

    def calculate_edge_cost(
        self,
        edge: DirectedEdge,
        route_mode: str,
        penalty_by_checkpoint: dict[str, float],
    ) -> float:
        return edge.distance_meters

    @staticmethod
    def day_type(value: datetime) -> str:
        return "weekend" if value.weekday() >= 5 else "weekday"


def normalize_route_mode(value: str | None) -> str:
    return "shortest"


def parse_now(value: str | None) -> datetime | None:
    if not value:
        return None
    normalized = value.replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(normalized)
    except ValueError:
        return None
