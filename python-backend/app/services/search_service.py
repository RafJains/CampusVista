from __future__ import annotations

from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

from app import db
from app.utils.distance_utils import normalize_text


class SearchService:
    def __init__(self, db_path: Path | str | None = None) -> None:
        self.db_path = db_path
        self._places: list[dict[str, Any]] | None = None
        self._aliases: dict[str, list[str]] | None = None

    def get_places(self) -> list[dict[str, Any]]:
        if self._places is None:
            self._places = db.fetch_all(
                "SELECT * FROM places ORDER BY place_name",
                db_path=self.db_path,
            )
        return self._places

    def get_place(self, place_id: str) -> dict[str, Any] | None:
        return db.fetch_one(
            "SELECT * FROM places WHERE place_id = ?",
            (place_id,),
            self.db_path,
        )

    def get_place_checkpoints(self, place_id: str) -> list[str]:
        rows = db.fetch_all(
            """
            SELECT checkpoint_id
            FROM place_checkpoints
            WHERE place_id = ?
            ORDER BY is_primary DESC, checkpoint_id
            """,
            (place_id,),
            self.db_path,
        )
        if rows:
            return [str(row["checkpoint_id"]) for row in rows]
        place = self.get_place(place_id)
        return [str(place["checkpoint_id"])] if place else []

    def search(
        self,
        query: str,
        place_type: str | None = None,
        limit: int = 10,
    ) -> list[dict[str, Any]]:
        query_norm = normalize_text(query)
        if not query_norm:
            return self._empty_query_results(place_type, limit)

        places = self.get_places()
        aliases = self._aliases_by_place()
        best_by_place: dict[str, dict[str, Any]] = {}

        for place in places:
            if place_type and place["place_type"] != place_type:
                continue
            candidates = [
                ("place_name", place["place_name"]),
                ("keywords", place.get("keywords") or ""),
            ]
            candidates.extend(
                ("alias", alias)
                for alias in aliases.get(str(place["place_id"]), [])
            )
            for source, text in candidates:
                score = self._score(query_norm, normalize_text(text))
                if score <= 0:
                    continue
                current = best_by_place.get(str(place["place_id"]))
                if current is None or score > current["match_score"]:
                    result = dict(place)
                    result.update(
                        {
                            "match_score": round(score, 3),
                            "match_source": source,
                            "matched_text": text,
                        }
                    )
                    best_by_place[str(place["place_id"])] = result

        return sorted(
            best_by_place.values(),
            key=lambda row: (-float(row["match_score"]), row["place_name"]),
        )[:limit]

    def _aliases_by_place(self) -> dict[str, list[str]]:
        if self._aliases is not None:
            return self._aliases
        rows = db.fetch_all(
            "SELECT place_id, alias_text FROM search_aliases",
            db_path=self.db_path,
        )
        aliases: dict[str, list[str]] = {}
        for row in rows:
            aliases.setdefault(str(row["place_id"]), []).append(str(row["alias_text"]))
        self._aliases = aliases
        return self._aliases

    def _empty_query_results(
        self,
        place_type: str | None,
        limit: int,
    ) -> list[dict[str, Any]]:
        results: list[dict[str, Any]] = []
        for place in self.get_places():
            if place_type and place["place_type"] != place_type:
                continue
            result = dict(place)
            result.update(
                {
                    "match_score": 0.0,
                    "match_source": "browse",
                    "matched_text": place["place_name"],
                }
            )
            results.append(result)
        return results[:limit]

    @staticmethod
    def _score(query_norm: str, candidate_norm: str) -> float:
        if not candidate_norm:
            return 0.0
        if query_norm == candidate_norm:
            return 100.0
        if candidate_norm.startswith(query_norm):
            return 92.0
        if query_norm in candidate_norm:
            return 82.0

        query_tokens = set(query_norm.split())
        candidate_tokens = set(candidate_norm.split())
        overlap = query_tokens & candidate_tokens
        token_score = 0.0
        if overlap:
            token_score = 62.0 + (20.0 * len(overlap) / max(len(query_tokens), 1))

        ratio = SequenceMatcher(None, query_norm, candidate_norm).ratio()
        fuzzy_score = ratio * 78.0 if ratio >= 0.54 else 0.0
        return max(token_score, fuzzy_score)
