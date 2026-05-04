package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.function.ToDoubleFunction;

final class ShortestPathSearch {
    private ShortestPathSearch() {
    }

    static Path find(
            Graph graph,
            CrowdCostCalculator crowdCostCalculator,
            String startCheckpointId,
            String destinationCheckpointId,
            ToDoubleFunction<String> heuristic
    ) {
        PriorityQueue<NodeRecord> openSet = new PriorityQueue<>();
        Map<String, Double> bestCost = new HashMap<>();
        Map<String, Graph.DirectedEdge> cameFromEdge = new HashMap<>();

        bestCost.put(startCheckpointId, 0.0);
        openSet.add(new NodeRecord(
                startCheckpointId,
                0.0,
                heuristic.applyAsDouble(startCheckpointId)
        ));

        while (!openSet.isEmpty()) {
            NodeRecord current = openSet.poll();
            Double knownBest = bestCost.get(current.checkpointId);
            if (knownBest == null || current.pathCost > knownBest) {
                continue;
            }

            if (current.checkpointId.equals(destinationCheckpointId)) {
                return new Path(
                        reconstructEdges(startCheckpointId, destinationCheckpointId, cameFromEdge),
                        current.pathCost
                );
            }

            for (Graph.DirectedEdge edge : graph.getOutgoingEdges(current.checkpointId)) {
                double nextCost = current.pathCost + crowdCostCalculator.calculateEdgeCost(edge);
                Double previousBest = bestCost.get(edge.getToCheckpointId());
                if (previousBest == null || nextCost < previousBest) {
                    bestCost.put(edge.getToCheckpointId(), nextCost);
                    cameFromEdge.put(edge.getToCheckpointId(), edge);
                    openSet.add(new NodeRecord(
                            edge.getToCheckpointId(),
                            nextCost,
                            nextCost + heuristic.applyAsDouble(edge.getToCheckpointId())
                    ));
                }
            }
        }

        return null;
    }

    static RouteResult toRouteResult(
            Graph graph,
            CrowdCostCalculator crowdCostCalculator,
            InstructionBuilder instructionBuilder,
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode,
            String destinationPlaceName,
            Path path
    ) {
        List<Checkpoint> checkpoints = checkpointsFromEdges(graph, startCheckpointId, path.edges);
        List<String> instructions = instructionBuilder.buildInstructions(
                checkpoints,
                path.edges,
                destinationPlaceName
        );
        return RouteResult.success(
                startCheckpointId,
                destinationCheckpointId,
                routeMode,
                checkpoints,
                path.edges,
                totalDistance(path.edges),
                path.totalCost,
                instructions,
                crowdCostCalculator.getCurrentWarningsForCheckpoints(checkpoints)
        );
    }

    private static List<Graph.DirectedEdge> reconstructEdges(
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

    private static List<Checkpoint> checkpointsFromEdges(
            Graph graph,
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

    static final class Path {
        private final List<Graph.DirectedEdge> edges;
        private final double totalCost;

        private Path(List<Graph.DirectedEdge> edges, double totalCost) {
            this.edges = edges;
            this.totalCost = totalCost;
        }
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
