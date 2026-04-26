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
        row["image_url"] = f"/assets/pano/outdoor/{row['image_file']}"
        thumbnail = row.get("thumbnail_file")
        row["thumbnail_url"] = f"/assets/pano/outdoor/{thumbnail}" if thumbnail else None
        return row
