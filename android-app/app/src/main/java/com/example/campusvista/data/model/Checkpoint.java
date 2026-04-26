package com.example.campusvista.data.model;

public final class Checkpoint {
    private final String checkpointId;
    private final String checkpointName;
    private final String checkpointType;
    private final double xCoord;
    private final double yCoord;
    private final Double latitude;
    private final Double longitude;
    private final String description;
    private final String orientation;

    public Checkpoint(
            String checkpointId,
            String checkpointName,
            String checkpointType,
            double xCoord,
            double yCoord,
            Double latitude,
            Double longitude,
            String description,
            String orientation
    ) {
        this.checkpointId = checkpointId;
        this.checkpointName = checkpointName;
        this.checkpointType = checkpointType;
        this.xCoord = xCoord;
        this.yCoord = yCoord;
        this.latitude = latitude;
        this.longitude = longitude;
        this.description = description;
        this.orientation = orientation;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getCheckpointName() {
        return checkpointName;
    }

    public String getCheckpointType() {
        return checkpointType;
    }

    public double getXCoord() {
        return xCoord;
    }

    public double getYCoord() {
        return yCoord;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public String getDescription() {
        return description;
    }

    public String getOrientation() {
        return orientation;
    }
}
