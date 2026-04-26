package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class AStarRouter {
    private final Graph graph;
    private final CrowdCostCalculator crowdCostCalculator;
    private final InstructionBuilder instructionBuilder;
    private final double metersPerPixel;

    public AStarRouter(
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

        Map<String, Double> penaltyByCheckpoint = routeMode == RouteMode.AVOID_CROWDED_PATH
                ? crowdCostCalculator.getCurrentPenaltyByCheckpoint()
                : Collections.<String, Double>emptyMap();

        PriorityQueue<NodeRecord> openSet = new PriorityQueue<>();
        Map<String, Double> bestCost = new HashMap<>();
        Map<String, Graph.DirectedEdge> cameFromEdge = new HashMap<>();

        bestCost.put(startCheckpointId, 0.0);
        openSet.add(new NodeRecord(
                startCheckpointId,
                0.0,
                heuristic(startCheckpointId, destinationCheckpointId)
        ));

        while (!openSet.isEmpty()) {
            NodeRecord current = openSet.poll();
            Double knownBest = bestCost.get(current.checkpointId);
            if (knownBest == null || current.pathCost > knownBest) {
                continue;
            }

            if (current.checkpointId.equals(destinationCheckpointId)) {
                return buildResult(
                        startCheckpointId,
                        destinationCheckpointId,
                        routeMode,
                        destinationPlaceName,
                        cameFromEdge,
                        current.pathCost
                );
            }

            for (Graph.DirectedEdge edge : graph.getOutgoingEdges(current.checkpointId)) {
                double edgeCost = crowdCostCalculator.calculateEdgeCost(
                        edge,
                        routeMode,
                        penaltyByCheckpoint
                );
                double nextCost = current.pathCost + edgeCost;
                Double previousBest = bestCost.get(edge.getToCheckpointId());
                if (previousBest == null || nextCost < previousBest) {
                    bestCost.put(edge.getToCheckpointId(), nextCost);
                    cameFromEdge.put(edge.getToCheckpointId(), edge);
                    openSet.add(new NodeRecord(
                            edge.getToCheckpointId(),
                            nextCost,
                            nextCost + heuristic(edge.getToCheckpointId(), destinationCheckpointId)
                    ));
                }
            }
        }

        return RouteResult.noRoute(startCheckpointId, destinationCheckpointId, routeMode);
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

    private RouteResult buildResult(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode,
            String destinationPlaceName,
            Map<String, Graph.DirectedEdge> cameFromEdge,
            double totalCost
    ) {
        List<Graph.DirectedEdge> edges = reconstructEdges(
                startCheckpointId,
                destinationCheckpointId,
                cameFromEdge
        );
        List<Checkpoint> checkpoints = checkpointsFromEdges(startCheckpointId, edges);
        double totalDistance = totalDistance(edges);
        List<String> instructions = instructionBuilder.buildInstructions(
                checkpoints,
                edges,
                destinationPlaceName
        );
        return RouteResult.success(
                startCheckpointId,
                destinationCheckpointId,
                routeMode,
                checkpoints,
                edges,
                totalDistance,
                totalCost,
                instructions
        );
    }

    static List<Graph.DirectedEdge> reconstructEdges(
            String startCheckpointId,
            String destinationCheckpointId,
            Map<String, Graph.DirectedEdge> cameFromEdge
    ) {
        List<Graph.DirectedEdge> edges = new ArrayList<>();
        String currentId = destinationCheckpointId;
        while (!currentId.equals(startCheckpointId)) {
            Graph.DirectedEdge edge = cameFromEdge.get(currentId);
            if (edge == null) {
                return Collections.emptyList();
            }
            edges.add(edge);
            currentId = edge.getFromCheckpointId();
        }
        Collections.reverse(edges);
        return edges;
    }

    private List<Checkpoint> checkpointsFromEdges(
            String startCheckpointId,
            List<Graph.DirectedEdge> edges
    ) {
        List<Checkpoint> checkpoints = new ArrayList<>();
        Checkpoint start = graph.getCheckpoint(startCheckpointId);
        if (start != null) {
            checkpoints.add(start);
        }
        for (Graph.DirectedEdge edge : edges) {
            Checkpoint checkpoint = graph.getCheckpoint(edge.getToCheckpointId());
            if (checkpoint != null) {
                checkpoints.add(checkpoint);
            }
        }
        return checkpoints;
    }

    private static double totalDistance(List<Graph.DirectedEdge> edges) {
        double total = 0.0;
        for (Graph.DirectedEdge edge : edges) {
            total += edge.getDistanceMeters();
        }
        return total;
    }

    private static final class NodeRecord implements Comparable<NodeRecord> {
        private final String checkpointId;
        private final double pathCost;
        private final double estimatedTotalCost;

        private NodeRecord(String checkpointId, double pathCost, double estimatedTotalCost) {
            this.checkpointId = checkpointId;
            this.pathCost = pathCost;
            this.estimatedTotalCost = estimatedTotalCost;
        }

        @Override
        public int compareTo(NodeRecord other) {
            return Double.compare(estimatedTotalCost, other.estimatedTotalCost);
        }
    }
}
