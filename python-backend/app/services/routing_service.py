from __future__ import annotations

import heapq
import json
from pathlib import Path
from typing import Any, Callable, Mapping

from app import db
from app.models import RouteRequest
from app.services.crowd_service import CrowdService, normalize_route_mode, parse_now
from app.services.instruction_service import InstructionService
from app.services.pano_service import PanoService
from app.services.search_service import SearchService
from app.utils.distance_utils import (
    coordinate_distance_pixels,
    estimate_walk_time_label,
    pixel_distance,
)
from app.utils.graph_utils import DirectedEdge, Graph, build_graph


class RoutingService:
    def __init__(self, db_path: Path | str | None = None) -> None:
        self.db_path = db_path
        self.crowd_service = CrowdService(db_path)
        self.instruction_service = InstructionService()
        self.pano_service = PanoService(db_path)
        self.search_service = SearchService(db_path)
        self._checkpoints: list[dict[str, Any]] | None = None
        self._edges: list[dict[str, Any]] | None = None
        self._map_config: dict[str, Any] | None = None
        self._graph: Graph | None = None

    def compute_route(self, request: RouteRequest | Mapping[str, Any]) -> dict[str, Any]:
        payload = (
            request.model_dump()
            if hasattr(request, "model_dump")
            else dict(request)
        )
        graph = self.build_graph()
        config = self.get_map_config()
        meters_per_pixel = float(config["meters_per_pixel"])
        route_mode = normalize_route_mode(payload.get("route_mode"))
        now = parse_now(payload.get("now_iso"))
        start_id, start_warning = self.resolve_start(payload)
        destination_id, destination_name, destination_warning = self.resolve_destination(
            payload,
            graph,
            start_id,
        )

        warnings = [warning for warning in [start_warning, destination_warning] if warning]
        if not start_id or not destination_id:
            raise ValueError("A start and destination checkpoint are required.")
        if not graph.contains(start_id):
            raise ValueError(f"Unknown start checkpoint: {start_id}")
        if not graph.contains(destination_id):
            raise ValueError(f"Unknown destination checkpoint: {destination_id}")

        if start_id == destination_id:
            checkpoint = graph.checkpoints[start_id]
            instructions = self.instruction_service.build_instructions(
                [checkpoint],
                [],
                destination_name,
            )
            crowd_warnings = self.crowd_service.warnings_for_checkpoints([start_id], now)
            return self._route_response(
                route_found=True,
                algorithm="none",
                route_mode=route_mode,
                start_id=start_id,
                destination_id=destination_id,
                destination_name=destination_name,
                checkpoints=[checkpoint],
                edges=[],
                total_distance=0.0,
                total_cost=0.0,
                instructions=instructions,
                warnings=warnings + crowd_warnings,
            )

        edge_path, total_cost = self._shortest_path(
            graph,
            start_id,
            destination_id,
            lambda checkpoint_id: self._heuristic(
                graph,
                checkpoint_id,
                destination_id,
                meters_per_pixel,
            ),
        )
        algorithm = "astar"
        if edge_path is None:
            edge_path, total_cost = self._shortest_path(
                graph,
                start_id,
                destination_id,
                lambda checkpoint_id: 0.0,
            )
            algorithm = "dijkstra"

        if edge_path is None:
            return self._route_response(
                route_found=False,
                algorithm=algorithm,
                route_mode=route_mode,
                start_id=start_id,
                destination_id=destination_id,
                destination_name=destination_name,
                checkpoints=[],
                edges=[],
                total_distance=0.0,
                total_cost=0.0,
                instructions=[],
                warnings=warnings + ["No outdoor route found."],
            )

        checkpoints = self._checkpoints_from_edges(graph, start_id, edge_path)
        total_distance = sum(edge.distance_meters for edge in edge_path)
        instructions = self.instruction_service.build_instructions(
            checkpoints,
            edge_path,
            destination_name,
        )
        validation_warnings = self.validate_route(checkpoints, edge_path)
        crowd_warnings = self.crowd_service.warnings_for_checkpoints(
            [str(checkpoint["checkpoint_id"]) for checkpoint in checkpoints],
            now,
        )
        return self._route_response(
            route_found=True,
            algorithm=algorithm,
            route_mode=route_mode,
            start_id=start_id,
            destination_id=destination_id,
            destination_name=destination_name,
            checkpoints=checkpoints,
            edges=edge_path,
            total_distance=total_distance,
            total_cost=total_cost,
            instructions=instructions,
            warnings=warnings + validation_warnings + crowd_warnings,
        )

    def get_checkpoints(self) -> list[dict[str, Any]]:
        if self._checkpoints is None:
            self._checkpoints = db.fetch_all(
                "SELECT * FROM checkpoints ORDER BY checkpoint_id",
                db_path=self.db_path,
            )
        return self._checkpoints

    def get_edges(self) -> list[dict[str, Any]]:
        if self._edges is None:
            self._edges = db.fetch_all(
                "SELECT * FROM edges ORDER BY edge_id",
                db_path=self.db_path,
            )
        return self._edges

    def get_checkpoint(self, checkpoint_id: str) -> dict[str, Any] | None:
        return db.fetch_one(
            "SELECT * FROM checkpoints WHERE checkpoint_id = ?",
            (checkpoint_id,),
            self.db_path,
        )

    def get_map_config(self) -> dict[str, Any]:
        if self._map_config is None:
            config_path = db.DEFAULT_MAP_CONFIG_PATH
            if config_path.exists():
                self._map_config = json.loads(config_path.read_text(encoding="utf-8"))
            else:
                self._map_config = {
                    "campus_map_file": "campus_map.png",
                    "campus_map_width_px": 3000,
                    "campus_map_height_px": 1800,
                    "meters_per_pixel": 0.2,
                }
        return self._map_config

    def build_graph(self) -> Graph:
        if self._graph is None:
            self._graph = build_graph(self.get_checkpoints(), self.get_edges())
        return self._graph

    def nearest_checkpoint(self, x: float, y: float) -> dict[str, Any]:
        config = self.get_map_config()
        meters_per_pixel = float(config["meters_per_pixel"])
        checkpoints = self.get_checkpoints()
        if not checkpoints:
            raise ValueError("No checkpoints are available.")
        best = min(
            checkpoints,
            key=lambda checkpoint: coordinate_distance_pixels(x, y, checkpoint),
        )
        distance_pixels = coordinate_distance_pixels(x, y, best)
        return {
            "checkpoint": best,
            "distance_pixels": distance_pixels,
            "distance_meters": distance_pixels * meters_per_pixel,
        }

    def resolve_start(
        self,
        payload: Mapping[str, Any],
    ) -> tuple[str | None, str | None]:
        checkpoint_id = payload.get("start_checkpoint_id")
        if checkpoint_id:
            return str(checkpoint_id), None
        if payload.get("start_x") is not None and payload.get("start_y") is not None:
            nearest = self.nearest_checkpoint(float(payload["start_x"]), float(payload["start_y"]))
            checkpoint = nearest["checkpoint"]
            return (
                checkpoint["checkpoint_id"],
                (
                    "Start coordinate snapped to "
                    f"{checkpoint['checkpoint_name']} "
                    f"({nearest['distance_meters']:.1f} m away)."
                ),
            )
        return None, None

    def resolve_destination(
        self,
        payload: Mapping[str, Any],
        graph: Graph,
        start_id: str | None = None,
    ) -> tuple[str | None, str | None, str | None]:
        checkpoint_id = payload.get("destination_checkpoint_id")
        if checkpoint_id:
            checkpoint = graph.checkpoints.get(str(checkpoint_id))
            return (
                str(checkpoint_id),
                checkpoint["checkpoint_name"] if checkpoint else str(checkpoint_id),
                None,
            )

        place_id = payload.get("destination_place_id")
        if place_id:
            place = self.search_service.get_place(str(place_id))
            if place:
                destination_id = self._best_destination_checkpoint(
                    graph,
                    start_id,
                    self.search_service.get_place_checkpoints(str(place_id)),
                )
                return destination_id or place["checkpoint_id"], place["place_name"], None

        query = payload.get("destination_query")
        if query:
            results = self.search_service.search(str(query), limit=1)
            if results:
                place = results[0]
                destination_id = self._best_destination_checkpoint(
                    graph,
                    start_id,
                    self.search_service.get_place_checkpoints(str(place["place_id"])),
                )
                return (
                    destination_id or place["checkpoint_id"],
                    place["place_name"],
                    f"Destination query matched {place['place_name']}.",
                )

        if payload.get("destination_x") is not None and payload.get("destination_y") is not None:
            nearest = self.nearest_checkpoint(
                float(payload["destination_x"]),
                float(payload["destination_y"]),
            )
            checkpoint = nearest["checkpoint"]
            return (
                checkpoint["checkpoint_id"],
                checkpoint["checkpoint_name"],
                (
                    "Destination coordinate snapped to "
                    f"{checkpoint['checkpoint_name']} "
                    f"({nearest['distance_meters']:.1f} m away)."
                ),
            )

        return None, None, None

    @staticmethod
    def _best_destination_checkpoint(
        graph: Graph,
        start_id: str | None,
        candidates: list[str],
    ) -> str | None:
        valid = [checkpoint_id for checkpoint_id in candidates if graph.contains(checkpoint_id)]
        if not valid:
            return None
        if not start_id or not graph.contains(start_id):
            return valid[0]
        start = graph.checkpoints[start_id]
        return min(valid, key=lambda checkpoint_id: pixel_distance(start, graph.checkpoints[checkpoint_id]))

    def validate_route(
        self,
        checkpoints: list[Mapping[str, Any]],
        edges: list[DirectedEdge],
    ) -> list[str]:
        warnings: list[str] = []
        if len(checkpoints) != len(edges) + 1:
            warnings.append("Route validation warning: checkpoint and edge counts do not align.")
        for index, edge in enumerate(edges):
            if str(checkpoints[index]["checkpoint_id"]) != edge.from_checkpoint_id:
                warnings.append("Route validation warning: edge starts at an unexpected checkpoint.")
            if str(checkpoints[index + 1]["checkpoint_id"]) != edge.to_checkpoint_id:
                warnings.append("Route validation warning: edge ends at an unexpected checkpoint.")
        return warnings

    def _shortest_path(
        self,
        graph: Graph,
        start_id: str,
        destination_id: str,
        heuristic_cost: Callable[[str], float],
    ) -> tuple[list[DirectedEdge], float] | tuple[None, float]:
        open_set: list[tuple[float, float, str]] = []
        heapq.heappush(open_set, (heuristic_cost(start_id), 0.0, start_id))
        best_cost = {start_id: 0.0}
        came_from: dict[str, DirectedEdge] = {}

        while open_set:
            _, current_cost, current_id = heapq.heappop(open_set)
            if current_cost > best_cost.get(current_id, float("inf")):
                continue
            if current_id == destination_id:
                return self._reconstruct_edges(start_id, destination_id, came_from), current_cost

            for edge in graph.outgoing(current_id):
                edge_cost = self.crowd_service.calculate_edge_cost(edge)
                next_cost = current_cost + edge_cost
                if next_cost < best_cost.get(edge.to_checkpoint_id, float("inf")):
                    best_cost[edge.to_checkpoint_id] = next_cost
                    came_from[edge.to_checkpoint_id] = edge
                    priority = next_cost + heuristic_cost(edge.to_checkpoint_id)
                    heapq.heappush(open_set, (priority, next_cost, edge.to_checkpoint_id))
        return None, 0.0

    @staticmethod
    def _heuristic(
        graph: Graph,
        from_id: str,
        to_id: str,
        meters_per_pixel: float,
    ) -> float:
        from_checkpoint = graph.checkpoints[from_id]
        to_checkpoint = graph.checkpoints[to_id]
        return pixel_distance(from_checkpoint, to_checkpoint) * meters_per_pixel

    @staticmethod
    def _reconstruct_edges(
        start_id: str,
        destination_id: str,
        came_from: dict[str, DirectedEdge],
    ) -> list[DirectedEdge]:
        edges: list[DirectedEdge] = []
        current_id = destination_id
        while current_id != start_id:
            edge = came_from[current_id]
            edges.append(edge)
            current_id = edge.from_checkpoint_id
        edges.reverse()
        return edges

    @staticmethod
    def _checkpoints_from_edges(
        graph: Graph,
        start_id: str,
        edges: list[DirectedEdge],
    ) -> list[dict[str, Any]]:
        checkpoints = [graph.checkpoints[start_id]]
        for edge in edges:
            checkpoints.append(graph.checkpoints[edge.to_checkpoint_id])
        return checkpoints

    def _route_response(
        self,
        route_found: bool,
        algorithm: str,
        route_mode: str,
        start_id: str | None,
        destination_id: str | None,
        destination_name: str | None,
        checkpoints: list[Mapping[str, Any]],
        edges: list[DirectedEdge],
        total_distance: float,
        total_cost: float,
        instructions: list[str],
        warnings: list[str],
    ) -> dict[str, Any]:
        return {
            "route_found": route_found,
            "algorithm": algorithm,
            "route_mode": route_mode,
            "start_checkpoint_id": start_id,
            "destination_checkpoint_id": destination_id,
            "destination_name": destination_name,
            "total_distance": round(total_distance, 3),
            "total_cost": round(total_cost, 3),
            "estimated_time": estimate_walk_time_label(total_distance),
            "checkpoint_ids": [str(row["checkpoint_id"]) for row in checkpoints],
            "checkpoints": [dict(row) for row in checkpoints],
            "edges": [edge.to_dict() for edge in edges],
            "panos": self.pano_service.get_panos_for_checkpoints(
                [str(row["checkpoint_id"]) for row in checkpoints]
            ),
            "instructions": instructions,
            "warnings": warnings,
        }
