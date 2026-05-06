from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from app import db
from app.services.recognition_features import (
    EMBEDDING_DIMENSION,
    MODEL_VERSION,
    ImageDecodeError,
    decode_image,
    embeddings_for_views,
    query_views,
)


MAX_IMAGE_BYTES = 8 * 1024 * 1024
SUPPORTED_CONTENT_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"}
TOP_MATCH_LIMIT = 5
RECOGNITION_DIR = db.DATA_DIR / "recognition"
INDEX_PATH = RECOGNITION_DIR / "recognition_index.npz"
METADATA_PATH = RECOGNITION_DIR / "recognition_metadata.json"


@dataclass(frozen=True)
class RecognitionIndex:
    embeddings: np.ndarray
    checkpoint_ids: np.ndarray
    image_files: np.ndarray
    model_version: str
    metadata: dict[str, Any]


class RecognitionService:
    def __init__(
        self,
        db_path: Path | str | None = None,
        index_path: Path | str = INDEX_PATH,
        metadata_path: Path | str = METADATA_PATH,
    ) -> None:
        self.db_path = Path(db_path) if db_path is not None else db.get_db_path()
        self.index_path = Path(index_path)
        self.metadata_path = Path(metadata_path)
        self._index: RecognitionIndex | None = None
        self._checkpoint_names_cache: dict[str, str] | None = None

    def get_reference_labels(self) -> list[dict[str, Any]]:
        rows = db.fetch_all(
            """
            SELECT recognition_refs.*, checkpoints.checkpoint_name
            FROM recognition_refs
            JOIN checkpoints
              ON checkpoints.checkpoint_id = recognition_refs.checkpoint_id
            ORDER BY recognition_refs.checkpoint_id
            """,
            db_path=self.db_path,
        )
        for row in rows:
            row["supported"] = bool(row["supported"])
            row["reference_image_url"] = (
                f"/assets/pano/outdoor/{row['image_file']}" if row.get("image_file") else None
            )
        return rows

    def get_coverage_summary(self) -> dict[str, Any]:
        rows = self.get_reference_labels()
        supported = [row for row in rows if row["supported"]]
        index = self._load_index()
        return {
            "checkpoint_count": len(rows),
            "supported_checkpoint_count": len(supported),
            "unsupported_checkpoint_count": len(rows) - len(supported),
            "embedding_count": int(index.embeddings.shape[0]) if index.embeddings.size else 0,
            "model_version": index.model_version,
            "coverage_percent": round(len(supported) / max(1, len(rows)) * 100.0, 1),
        }

    def recognize(self, image_bytes: bytes, content_type: str, limit: int = TOP_MATCH_LIMIT) -> dict[str, Any]:
        self._validate_request(image_bytes, content_type)
        index = self._load_index()
        if index.embeddings.size == 0:
            return self._empty_response("Recognition references are not available.")

        try:
            image = decode_image(image_bytes)
        except ImageDecodeError:
            return self._empty_response("Image could not be decoded.")
        if self._is_low_information_image(image):
            return self._empty_response("Image is too blank, dark, or low-detail to recognize.")

        query_embeddings = embeddings_for_views(query_views(image))
        similarities = query_embeddings @ index.embeddings.T
        best_by_reference = similarities.max(axis=0)
        ranked_reference_indexes = np.argsort(best_by_reference)[::-1][:80]
        scores = self._aggregate_scores(best_by_reference, ranked_reference_indexes, index)
        matches = self._rank_matches(scores, max(1, min(limit, TOP_MATCH_LIMIT)))
        if not matches:
            return self._empty_response("No supported visual reference matched the photo.")

        top = matches[0]
        second_percent = matches[1]["confidence_percent"] if len(matches) > 1 else 0.0
        margin = top["confidence_percent"] - second_percent
        recognized = (
            top["confidence_percent"] >= 70.0
            and margin >= 6.0
            and top["supporting_views"] >= 2
        )
        message = (
            "Location recognized."
            if recognized
            else "No confident match. Showing the closest supported checkpoints."
        )
        return {
            "recognized": recognized,
            "matches": matches,
            "message": message,
            "model_version": index.model_version,
        }

    def _validate_request(self, image_bytes: bytes, content_type: str) -> None:
        normalized_type = content_type.split(";")[0].strip().lower()
        if normalized_type not in SUPPORTED_CONTENT_TYPES:
            raise ValueError("Unsupported image type. Upload JPEG, PNG, or WebP.")
        if not image_bytes:
            raise ValueError("Image file is empty.")
        if len(image_bytes) > MAX_IMAGE_BYTES:
            raise ValueError("Image file is too large.")

    def _load_index(self) -> RecognitionIndex:
        if self._index is not None:
            return self._index
        if not self.index_path.exists() or not self.metadata_path.exists():
            self._index = RecognitionIndex(
                embeddings=np.empty((0, 1), dtype=np.float32),
                checkpoint_ids=np.array([]),
                image_files=np.array([]),
                model_version=MODEL_VERSION,
                metadata={},
            )
            return self._index

        payload = np.load(self.index_path, allow_pickle=False)
        metadata = json.loads(self.metadata_path.read_text(encoding="utf-8"))
        embeddings = payload["embeddings"].astype(np.float32)
        checkpoint_ids = payload["checkpoint_ids"].astype(str)
        image_files = payload["image_files"].astype(str)
        model_version = str(payload["model_version"].item())
        self._validate_index_payload(embeddings, checkpoint_ids, image_files, model_version)

        self._index = RecognitionIndex(
            embeddings=embeddings,
            checkpoint_ids=checkpoint_ids,
            image_files=image_files,
            model_version=model_version,
            metadata=metadata,
        )
        return self._index

    @staticmethod
    def _validate_index_payload(
        embeddings: np.ndarray,
        checkpoint_ids: np.ndarray,
        image_files: np.ndarray,
        model_version: str,
    ) -> None:
        if embeddings.ndim != 2 or embeddings.shape[1] != EMBEDDING_DIMENSION:
            raise ValueError("Recognition index has an unexpected embedding shape.")
        if embeddings.shape[0] != checkpoint_ids.shape[0] or embeddings.shape[0] != image_files.shape[0]:
            raise ValueError("Recognition index labels do not match embedding rows.")
        if model_version != MODEL_VERSION:
            raise ValueError("Recognition index model version is not supported.")

    def _aggregate_scores(
        self,
        best_by_reference: np.ndarray,
        ranked_reference_indexes: np.ndarray,
        index: RecognitionIndex,
    ) -> dict[str, dict[str, Any]]:
        scores: dict[str, dict[str, Any]] = {}
        for reference_index in ranked_reference_indexes:
            checkpoint_id = str(index.checkpoint_ids[reference_index])
            score = float(best_by_reference[reference_index])
            entry = scores.setdefault(
                checkpoint_id,
                {
                    "scores": [],
                    "image_file": str(index.image_files[reference_index]),
                },
            )
            entry["scores"].append(score)
        return scores

    def _rank_matches(
        self,
        scores: dict[str, dict[str, Any]],
        limit: int,
    ) -> list[dict[str, Any]]:
        checkpoint_names = self._checkpoint_names()
        ranked: list[tuple[float, str, dict[str, Any]]] = []
        for checkpoint_id, entry in scores.items():
            top_scores = sorted(entry["scores"], reverse=True)[:5]
            if not top_scores:
                continue
            mean_score = float(np.mean(top_scores))
            vote_bonus = min(len(top_scores), 5) * 0.01
            aggregate = mean_score + vote_bonus
            ranked.append((aggregate, checkpoint_id, entry))

        ranked.sort(reverse=True, key=lambda item: item[0])
        matches: list[dict[str, Any]] = []
        for rank, (score, checkpoint_id, entry) in enumerate(ranked[:limit], start=1):
            percent = self._confidence_percent(score)
            image_file = entry["image_file"]
            matches.append(
                {
                    "checkpoint_id": checkpoint_id,
                    "checkpoint_name": checkpoint_names.get(checkpoint_id, checkpoint_id),
                    "confidence_percent": percent,
                    "rank": rank,
                    "reference_image_url": f"/assets/pano/outdoor/{image_file}",
                    "supporting_views": min(len(entry["scores"]), 5),
                }
            )
        return matches

    def _checkpoint_names(self) -> dict[str, str]:
        if self._checkpoint_names_cache is not None:
            return self._checkpoint_names_cache
        rows = db.fetch_all(
            "SELECT checkpoint_id, checkpoint_name FROM checkpoints",
            db_path=self.db_path,
        )
        self._checkpoint_names_cache = {
            str(row["checkpoint_id"]): str(row["checkpoint_name"]) for row in rows
        }
        return self._checkpoint_names_cache

    @staticmethod
    def _confidence_percent(score: float) -> float:
        percent = (score - 0.90) / 0.14 * 99.0
        return round(max(0.0, min(99.0, percent)), 1)

    @staticmethod
    def _is_low_information_image(image: Any) -> bool:
        resized = image.convert("L").resize((96, 96))
        pixels = np.asarray(resized, dtype=np.float32) / 255.0
        return float(pixels.std()) < 0.04

    @staticmethod
    def _empty_response(message: str) -> dict[str, Any]:
        return {
            "recognized": False,
            "matches": [],
            "message": message,
            "model_version": MODEL_VERSION,
        }
