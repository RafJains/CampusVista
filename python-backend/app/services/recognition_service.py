from __future__ import annotations

from pathlib import Path
from typing import Any

from app import db


FALLBACK_OPTIONS = [
    "select_on_map",
    "search_outdoor_location",
    "try_camera_again",
]


class RecognitionService:
    def __init__(self, db_path: Path | str | None = None) -> None:
        self.db_path = db_path

    def get_reference_labels(self) -> list[dict[str, Any]]:
        return db.fetch_all(
            """
            SELECT checkpoint_id, label_name, model_label_index, confidence_threshold
            FROM recognition_refs
            ORDER BY model_label_index
            """,
            db_path=self.db_path,
        )

    def recognize(self, payload: dict[str, Any]) -> dict[str, Any]:
        candidates = self.get_reference_labels()
        label_name = payload.get("label_name")
        label_index = payload.get("model_label_index")
        confidence = payload.get("confidence")

        matched = self._match_candidate(candidates, label_name, label_index)
        if matched is None:
            return {
                "available": False,
                "status": "model_not_loaded",
                "message": (
                    "Recognition API is ready, but no Python ML model is loaded yet. "
                    "Use a fallback option or send a model label to exercise mapping."
                ),
                "checkpoint_id": None,
                "confidence": confidence,
                "candidates": candidates,
                "fallback_options": FALLBACK_OPTIONS,
                "raw": payload,
            }

        threshold = float(matched["confidence_threshold"])
        confidence_value = float(confidence or 0.0)
        candidate = dict(matched)
        candidate["confidence"] = confidence_value
        if confidence_value >= threshold:
            return {
                "available": True,
                "status": "accepted",
                "message": "Outdoor checkpoint recognized from supplied model label.",
                "checkpoint_id": matched["checkpoint_id"],
                "confidence": confidence_value,
                "candidates": [candidate],
                "fallback_options": FALLBACK_OPTIONS,
                "raw": payload,
            }

        return {
            "available": True,
            "status": "low_confidence",
            "message": "Low-confidence outdoor match. Ask the user to confirm or choose fallback.",
            "checkpoint_id": None,
            "confidence": confidence_value,
            "candidates": [candidate],
            "fallback_options": FALLBACK_OPTIONS,
            "raw": payload,
        }

    @staticmethod
    def _match_candidate(
        candidates: list[dict[str, Any]],
        label_name: str | None,
        label_index: int | None,
    ) -> dict[str, Any] | None:
        for candidate in candidates:
            if label_index is not None and int(candidate["model_label_index"]) == int(label_index):
                return candidate
            if label_name and candidate["label_name"] == label_name:
                return candidate
        return None
