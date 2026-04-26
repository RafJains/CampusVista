from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping


@dataclass(frozen=True)
class DirectedEdge:
    edge_id: str
    from_checkpoint_id: str
    to_checkpoint_id: str
    distance_meters: float
    edge_type: str | None
    reverse_of_bidirectional_edge: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "edge_id": self.edge_id,
            "from_checkpoint_id": self.from_checkpoint_id,
            "to_checkpoint_id": self.to_checkpoint_id,
            "distance_meters": self.distance_meters,
            "edge_type": self.edge_type,
            "reverse_of_bidirectional_edge": self.reverse_of_bidirectional_edge,
        }


@dataclass
class Graph:
    checkpoints: dict[str, dict[str, Any]]
    adjacency: dict[str, list[DirectedEdge]]

    def contains(self, checkpoint_id: str) -> bool:
        return checkpoint_id in self.checkpoints

    def outgoing(self, checkpoint_id: str) -> list[DirectedEdge]:
        return self.adjacency.get(checkpoint_id, [])


def build_graph(
    checkpoints: list[Mapping[str, Any]],
    edges: list[Mapping[str, Any]],
) -> Graph:
    checkpoint_map = {
        str(row["checkpoint_id"]): dict(row)
        for row in checkpoints
    }
    adjacency: dict[str, list[DirectedEdge]] = {
        checkpoint_id: []
        for checkpoint_id in checkpoint_map
    }

    for row in edges:
        from_id = str(row["from_checkpoint_id"])
        to_id = str(row["to_checkpoint_id"])
        if from_id not in checkpoint_map or to_id not in checkpoint_map:
            continue

        edge = DirectedEdge(
            edge_id=str(row["edge_id"]),
            from_checkpoint_id=from_id,
            to_checkpoint_id=to_id,
            distance_meters=float(row["distance_meters"]),
            edge_type=row.get("edge_type"),
            reverse_of_bidirectional_edge=False,
        )
        adjacency[from_id].append(edge)

        if int(row.get("is_bidirectional", 0)) == 1:
            adjacency[to_id].append(
                DirectedEdge(
                    edge_id=str(row["edge_id"]),
                    from_checkpoint_id=to_id,
                    to_checkpoint_id=from_id,
                    distance_meters=float(row["distance_meters"]),
                    edge_type=row.get("edge_type"),
                    reverse_of_bidirectional_edge=True,
                )
            )

    return Graph(checkpoint_map, adjacency)
