package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendEdgeDto {
    @SerializedName("edge_id")
    public String edgeId;
    @SerializedName("from_checkpoint_id")
    public String fromCheckpointId;
    @SerializedName("to_checkpoint_id")
    public String toCheckpointId;
    @SerializedName("distance_meters")
    public double distanceMeters;
    @SerializedName("edge_type")
    public String edgeType;
    @SerializedName("reverse_of_bidirectional_edge")
    public boolean reverseOfBidirectionalEdge;
}
