package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendCheckpointDto {
    @SerializedName("checkpoint_id")
    public String checkpointId;
    @SerializedName("checkpoint_name")
    public String checkpointName;
    @SerializedName("checkpoint_type")
    public String checkpointType;
    @SerializedName("x_coord")
    public double xCoord;
    @SerializedName("y_coord")
    public double yCoord;
    public Double latitude;
    public Double longitude;
    public String description;
    public String orientation;
}
