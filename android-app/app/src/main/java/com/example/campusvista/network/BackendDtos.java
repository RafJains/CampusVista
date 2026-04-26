package com.example.campusvista.network;

import com.example.campusvista.routing.RouteMode;
import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class BackendDtos {
    private BackendDtos() {
    }

    public static final class CheckpointDto {
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

    public static final class PlaceDto {
        @SerializedName("place_id")
        public String placeId;
        @SerializedName("place_name")
        public String placeName;
        @SerializedName("place_type")
        public String placeType;
        @SerializedName("checkpoint_id")
        public String checkpointId;
        public String description;
        public String keywords;
        @SerializedName("match_score")
        public double matchScore;
        @SerializedName("match_source")
        public String matchSource;
        @SerializedName("matched_text")
        public String matchedText;
    }

    public static final class PanoDto {
        @SerializedName("pano_id")
        public String panoId;
        @SerializedName("checkpoint_id")
        public String checkpointId;
        @SerializedName("image_file")
        public String imageFile;
        @SerializedName("thumbnail_file")
        public String thumbnailFile;
        public String orientation;
        public String description;
        @SerializedName("image_url")
        public String imageUrl;
        @SerializedName("thumbnail_url")
        public String thumbnailUrl;
    }

    public static final class EdgeDto {
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

    public static final class NearestCheckpointDto {
        public CheckpointDto checkpoint;
        @SerializedName("distance_pixels")
        public double distancePixels;
        @SerializedName("distance_meters")
        public double distanceMeters;
    }

    public static final class RouteRequestDto {
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

        public static RouteRequestDto forCheckpoints(
                String startCheckpointId,
                String destinationCheckpointId,
                RouteMode routeMode
        ) {
            RouteRequestDto request = new RouteRequestDto();
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

    public static final class RouteResponseDto {
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
        public List<CheckpointDto> checkpoints;
        public List<EdgeDto> edges;
        public List<String> instructions;
        public List<String> warnings;
    }

    public static final class RecognitionRequestDto {
        @SerializedName("image_base64")
        public String imageBase64;
        @SerializedName("label_name")
        public String labelName;
        @SerializedName("model_label_index")
        public Integer modelLabelIndex;
        public Double confidence;
    }

    public static final class RecognitionCandidateDto {
        @SerializedName("checkpoint_id")
        public String checkpointId;
        @SerializedName("label_name")
        public String labelName;
        @SerializedName("model_label_index")
        public int modelLabelIndex;
        @SerializedName("confidence_threshold")
        public double confidenceThreshold;
        public Double confidence;
    }

    public static final class RecognitionResponseDto {
        public boolean available;
        public String status;
        public String message;
        @SerializedName("checkpoint_id")
        public String checkpointId;
        public Double confidence;
        public List<RecognitionCandidateDto> candidates;
        @SerializedName("fallback_options")
        public List<String> fallbackOptions;
    }
}
