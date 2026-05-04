from __future__ import annotations

from pathlib import Path
from typing import Any

from app import db


class PanoService:
    def __init__(self, db_path: Path | str | None = None) -> None:
        self.db_path = db_path

    def get_pano_for_checkpoint(self, checkpoint_id: str) -> dict[str, Any] | None:
        row = db.fetch_one(
            """
            SELECT *
            FROM outdoor_panos
            WHERE checkpoint_id = ?
            LIMIT 1
            """,
            (checkpoint_id,),
            self.db_path,
        )
        if row is None:
            return None
        return self._with_asset_urls(row)

    def get_panos_for_checkpoints(
        self,
        checkpoint_ids: list[str],
    ) -> list[dict[str, Any] | None]:
        if not checkpoint_ids:
            return []
        placeholders = ", ".join("?" for _ in checkpoint_ids)
        rows = db.fetch_all(
            f"""
            SELECT *
            FROM outdoor_panos
            WHERE checkpoint_id IN ({placeholders})
            """,
            checkpoint_ids,
            self.db_path,
        )
        by_checkpoint = {
            str(row["checkpoint_id"]): self._with_asset_urls(row)
            for row in rows
        }
        return [by_checkpoint.get(checkpoint_id) for checkpoint_id in checkpoint_ids]

    @staticmethod
    def _with_asset_urls(row: dict[str, Any]) -> dict[str, Any]:
        row["image_url"] = f"/assets/pano/outdoor/{row['image_file']}"
        thumbnail = row.get("thumbnail_file")
        row["thumbnail_url"] = f"/assets/pano/outdoor/{thumbnail}" if thumbnail else None
        return row
