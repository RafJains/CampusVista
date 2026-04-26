package com.example.campusvista.data.model;

public final class Edge {
    private final String edgeId;
    private final String fromCheckpointId;
    private final String toCheckpointId;
    private final double distanceMeters;
    private final boolean bidirectional;
    private final String edgeType;

    public Edge(
            String edgeId,
            String fromCheckpointId,
            String toCheckpointId,
            double distanceMeters,
            boolean bidirectional,
            String edgeType
    ) {
        this.edgeId = edgeId;
        this.fromCheckpointId = fromCheckpointId;
        this.toCheckpointId = toCheckpointId;
        this.distanceMeters = distanceMeters;
        this.bidirectional = bidirectional;
        this.edgeType = edgeType;
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

    public boolean isBidirectional() {
        return bidirectional;
    }

    public String getEdgeType() {
        return edgeType;
    }
}
