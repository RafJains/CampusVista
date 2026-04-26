package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Graph {
    private final Map<String, Checkpoint> checkpoints;
    private final Map<String, List<DirectedEdge>> adjacency;
    private final int directedEdgeCount;

    public Graph(
            Map<String, Checkpoint> checkpoints,
            Map<String, List<DirectedEdge>> adjacency
    ) {
        Map<String, Checkpoint> checkpointCopy = new LinkedHashMap<>(checkpoints);
        Map<String, List<DirectedEdge>> adjacencyCopy = new HashMap<>();
        int count = 0;

        for (String checkpointId : checkpointCopy.keySet()) {
            List<DirectedEdge> edges = adjacency.get(checkpointId);
            if (edges == null) {
                edges = Collections.emptyList();
            }
            List<DirectedEdge> immutableEdges = Collections.unmodifiableList(new ArrayList<>(edges));
            adjacencyCopy.put(checkpointId, immutableEdges);
            count += immutableEdges.size();
        }

        this.checkpoints = Collections.unmodifiableMap(checkpointCopy);
        this.adjacency = Collections.unmodifiableMap(adjacencyCopy);
        this.directedEdgeCount = count;
    }

    public Checkpoint getCheckpoint(String checkpointId) {
        return checkpoints.get(checkpointId);
    }

    public Collection<Checkpoint> getCheckpoints() {
        return checkpoints.values();
    }

    public List<DirectedEdge> getOutgoingEdges(String checkpointId) {
        List<DirectedEdge> edges = adjacency.get(checkpointId);
        return edges == null ? Collections.<DirectedEdge>emptyList() : edges;
    }

    public DirectedEdge getEdgeBetween(String fromCheckpointId, String toCheckpointId) {
        for (DirectedEdge edge : getOutgoingEdges(fromCheckpointId)) {
            if (edge.getToCheckpointId().equals(toCheckpointId)) {
                return edge;
            }
        }
        return null;
    }

    public boolean containsCheckpoint(String checkpointId) {
        return checkpoints.containsKey(checkpointId);
    }

    public int getNodeCount() {
        return checkpoints.size();
    }

    public int getDirectedEdgeCount() {
        return directedEdgeCount;
    }

    public static final class DirectedEdge {
        private final String edgeId;
        private final String fromCheckpointId;
        private final String toCheckpointId;
        private final double distanceMeters;
        private final String edgeType;
        private final boolean reverseOfBidirectionalEdge;

        public DirectedEdge(
                String edgeId,
                String fromCheckpointId,
                String toCheckpointId,
                double distanceMeters,
                String edgeType,
                boolean reverseOfBidirectionalEdge
        ) {
            this.edgeId = edgeId;
            this.fromCheckpointId = fromCheckpointId;
            this.toCheckpointId = toCheckpointId;
            this.distanceMeters = distanceMeters;
            this.edgeType = edgeType;
            this.reverseOfBidirectionalEdge = reverseOfBidirectionalEdge;
        }

        public String getEdgeId() {
            return edgeId;
        }

        public String getFromCheckpointId() {
            return fromCheckpointId;
        }

        public String getToCheckpointId() {
            return toCheckpointId;
        }

        public double getDistanceMeters() {
            return distanceMeters;
        }

        public String getEdgeType() {
            return edgeType;
        }

        public boolean isReverseOfBidirectionalEdge() {
            return reverseOfBidirectionalEdge;
        }
    }
}
