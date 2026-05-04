package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RouteResult {
    private final boolean routeFound;
    private final String destinationCheckpointId;
    private final List<Checkpoint> checkpointPath;
    private final double totalDistanceMeters;
    private final List<String> instructions;
    private final List<String> warnings;

    private RouteResult(
            boolean routeFound,
            String destinationCheckpointId,
            List<Checkpoint> checkpointPath,
            double totalDistanceMeters,
            List<String> instructions,
            List<String> warnings
    ) {
        this.routeFound = routeFound;
        this.destinationCheckpointId = destinationCheckpointId;
        this.checkpointPath = immutableCopy(checkpointPath);
        this.totalDistanceMeters = totalDistanceMeters;
        this.instructions = immutableCopy(instructions);
        this.warnings = immutableCopy(warnings);
    }

    public static RouteResult success(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode,
            List<Checkpoint> checkpointPath,
            List<Graph.DirectedEdge> edgePath,
            double totalDistanceMeters,
            double totalCost,
            List<String> instructions,
            List<String> warnings
    ) {
        return new RouteResult(
                true,
                destinationCheckpointId,
                checkpointPath,
                totalDistanceMeters,
                instructions,
                warnings
        );
    }

    public static RouteResult noRoute(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode
    ) {
        return new RouteResult(
                false,
                destinationCheckpointId,
                Collections.<Checkpoint>emptyList(),
                0.0,
                Collections.<String>emptyList(),
                Collections.<String>emptyList()
        );
    }

    public boolean isRouteFound() {
        return routeFound;
    }

    public String getDestinationCheckpointId() {
        return destinationCheckpointId;
    }

    public List<Checkpoint> getCheckpointPath() {
        return checkpointPath;
    }

    public double getTotalDistanceMeters() {
        return totalDistanceMeters;
    }

    public List<String> getInstructions() {
        return instructions;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    private static <T> List<T> immutableCopy(List<T> input) {
        if (input == null || input.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(input));
    }
}
