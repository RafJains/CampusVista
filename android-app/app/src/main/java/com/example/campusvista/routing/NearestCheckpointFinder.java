package com.example.campusvista.routing;

import com.example.campusvista.data.model.Checkpoint;

public final class NearestCheckpointFinder {
    private final Graph graph;

    public NearestCheckpointFinder(Graph graph) {
        this.graph = graph;
    }

    public Checkpoint findNearest(double xCoord, double yCoord) {
        Checkpoint nearest = null;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;

        for (Checkpoint checkpoint : graph.getCheckpoints()) {
            double dx = checkpoint.getMapX() - xCoord;
            double dy = checkpoint.getMapY() - yCoord;
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = checkpoint;
            }
        }

        return nearest;
    }

    public String findNearestCheckpointId(double xCoord, double yCoord) {
        Checkpoint nearest = findNearest(xCoord, yCoord);
        return nearest == null ? null : nearest.getCheckpointId();
    }
}
