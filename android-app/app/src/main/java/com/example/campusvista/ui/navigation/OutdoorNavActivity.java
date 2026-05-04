package com.example.campusvista.ui.navigation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.PanoDto;
import com.example.campusvista.network.BackendDtos.RouteRequestDto;
import com.example.campusvista.network.BackendDtos.RouteResponseDto;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.pano.OutdoorPanoViewer;
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.routing.RouteResult;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.home.HomeMapActivity;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class OutdoorNavActivity extends Activity {
    private String destinationCheckpointId;
    private String destinationName;
    private String destinationPlaceId;
    private RouteMode routeMode;
    private RouteResult routeResult;
    private int instructionIndex;
    private boolean panoMode;
    private boolean startInPano;
    private boolean warningsShown;
    private final Map<String, OutdoorPano> backendPanosByCheckpoint = new HashMap<>();

    private TextView navTitle;
    private TextView navStart;
    private TextView navEnd;
    private TextView navDistance;
    private TextView navProgress;
    private TextView navInstruction;
    private TextView panoCheckpointName;
    private TextView panoInstruction;
    private ImageView panoImage;
    private Button panoPreviousButton;
    private Button panoNextButton;
    private OutdoorPanoViewer panoViewer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outdoor_nav);

        navTitle = findViewById(R.id.navTitle);
        navStart = findViewById(R.id.navStart);
        navEnd = findViewById(R.id.navEnd);
        navDistance = findViewById(R.id.navDistance);
        navProgress = findViewById(R.id.navProgress);
        navInstruction = findViewById(R.id.navInstruction);
        panoCheckpointName = findViewById(R.id.navPanoCheckpointName);
        panoInstruction = findViewById(R.id.navPanoInstruction);
        panoImage = findViewById(R.id.navPanoImage);
        panoPreviousButton = findViewById(R.id.navPanoPreviousButton);
        panoNextButton = findViewById(R.id.navPanoNextButton);
        panoViewer = new OutdoorPanoViewer(
                this,
                ((CampusVistaApp) getApplication()).getPanoRepository()
        );

        destinationCheckpointId = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID);
        destinationName = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_NAME);
        destinationPlaceId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        routeMode = RouteMode.SHORTEST_PATH;
        startInPano = getIntent().getBooleanExtra(NavExtras.EXTRA_START_IN_PANO, false);

        findViewById(R.id.navNextButton).setOnClickListener(view -> moveNext());
        findViewById(R.id.navCompleteButton).setOnClickListener(view -> completeRoute());
        findViewById(R.id.navPanoButton).setOnClickListener(view -> setPanoMode(true));
        panoPreviousButton.setOnClickListener(view -> movePrevious());
        panoNextButton.setOnClickListener(view -> moveNext());
        findViewById(R.id.navBackToMapButton).setOnClickListener(view -> setPanoMode(false));

        ViewFactory.setVisible(findViewById(R.id.navPanoButton), false);
        ViewFactory.setVisible(findViewById(R.id.navCompleteButton), false);
        navProgress.setText("Preparing route...");
        computeRoute();
    }

    private void computeRoute() {
        String startId = LocationStore.getCurrentCheckpointId(this);
        if (startId == null || destinationCheckpointId == null) {
            Toast.makeText(this, "Start or destination is missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        RouteRequestDto request = RouteRequestDto.forCheckpoints(
                startId,
                destinationCheckpointId,
                routeMode
        );
        if (destinationPlaceId != null) {
            request.destinationPlaceId = destinationPlaceId;
            request.destinationCheckpointId = null;
        }
        BackendClient.getInstance(this).buildRoute(
                request,
                new BackendCallback<RouteResponseDto>() {
                    @Override
                    public void onSuccess(RouteResponseDto value) {
                        backendPanosByCheckpoint.clear();
                        cacheBackendPanos(value);
                        routeResult = BackendMapper.toRouteResult(value, routeMode);
                        handleRouteLoaded();
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        backendPanosByCheckpoint.clear();
                        routeResult = computeLocalRoute(startId);
                        handleRouteLoaded();
                    }
                }
        );
    }

    private RouteResult computeLocalRoute(String startId) {
        return ((CampusVistaApp) getApplication()).getRoutePlanner().computeRoute(
                startId,
                destinationCheckpointId,
                routeMode,
                destinationName
        );
    }

    private void handleRouteLoaded() {
        if (routeResult == null || !routeResult.isRouteFound()) {
            Toast.makeText(
                    this,
                    "No route found between these points.",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }
        ViewFactory.setVisible(findViewById(R.id.navPanoButton), true);
        updateStep();
        if (startInPano) {
            setPanoMode(true);
        }
        showCrowdWarningsIfNeeded();
    }

    private void updateStep() {
        if (routeResult == null || !routeResult.isRouteFound()) {
            return;
        }

        bindMetrics();
        int total = routeResult.getInstructions().size();
        if (instructionIndex >= total) {
            navProgress.setText("Route complete");
            navInstruction.setText("You have arrived at " + destinationName + ".");
            ViewFactory.setVisible(findViewById(R.id.navNextButton), false);
            ViewFactory.setVisible(findViewById(R.id.navCompleteButton), true);
            updatePanoStep();
            return;
        }

        navProgress.setText("Step " + (instructionIndex + 1) + " of " + total);
        navInstruction.setText(routeResult.getInstructions().get(instructionIndex));
        ViewFactory.setVisible(findViewById(R.id.navNextButton), true);
        ViewFactory.setVisible(findViewById(R.id.navCompleteButton), false);
        updatePanoStep();
    }

    private void bindMetrics() {
        Checkpoint start = null;
        Checkpoint end = null;
        if (routeResult != null && !routeResult.getCheckpointPath().isEmpty()) {
            start = routeResult.getCheckpointPath().get(0);
            end = routeResult.getCheckpointPath().get(routeResult.getCheckpointPath().size() - 1);
        }
        navStart.setText("Start\n" + checkpointName(start, LocationStore.getCurrentCheckpointId(this)));
        navEnd.setText("End\n" + (destinationName == null
                ? checkpointName(end, destinationCheckpointId)
                : destinationName));
        navDistance.setText("Distance\n" + Math.round(routeResult.getTotalDistanceMeters()) + " m");
    }

    private void moveNext() {
        if (routeResult == null) {
            return;
        }
        if (instructionIndex >= routeResult.getInstructions().size() - 1) {
            if (panoMode) {
                completeRoute();
                return;
            }
        }
        instructionIndex++;
        updateStep();
    }

    private void movePrevious() {
        if (instructionIndex <= 0) {
            return;
        }
        instructionIndex--;
        updateStep();
    }

    private void completeRoute() {
        LocationStore.setCurrentCheckpointId(this, finalCheckpointId());
        Intent intent = new Intent(this, HomeMapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private String finalCheckpointId() {
        if (routeResult != null && !routeResult.getCheckpointPath().isEmpty()) {
            Checkpoint finalCheckpoint = routeResult.getCheckpointPath()
                    .get(routeResult.getCheckpointPath().size() - 1);
            if (finalCheckpoint != null && finalCheckpoint.getCheckpointId() != null) {
                return finalCheckpoint.getCheckpointId();
            }
        }
        if (routeResult != null && routeResult.getDestinationCheckpointId() != null) {
            return routeResult.getDestinationCheckpointId();
        }
        return destinationCheckpointId;
    }

    private Checkpoint checkpointForCurrentStep() {
        if (routeResult == null || routeResult.getCheckpointPath().isEmpty()) {
            return null;
        }
        int checkpointIndex = Math.min(instructionIndex, routeResult.getCheckpointPath().size() - 1);
        return routeResult.getCheckpointPath().get(checkpointIndex);
    }

    private void setPanoMode(boolean enabled) {
        panoMode = enabled;
        navTitle.setText(enabled ? "Pano Mode" : "Navigation Steps");
        ViewFactory.setVisible(findViewById(R.id.navMetricRow), !enabled);
        ViewFactory.setVisible(navProgress, !enabled);
        ViewFactory.setVisible(navInstruction, !enabled);
        ViewFactory.setVisible(findViewById(R.id.navPanoPanel), enabled);
        ViewFactory.setVisible(findViewById(R.id.navPanoButton), !enabled);
        ViewFactory.setVisible(findViewById(R.id.navCompleteButton), !enabled && isRouteComplete());
        updatePanoStep();
    }

    private boolean isRouteComplete() {
        return routeResult != null && instructionIndex >= routeResult.getInstructions().size();
    }

    private void updatePanoStep() {
        if (!panoMode || routeResult == null) {
            return;
        }

        Checkpoint checkpoint = checkpointForCurrentStep();
        if (checkpoint == null) {
            panoCheckpointName.setText("Current view");
            panoInstruction.setText("Under work: panorama view is being prepared.");
            panoViewer.loadPano(panoImage, null, false);
            panoPreviousButton.setEnabled(false);
            return;
        }

        int total = routeResult.getInstructions().size();
        panoCheckpointName.setText("Current view");
        if (instructionIndex < total) {
            panoInstruction.setText(routeResult.getInstructions().get(instructionIndex));
        } else {
            panoInstruction.setText("You have arrived at " + destinationName + ".");
        }

        OutdoorPano pano = panoForCheckpoint(checkpoint.getCheckpointId());
        boolean loaded = panoViewer.loadPano(panoImage, pano, false);
        if (!loaded) {
            panoInstruction.setText("Under work: panorama view is being prepared.");
        }
        panoPreviousButton.setEnabled(instructionIndex > 0);
        panoNextButton.setText(instructionIndex >= total - 1 ? "Finish" : "Next");
    }

    private OutdoorPano panoForCheckpoint(String checkpointId) {
        OutdoorPano backendPano = backendPanosByCheckpoint.get(checkpointId);
        if (backendPano != null) {
            return backendPano;
        }
        return ((CampusVistaApp) getApplication())
                .getPanoRepository()
                .getOutdoorPanoForCheckpoint(checkpointId);
    }

    private void cacheBackendPanos(RouteResponseDto value) {
        if (value == null || value.panos == null) {
            return;
        }
        for (PanoDto dto : value.panos) {
            OutdoorPano pano = BackendMapper.toPano(dto);
            if (pano != null && pano.getCheckpointId() != null) {
                backendPanosByCheckpoint.put(pano.getCheckpointId(), pano);
            }
        }
    }

    private static String checkpointName(Checkpoint checkpoint, String fallbackId) {
        return checkpoint == null ? fallbackId : checkpoint.getCheckpointName();
    }

    private void showCrowdWarningsIfNeeded() {
        if (warningsShown || routeResult == null || routeResult.getWarnings().isEmpty()) {
            return;
        }
        StringBuilder message = new StringBuilder();
        for (String warning : routeResult.getWarnings()) {
            if (warning != null && warning.toLowerCase(Locale.US).contains("may be")) {
                if (message.length() > 0) {
                    message.append("\n\n");
                }
                message.append(warning);
            }
        }
        if (message.length() == 0) {
            return;
        }
        warningsShown = true;
        new AlertDialog.Builder(this)
                .setTitle("Busy Area")
                .setMessage(message.toString())
                .setPositiveButton("OK", null)
                .show();
    }
}
