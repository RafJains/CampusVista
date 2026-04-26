package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class BackendRouteResponse {
    @SerializedName("route_found")
    public boolean routeFound;
    public String algorithm;
    @SerializedName("route_mode")
    public String routeMode;
    @SerializedName("start_checkpoint_id")
    public String startCheckpointId;
    @SerializedName("destination_checkpoint_id")
    public String destinationCheckpointId;
    @SerializedName("destination_name")
    public String destinationName;
    @SerializedName("total_distance")
    public double totalDistance;
    @SerializedName("total_cost")
    public double totalCost;
    @SerializedName("estimated_time")
    public String estimatedTime;
    @SerializedName("checkpoint_ids")
    public List<String> checkpointIds;
    public List<BackendCheckpointDto> checkpoints;
    public List<BackendEdgeDto> edges;
    public List<String> instructions;
    public List<String> warnings;
}
