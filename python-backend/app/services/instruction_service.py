from __future__ import annotations

import math
from typing import Any, Mapping

from app.utils.graph_utils import DirectedEdge


STRAIGHT_THRESHOLD_DEGREES = 30.0
TURN_BACK_THRESHOLD_DEGREES = 150.0


class InstructionService:
    def build_instructions(
        self,
        checkpoints: list[Mapping[str, Any]],
        edges: list[DirectedEdge],
        destination_name: str | None = None,
    ) -> list[str]:
        if not checkpoints:
            return []

        destination = checkpoints[-1]
        display_name = destination_name or str(destination["checkpoint_name"])
        if len(checkpoints) == 1:
            return [self.arrival_instruction(display_name)]

        instructions: list[str] = []
        for index, edge in enumerate(edges):
            previous = checkpoints[index - 1] if index > 0 else None
            current = checkpoints[index]
            next_checkpoint = checkpoints[index + 1]

            if previous is None:
                instructions.append(
                    f"Start walking toward {next_checkpoint['checkpoint_name']}."
                )
            else:
                direction = self.direction_text(previous, current, next_checkpoint)
                target = self.wording_target(next_checkpoint, edge)
                instructions.append(f"{direction} toward {target}.")

        instructions.append(self.arrival_instruction(display_name))
        return instructions

    @staticmethod
    def direction_text(
        previous: Mapping[str, Any],
        current: Mapping[str, Any],
        next_checkpoint: Mapping[str, Any],
    ) -> str:
        incoming_x = float(current["x_coord"]) - float(previous["x_coord"])
        incoming_y = float(current["y_coord"]) - float(previous["y_coord"])
        outgoing_x = float(next_checkpoint["x_coord"]) - float(current["x_coord"])
        outgoing_y = float(next_checkpoint["y_coord"]) - float(current["y_coord"])

        cross = incoming_x * outgoing_y - incoming_y * outgoing_x
        dot = incoming_x * outgoing_x + incoming_y * outgoing_y
        angle = math.degrees(math.atan2(cross, dot))

        if -STRAIGHT_THRESHOLD_DEGREES <= angle <= STRAIGHT_THRESHOLD_DEGREES:
            return "Go straight"
        if angle >= TURN_BACK_THRESHOLD_DEGREES or angle <= -TURN_BACK_THRESHOLD_DEGREES:
            return "Turn back"
        if angle > STRAIGHT_THRESHOLD_DEGREES:
            return "Turn right"
        return "Turn left"

    @staticmethod
    def wording_target(checkpoint: Mapping[str, Any], edge: DirectedEdge | None) -> str:
        checkpoint_type = str(checkpoint.get("checkpoint_type") or "").lower()
        name = str(checkpoint["checkpoint_name"])
        if checkpoint_type == "building_entry":
            if "entrance" in name.lower():
                return name
            return f"{name} entrance"
        if checkpoint_type in {"gate", "facility_entry", "parking", "landmark", "junction"}:
            return name
        edge_type = (edge.edge_type if edge else "") or ""
        if edge_type.lower() in {"outdoor_walk", "entry_transition"}:
            return name
        return name

    @staticmethod
    def arrival_instruction(destination_name: str) -> str:
        return f"You have arrived at {destination_name}."
