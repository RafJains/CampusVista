package com.example.campusvista.network.dto;

import com.example.campusvista.routing.RouteMode;
import com.google.gson.annotations.SerializedName;

public final class BackendRouteRequest {
    @SerializedName("start_checkpoint_id")
    public String startCheckpointId;
    @SerializedName("start_x")
    public Double startX;
    @SerializedName("start_y")
    public Double startY;
    @SerializedName("destination_checkpoint_id")
    public String destinationCheckpointId;
    @SerializedName("destination_place_id")
    public String destinationPlaceId;
    @SerializedName("destination_query")
    public String destinationQuery;
    @SerializedName("destination_x")
    public Double destinationX;
    @SerializedName("destination_y")
    public Double destinationY;
    @SerializedName("route_mode")
    public String routeMode;
    @SerializedName("now_iso")
    public String nowIso;

    public static BackendRouteRequest forCheckpoints(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode
    ) {
        BackendRouteRequest request = new BackendRouteRequest();
        request.startCheckpointId = startCheckpointId;
        request.destinationCheckpointId = destinationCheckpointId;
        request.routeMode = toBackendRouteMode(routeMode);
        return request;
    }

    public static String toBackendRouteMode(RouteMode routeMode) {
        if (routeMode == RouteMode.AVOID_CROWDED_PATH) {
            return "avoid_crowded";
        }
        return "shortest";
    }
}
