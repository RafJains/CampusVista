package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class InstructionBuilder {
    private static final double STRAIGHT_THRESHOLD_DEGREES = 30.0;
    private static final double TURN_BACK_THRESHOLD_DEGREES = 150.0;

    public List<String> buildInstructions(
            List<Checkpoint> checkpointPath,
            List<Graph.DirectedEdge> edgePath,
            String destinationPlaceName
    ) {
        if (checkpointPath == null || checkpointPath.isEmpty()) {
            return Collections.emptyList();
        }

        if (checkpointPath.size() == 1) {
            return Collections.singletonList(arrivalInstruction(
                    displayDestinationName(destinationPlaceName, checkpointPath.get(0))
            ));
        }

        List<String> instructions = new ArrayList<>();
        Checkpoint destinationCheckpoint = checkpointPath.get(checkpointPath.size() - 1);
        String destinationName = displayDestinationName(destinationPlaceName, destinationCheckpoint);

        for (int i = 0; i < edgePath.size(); i++) {
            Checkpoint previous = i == 0 ? null : checkpointPath.get(i - 1);
            Checkpoint current = checkpointPath.get(i);
            Checkpoint next = checkpointPath.get(i + 1);
            Graph.DirectedEdge edge = edgePath.get(i);

            if (previous == null) {
                instructions.add("Start walking toward " + next.getCheckpointName() + ".");
            } else {
                String direction = directionText(previous, current, next);
                instructions.add(direction + " toward " + wordingTarget(next, edge) + ".");
            }
        }
        instructions.add(arrivalInstruction(destinationName));

        return Collections.unmodifiableList(instructions);
    }

    private static String directionText(
            Checkpoint previous,
            Checkpoint current,
            Checkpoint next
    ) {
        double incomingX = current.getXCoord() - previous.getXCoord();
        double incomingY = current.getYCoord() - previous.getYCoord();
        double outgoingX = next.getXCoord() - current.getXCoord();
        double outgoingY = next.getYCoord() - current.getYCoord();

        double cross = incomingX * outgoingY - incomingY * outgoingX;
        double dot = incomingX * outgoingX + incomingY * outgoingY;
        double angle = Math.toDegrees(Math.atan2(cross, dot));

        if (angle >= -STRAIGHT_THRESHOLD_DEGREES && angle <= STRAIGHT_THRESHOLD_DEGREES) {
            return "Go straight";
        }
        if (angle >= TURN_BACK_THRESHOLD_DEGREES || angle <= -TURN_BACK_THRESHOLD_DEGREES) {
            return "Turn back";
        }
        if (angle > STRAIGHT_THRESHOLD_DEGREES) {
            return "Turn right";
        }
        return "Turn left";
    }

    private static String wordingTarget(Checkpoint next, Graph.DirectedEdge edge) {
        String type = normalize(next.getCheckpointType());
        String name = next.getCheckpointName();

        if ("building_entry".equals(type)) {
            return name + " entrance";
        }
        if ("gate".equals(type)
                || "facility_entry".equals(type)
                || "parking".equals(type)) {
            return name;
        }
        if ("landmark".equals(type)
                || "junction".equals(type)
                || "outdoor_path".equals(type)) {
            return name;
        }

        String edgeType = edge == null ? null : normalize(edge.getEdgeType());
        if ("outdoor_walk".equals(edgeType) || "entry_transition".equals(edgeType)) {
            return name;
        }
        return name;
    }

    public String instructionForCheckpointType(Checkpoint next, Graph.DirectedEdge edge) {
        String type = normalize(next.getCheckpointType());
        String name = next.getCheckpointName();

        if ("gate".equals(type)) {
            return "Head toward " + name + ".";
        }
        if ("building_entry".equals(type)) {
            return "Head toward " + name + " entrance.";
        }
        if ("landmark".equals(type) || "junction".equals(type)) {
            return "Continue toward " + name + ".";
        }
        if ("facility_entry".equals(type) || "parking".equals(type)) {
            return "Head toward " + name + ".";
        }
        if ("outdoor_path".equals(type)) {
            return "Walk toward " + name + ".";
        }

        String edgeType = edge == null ? null : normalize(edge.getEdgeType());
        if ("outdoor_walk".equals(edgeType)) {
            return "Walk toward " + name + ".";
        }
        if ("entry_transition".equals(edgeType)) {
            return "Head toward " + name + ".";
        }
        return "Continue toward " + name + ".";
    }

    private static String arrivalInstruction(String destinationName) {
        return "You have arrived at " + destinationName + ".";
    }

    private static String displayDestinationName(
            String destinationPlaceName,
            Checkpoint destinationCheckpoint
    ) {
        if (destinationPlaceName != null && !destinationPlaceName.trim().isEmpty()) {
            return destinationPlaceName.trim();
        }
        return destinationCheckpoint.getCheckpointName();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }
}
