from __future__ import annotations

import json
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

import numpy as np

from app import db
PYTHON_COMMON_DIR = Path(__file__).resolve().parents[3] / "python-common"
if str(PYTHON_COMMON_DIR) not in sys.path:
    sys.path.insert(0, str(PYTHON_COMMON_DIR))

from campusvista_recognition import (  # noqa: E402
    MODEL_VERSION,
    ImageDecodeError,
    decode_image,
    extract_embedding,
    encoder_for_model_version,
    query_views,
    reference_views,
)


MAX_IMAGE_BYTES = 8 * 1024 * 1024
SUPPORTED_CONTENT_TYPES = {"image/jpeg", "image/jpg", "image/png", "image/webp"}
TOP_MATCH_LIMIT = 5
CANDIDATE_REFERENCE_LIMIT = 600
HYBRID_CONFIDENCE_FLOOR = 0.78
HYBRID_CONFIDENCE_SPAN = 0.16
MIN_RECOGNIZED_CONFIDENCE_PERCENT = 75.0
MIN_RECOGNIZED_MARGIN_PERCENT = 8.0
MIN_RECOGNIZED_SUPPORTING_VIEWS = 2
RECOGNITION_DIR = db.DATA_DIR / "recognition"
INDEX_PATH = RECOGNITION_DIR / "recognition_index.npz"
METADATA_PATH = RECOGNITION_DIR / "recognition_metadata.json"
PANO_DIR = db.DATA_DIR / "pano" / "outdoor"


@dataclass(frozen=True)
class RecognitionIndex:
    embeddings: np.ndarray
    checkpoint_ids: np.ndarray
    image_files: np.ndarray
    view_indexes: np.ndarray
    model_version: str
    embedding_dimension: int
    metadata: dict[str, Any]

    @property
    def confidence_floor(self) -> float:
        return float(self.metadata.get("confidence_floor", 0.90))

    @property
    def confidence_span(self) -> float:
        return float(self.metadata.get("confidence_span", 0.14))


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
        self._handcrafted_reference_cache: dict[str, np.ndarray] = {}
        self._nearby_checkpoints_cache: dict[str, set[str]] | None = None

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

        encoder = encoder_for_model_version(index.model_version)
        views = query_views(image)
        query_embeddings = encoder.embeddings_for_views(views)
        similarities = query_embeddings @ index.embeddings.T
        best_by_reference = similarities.max(axis=0)
        ranked_reference_indexes = np.argsort(best_by_reference)[::-1][:CANDIDATE_REFERENCE_LIMIT]
        scores = self._aggregate_scores(similarities, best_by_reference, ranked_reference_indexes, index)
        self._add_layout_scores(scores, views)
        matches = self._rank_matches(scores, max(1, min(limit, TOP_MATCH_LIMIT)), index)
        if not matches:
            return self._empty_response("No supported visual reference matched the photo.")

        top = matches[0]
        second_percent = matches[1]["confidence_percent"] if len(matches) > 1 else 0.0
        margin = top["confidence_percent"] - second_percent
        recognized = (
            top["confidence_percent"] >= MIN_RECOGNIZED_CONFIDENCE_PERCENT
            and margin >= MIN_RECOGNIZED_MARGIN_PERCENT
            and top["supporting_views"] >= MIN_RECOGNIZED_SUPPORTING_VIEWS
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
                view_indexes=np.array([], dtype=np.int16),
                model_version=MODEL_VERSION,
                embedding_dimension=1,
                metadata={},
            )
            return self._index

        payload = np.load(self.index_path, allow_pickle=False)
        metadata = json.loads(self.metadata_path.read_text(encoding="utf-8"))
        embeddings = payload["embeddings"].astype(np.float32)
        checkpoint_ids = payload["checkpoint_ids"].astype(str)
        image_files = payload["image_files"].astype(str)
        view_indexes = (
            payload["view_indexes"].astype(np.int16)
            if "view_indexes" in payload.files
            else np.zeros(checkpoint_ids.shape[0], dtype=np.int16)
        )
        model_version = str(payload["model_version"].item())
        encoder = encoder_for_model_version(model_version)
        self._validate_index_payload(embeddings, checkpoint_ids, image_files, view_indexes, encoder.embedding_dimension)
        self._validate_metadata(metadata, model_version, encoder.embedding_dimension)

        self._index = RecognitionIndex(
            embeddings=embeddings,
            checkpoint_ids=checkpoint_ids,
            image_files=image_files,
            view_indexes=view_indexes,
            model_version=model_version,
            embedding_dimension=encoder.embedding_dimension,
            metadata=metadata,
        )
        return self._index

    @staticmethod
    def _validate_index_payload(
        embeddings: np.ndarray,
        checkpoint_ids: np.ndarray,
        image_files: np.ndarray,
        view_indexes: np.ndarray,
        embedding_dimension: int,
    ) -> None:
        if embeddings.ndim != 2 or embeddings.shape[1] != embedding_dimension:
            raise ValueError("Recognition index has an unexpected embedding shape.")
        if embeddings.shape[0] != checkpoint_ids.shape[0] or embeddings.shape[0] != image_files.shape[0]:
            raise ValueError("Recognition index labels do not match embedding rows.")
        if embeddings.shape[0] != view_indexes.shape[0]:
            raise ValueError("Recognition index view labels do not match embedding rows.")

    @staticmethod
    def _validate_metadata(
        metadata: dict[str, Any],
        model_version: str,
        embedding_dimension: int,
    ) -> None:
        if metadata.get("model_version", model_version) != model_version:
            raise ValueError("Recognition metadata model version does not match the index.")
        metadata_dimension = metadata.get("embedding_dimension", embedding_dimension)
        if int(metadata_dimension) != embedding_dimension:
            raise ValueError("Recognition metadata embedding dimension does not match the index.")

    def _aggregate_scores(
        self,
        similarities: np.ndarray,
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
                    "reference_scores": [],
                    "image_file": str(index.image_files[reference_index]),
                },
            )
            entry["reference_scores"].append(score)
        for checkpoint_id, entry in scores.items():
            reference_indexes = np.flatnonzero(index.checkpoint_ids == checkpoint_id)
            if reference_indexes.size == 0:
                entry["query_scores"] = []
                entry["max_score"] = 0.0
                continue
            checkpoint_similarities = similarities[:, reference_indexes]
            query_scores = checkpoint_similarities.max(axis=1).astype(float)
            entry["query_scores"] = query_scores.tolist()
            entry["max_score"] = float(checkpoint_similarities.max())
        return scores

    def _rank_matches(
        self,
        scores: dict[str, dict[str, Any]],
        limit: int,
        index: RecognitionIndex,
    ) -> list[dict[str, Any]]:
        checkpoint_names = self._checkpoint_names()
        ranked: list[tuple[float, str, dict[str, Any]]] = []
        for checkpoint_id, entry in scores.items():
            reference_scores = sorted(entry["reference_scores"], reverse=True)[:7]
            query_scores = sorted(entry.get("query_scores", []), reverse=True)
            if not reference_scores or not query_scores:
                continue
            reference_mean = float(np.mean(reference_scores[:5]))
            query_mean = float(np.mean(query_scores[:3]))
            max_score = max(float(entry.get("max_score", 0.0)), reference_scores[0], query_scores[0])
            layout_score = float(entry.get("layout_score", 0.0))
            support_threshold = max_score - 0.035
            query_support = sum(1 for score in query_scores if score >= support_threshold)
            support_bonus = min(query_support, 5) * 0.003
            low_support_penalty = 0.015 if query_support < 2 else 0.0
            aggregate = (
                reference_mean * 0.45
                + query_mean * 0.30
                + max_score * 0.10
                + layout_score * 0.15
                + support_bonus
                - low_support_penalty
            )
            entry["raw_score"] = aggregate
            entry["query_support"] = query_support
            ranked.append((aggregate, checkpoint_id, entry))

        ranked = self._apply_neighborhood_smoothing(ranked)
        ranked.sort(reverse=True, key=lambda item: item[0])
        matches: list[dict[str, Any]] = []
        for rank, (score, checkpoint_id, entry) in enumerate(ranked[:limit], start=1):
            percent = self._confidence_percent(score, HYBRID_CONFIDENCE_FLOOR, HYBRID_CONFIDENCE_SPAN)
            image_file = entry["image_file"]
            matches.append(
                {
                    "checkpoint_id": checkpoint_id,
                    "checkpoint_name": checkpoint_names.get(checkpoint_id, checkpoint_id),
                    "confidence_percent": percent,
                    "rank": rank,
                    "reference_image_url": f"/assets/pano/outdoor/{image_file}",
                    "supporting_views": min(int(entry.get("query_support", 0)), 5),
                }
            )
        return matches

    def _add_layout_scores(
        self,
        scores: dict[str, dict[str, Any]],
        query_view_images: list[Any],
    ) -> None:
        query_embeddings = np.vstack(
            [extract_embedding(view) for view in query_view_images]
        ).astype(np.float32)
        for entry in scores.values():
            image_file = str(entry.get("image_file", ""))
            reference_embeddings = self._handcrafted_reference_embeddings(image_file)
            if reference_embeddings.size == 0:
                entry["layout_score"] = 0.0
                continue
            similarities = query_embeddings @ reference_embeddings.T
            query_scores = np.sort(similarities.max(axis=1))[::-1]
            reference_scores = np.sort(similarities.max(axis=0))[::-1]
            layout_score = (
                float(np.mean(query_scores[: min(3, query_scores.size)])) * 0.65
                + float(np.mean(reference_scores[: min(5, reference_scores.size)])) * 0.35
            )
            entry["layout_score"] = layout_score

    def _handcrafted_reference_embeddings(self, image_file: str) -> np.ndarray:
        if not image_file:
            return np.empty((0, 1), dtype=np.float32)
        cached = self._handcrafted_reference_cache.get(image_file)
        if cached is not None:
            return cached
        image_path = PANO_DIR / image_file
        if not image_path.exists():
            embeddings = np.empty((0, 1), dtype=np.float32)
        else:
            image = decode_image(image_path.read_bytes())
            embeddings = np.vstack(
                [extract_embedding(view) for view in reference_views(image)]
            ).astype(np.float32)
        self._handcrafted_reference_cache[image_file] = embeddings
        return embeddings

    def _apply_neighborhood_smoothing(
        self,
        ranked: list[tuple[float, str, dict[str, Any]]],
    ) -> list[tuple[float, str, dict[str, Any]]]:
        if not ranked:
            return ranked
        raw_by_checkpoint = {checkpoint_id: score for score, checkpoint_id, _ in ranked}
        nearby = self._nearby_checkpoints()
        smoothed: list[tuple[float, str, dict[str, Any]]] = []
        for score, checkpoint_id, entry in ranked:
            neighbor_scores = [
                raw_by_checkpoint[neighbor]
                for neighbor in nearby.get(checkpoint_id, set())
                if neighbor in raw_by_checkpoint
            ]
            if neighbor_scores:
                score = score * 0.84 + max(neighbor_scores) * 0.16
            smoothed.append((score, checkpoint_id, entry))
        return smoothed

    def _nearby_checkpoints(self) -> dict[str, set[str]]:
        if self._nearby_checkpoints_cache is not None:
            return self._nearby_checkpoints_cache
        rows = db.fetch_all(
            """
            SELECT from_checkpoint_id, to_checkpoint_id
            FROM edges
            """,
            db_path=self.db_path,
        )
        direct: dict[str, set[str]] = {}
        for row in rows:
            start = str(row["from_checkpoint_id"])
            end = str(row["to_checkpoint_id"])
            direct.setdefault(start, set()).add(end)
            direct.setdefault(end, set()).add(start)

        nearby: dict[str, set[str]] = {}
        for checkpoint_id, neighbors in direct.items():
            expanded = set(neighbors)
            for neighbor in neighbors:
                expanded.update(direct.get(neighbor, set()))
            expanded.discard(checkpoint_id)
            nearby[checkpoint_id] = expanded
        self._nearby_checkpoints_cache = nearby
        return nearby

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
    def _confidence_percent(score: float, floor: float, span: float) -> float:
        percent = (score - floor) / max(span, 1e-6) * 99.0
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
