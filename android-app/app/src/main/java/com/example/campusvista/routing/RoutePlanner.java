package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

public final class RoutePlanner {
    private final Graph graph;
    private final CrowdCostCalculator crowdCostCalculator;
    private final InstructionBuilder instructionBuilder;
    private final double metersPerPixel;

    public RoutePlanner(
            Graph graph,
            CrowdCostCalculator crowdCostCalculator,
            InstructionBuilder instructionBuilder,
            double metersPerPixel
    ) {
        this.graph = graph;
        this.crowdCostCalculator = crowdCostCalculator;
        this.instructionBuilder = instructionBuilder;
        this.metersPerPixel = metersPerPixel;
    }

    public RouteResult computeRoute(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode,
            String destinationPlaceName
    ) {
        if (!graph.containsCheckpoint(startCheckpointId)
                || !graph.containsCheckpoint(destinationCheckpointId)) {
            return RouteResult.noRoute(startCheckpointId, destinationCheckpointId, routeMode);
        }

        ShortestPathSearch.Path path = ShortestPathSearch.find(
                graph,
                crowdCostCalculator,
                startCheckpointId,
                destinationCheckpointId,
                checkpointId -> heuristic(checkpointId, destinationCheckpointId)
        );
        if (path == null) {
            path = ShortestPathSearch.find(
                    graph,
                    crowdCostCalculator,
                    startCheckpointId,
                    destinationCheckpointId,
                    checkpointId -> 0.0
            );
        }
        if (path == null) {
            return RouteResult.noRoute(startCheckpointId, destinationCheckpointId, routeMode);
        }

        return ShortestPathSearch.toRouteResult(
                graph,
                crowdCostCalculator,
                instructionBuilder,
                startCheckpointId,
                destinationCheckpointId,
                routeMode,
                destinationPlaceName,
                path
        );
    }

    private double heuristic(String fromCheckpointId, String toCheckpointId) {
        Checkpoint from = graph.getCheckpoint(fromCheckpointId);
        Checkpoint to = graph.getCheckpoint(toCheckpointId);
        if (from == null || to == null) {
            return 0.0;
        }
        double dx = to.getXCoord() - from.getXCoord();
        double dy = to.getYCoord() - from.getYCoord();
        return Math.sqrt(dx * dx + dy * dy) * metersPerPixel;
    }
}
