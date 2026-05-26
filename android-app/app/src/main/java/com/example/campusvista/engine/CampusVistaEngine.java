package com.example.campusvista.engine;

import android.content.Context;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.recognition.RecognitionMatch;
import com.example.campusvista.recognition.RecognitionResponse;
import com.example.campusvista.recognition.LocalRecognitionEngine;
import com.example.campusvista.recognition.RecognitionConfidence;
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.routing.RouteResult;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class CampusVistaEngine {
    private static final int DEFAULT_SEARCH_LIMIT = 200;
    private static final int DEFAULT_RECOGNITION_LIMIT = 5;

    private final Context context;
    private final CampusVistaApp app;

    public CampusVistaEngine(Context context) {
        this.context = context.getApplicationContext();
        this.app = (CampusVistaApp) this.context;
    }

    public List<Place> searchPlaces(String query, String placeType, int limit) {
        String normalizedQuery = query == null ? "" : query.trim();
        int resultLimit = Math.max(1, limit <= 0 ? DEFAULT_SEARCH_LIMIT : limit);
        if (normalizedQuery.isEmpty() && placeType != null) {
            return app.getPlaceRepository().getPlacesByType(placeType);
        }
        if (placeType != null) {
            return app.getPlaceRepository().searchPlacesByType(normalizedQuery, placeType, resultLimit);
        }
        if (normalizedQuery.isEmpty()) {
            return app.getPlaceRepository().getAllPlaces();
        }
        return app.getPlaceRepository().searchPlaces(normalizedQuery, resultLimit);
    }

    public List<Checkpoint> getCheckpoints() {
        return app.getCheckpointRepository().getAllCheckpoints();
    }

    public Place getPlace(String placeId) {
        return app.getPlaceRepository().getPlaceById(placeId);
    }

    public Checkpoint getCheckpoint(String checkpointId) {
        return app.getCheckpointRepository().getCheckpointById(checkpointId);
    }

    public Checkpoint nearestCheckpoint(double mapX, double mapY) {
        return app.getNearestCheckpointFinder().findNearest(mapX, mapY);
    }

    public RouteResult buildRoute(
            String startCheckpointId,
            String destinationCheckpointId,
            RouteMode routeMode,
            String destinationName
    ) {
        return app.getRoutePlanner().computeRoute(
                startCheckpointId,
                destinationCheckpointId,
                routeMode,
                destinationName
        );
    }

    public OutdoorPano getPano(String checkpointId) {
        return app.getPanoRepository().getOutdoorPanoForCheckpoint(checkpointId);
    }

    public boolean hasPano(String checkpointId) {
        return app.getPanoRepository().hasOutdoorPano(checkpointId);
    }

    public RecognitionResponse recognize(byte[] imageBytes) throws IOException {
        List<RecognitionMatch> matches = LocalRecognitionEngine.getInstance(context)
                .recognize(imageBytes, DEFAULT_RECOGNITION_LIMIT);
        RecognitionResponse response = new RecognitionResponse();
        response.matches = matches == null ? Collections.emptyList() : matches;
        response.recognized = RecognitionConfidence.isConfident(response.matches);
        response.message = response.recognized
                ? "Location recognized on device."
                : "Showing closest on-device matches.";
        response.modelVersion = "android-local-vpr-v1";
        return response;
    }
}
