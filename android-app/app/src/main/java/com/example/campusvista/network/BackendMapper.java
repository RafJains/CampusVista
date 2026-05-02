package com.example.campusvista.network;

import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.network.BackendDtos.CheckpointDto;
import com.example.campusvista.network.BackendDtos.EdgeDto;
import com.example.campusvista.network.BackendDtos.PanoDto;
import com.example.campusvista.network.BackendDtos.PlaceDto;
import com.example.campusvista.network.BackendDtos.RouteResponseDto;
import com.example.campusvista.routing.Graph;
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.routing.RouteResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BackendMapper {
    private BackendMapper() {
    }

    public static Checkpoint toCheckpoint(CheckpointDto dto) {
        if (dto == null) {
            return null;
        }
        return new Checkpoint(
                dto.checkpointId,
                dto.checkpointName,
                dto.checkpointType,
                dto.xCoord,
                dto.yCoord,
                dto.rawMapX,
                dto.rawMapY,
                dto.latitude,
                dto.longitude,
                dto.description,
                dto.orientation
        );
    }

    public static List<Checkpoint> toCheckpoints(List<CheckpointDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<Checkpoint> checkpoints = new ArrayList<>();
        for (CheckpointDto dto : dtos) {
            Checkpoint checkpoint = toCheckpoint(dto);
            if (checkpoint != null) {
                checkpoints.add(checkpoint);
            }
        }
        return checkpoints;
    }

    public static Place toPlace(PlaceDto dto) {
        if (dto == null) {
            return null;
        }
        return new Place(
                dto.placeId,
                dto.placeName,
                dto.placeType,
                dto.checkpointId,
                dto.description,
                dto.keywords
        );
    }

    public static List<Place> toPlaces(List<PlaceDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<Place> places = new ArrayList<>();
        for (PlaceDto dto : dtos) {
            Place place = toPlace(dto);
            if (place != null) {
                places.add(place);
            }
        }
        return places;
    }

    public static OutdoorPano toPano(PanoDto dto) {
        if (dto == null) {
            return null;
        }
        return new OutdoorPano(
                dto.panoId,
                dto.checkpointId,
                dto.imageFile,
                dto.thumbnailFile,
                dto.orientation,
                dto.description
        );
    }

    public static RouteResult toRouteResult(
            RouteResponseDto response,
            RouteMode fallbackMode
    ) {
        RouteMode routeMode = parseRouteMode(response == null ? null : response.routeMode, fallbackMode);
        if (response == null || !response.routeFound) {
            return RouteResult.noRoute(
                    response == null ? null : response.startCheckpointId,
                    response == null ? null : response.destinationCheckpointId,
                    routeMode
            );
        }

        List<Checkpoint> checkpoints = toCheckpoints(response.checkpoints);
        List<Graph.DirectedEdge> edges = toEdges(response.edges);
        List<String> instructions = response.instructions == null
                ? Collections.<String>emptyList()
                : response.instructions;
        return RouteResult.success(
                response.startCheckpointId,
                response.destinationCheckpointId,
                routeMode,
                checkpoints,
                edges,
                response.totalDistance,
                response.totalCost,
                instructions,
                response.warnings
        );
    }

    private static List<Graph.DirectedEdge> toEdges(List<EdgeDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<Graph.DirectedEdge> edges = new ArrayList<>();
        for (EdgeDto dto : dtos) {
            edges.add(new Graph.DirectedEdge(
                    dto.edgeId,
                    dto.fromCheckpointId,
                    dto.toCheckpointId,
                    dto.distanceMeters,
                    dto.edgeType,
                    dto.reverseOfBidirectionalEdge
            ));
        }
        return edges;
    }

    private static RouteMode parseRouteMode(String value, RouteMode fallbackMode) {
        if ("shortest".equals(value)) {
            return RouteMode.SHORTEST_PATH;
        }
        return fallbackMode == null ? RouteMode.SHORTEST_PATH : fallbackMode;
    }
}
