package com.example.campusvista.routing;

import android.content.Context;

import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.Edge;
import com.example.campusvista.data.repository.CheckpointRepository;
import com.example.campusvista.data.repository.GraphRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GraphBuilder {
    private static GraphBuilder instance;
    private final CheckpointRepository checkpointRepository;
    private final GraphRepository graphRepository;
    private Graph cachedGraph;

    public static synchronized GraphBuilder getInstance(Context context) {
        if (instance == null) {
            instance = new GraphBuilder(
                    CheckpointRepository.getInstance(context),
                    GraphRepository.getInstance(context)
            );
        }
        return instance;
    }

    public GraphBuilder(
            CheckpointRepository checkpointRepository,
            GraphRepository graphRepository
    ) {
        this.checkpointRepository = checkpointRepository;
        this.graphRepository = graphRepository;
    }

    public synchronized Graph buildStaticGraph() {
        if (cachedGraph == null) {
            cachedGraph = buildFrom(
                    checkpointRepository.getAllCheckpoints(),
                    graphRepository.getAllEdges()
            );
        }
        return cachedGraph;
    }

    public synchronized void clearCache() {
        cachedGraph = null;
    }

    public static Graph buildFrom(List<Checkpoint> checkpoints, List<Edge> edgeRows) {
        Map<String, Checkpoint> checkpointMap = new LinkedHashMap<>();
        Map<String, List<Graph.DirectedEdge>> adjacency = new LinkedHashMap<>();

        for (Checkpoint checkpoint : checkpoints) {
            checkpointMap.put(checkpoint.getCheckpointId(), checkpoint);
            adjacency.put(checkpoint.getCheckpointId(), new ArrayList<Graph.DirectedEdge>());
        }

        for (Edge edge : edgeRows) {
            if (!checkpointMap.containsKey(edge.getFromCheckpointId())
                    || !checkpointMap.containsKey(edge.getToCheckpointId())) {
                continue;
            }

            addDirectedEdge(
                    adjacency,
                    edge.getEdgeId(),
                    edge.getFromCheckpointId(),
                    edge.getToCheckpointId(),
                    edge.getDistanceMeters(),
                    edge.getEdgeType(),
                    false
            );

            if (edge.isBidirectional()) {
                addDirectedEdge(
                        adjacency,
                        edge.getEdgeId(),
                        edge.getToCheckpointId(),
                        edge.getFromCheckpointId(),
                        edge.getDistanceMeters(),
                        edge.getEdgeType(),
                        true
                );
            }
        }

        return new Graph(checkpointMap, adjacency);
    }

    private static void addDirectedEdge(
            Map<String, List<Graph.DirectedEdge>> adjacency,
            String edgeId,
            String fromCheckpointId,
            String toCheckpointId,
            double distanceMeters,
            String edgeType,
            boolean reverseOfBidirectionalEdge
    ) {
        List<Graph.DirectedEdge> edges = adjacency.get(fromCheckpointId);
        if (edges != null) {
            edges.add(new Graph.DirectedEdge(
                    edgeId,
                    fromCheckpointId,
                    toCheckpointId,
                    distanceMeters,
                    edgeType,
                    reverseOfBidirectionalEdge
            ));
        }
    }
}
