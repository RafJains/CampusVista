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
import com.example.campusvista.pano.OutdoorPanoViewer;
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.routing.RouteResult;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.home.HomeMapActivity;

import java.util.Locale;

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
    private Button navNextButton;
    private Button navCompleteButton;
    private Button navPanoButton;
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
        navNextButton = findViewById(R.id.navNextButton);
        navCompleteButton = findViewById(R.id.navCompleteButton);
        navPanoButton = findViewById(R.id.navPanoButton);
        panoViewer = new OutdoorPanoViewer(
                this,
                ((CampusVistaApp) getApplication()).getPanoRepository()
        );

        destinationCheckpointId = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID);
        destinationName = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_NAME);
        destinationPlaceId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        routeMode = RouteMode.SHORTEST_PATH;
        startInPano = getIntent().getBooleanExtra(NavExtras.EXTRA_START_IN_PANO, false);

        navNextButton.setOnClickListener(view -> moveNext());
        navCompleteButton.setOnClickListener(view -> completeRoute());
        navPanoButton.setOnClickListener(view -> setPanoMode(true));
        panoPreviousButton.setOnClickListener(view -> movePrevious());
        panoNextButton.setOnClickListener(view -> moveNext());
        findViewById(R.id.navBackToMapButton).setOnClickListener(view -> setPanoMode(false));

        ViewFactory.setVisible(navPanoButton, false);
        ViewFactory.setVisible(navCompleteButton, false);
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
        routeResult = computeLocalRoute(startId);
        handleRouteLoaded();
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
        ViewFactory.setVisible(navPanoButton, true);
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
            ViewFactory.setVisible(navNextButton, false);
            ViewFactory.setVisible(navCompleteButton, !panoMode);
            updatePanoStep();
            return;
        }

        navProgress.setText("Step " + (instructionIndex + 1) + " of " + total);
        navInstruction.setText(routeResult.getInstructions().get(instructionIndex));
        ViewFactory.setVisible(navNextButton, !panoMode);
        ViewFactory.setVisible(navCompleteButton, false);
        updatePanoStep();
    }

    private void bindMetrics() {
        Checkpoint start = null;
        Checkpoint end = null;
        if (routeResult != null && !routeResult.getCheckpointPath().isEmpty()) {
            start = routeResult.getCheckpointPath().get(0);
            end = routeResult.getCheckpointPath().get(routeResult.getCheckpointPath().size() - 1);
        }
        navStart.setText("Start\n" + UiText.checkpointName(
                start,
                LocationStore.getCurrentCheckpointId(this)
        ));
        navEnd.setText("End\n" + (destinationName == null
                ? UiText.checkpointName(end, destinationCheckpointId)
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
        ViewFactory.setVisible(navPanoButton, !enabled);
        ViewFactory.setVisible(navNextButton, !enabled && !isRouteComplete());
        ViewFactory.setVisible(navCompleteButton, !enabled && isRouteComplete());
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
        return ((CampusVistaApp) getApplication())
                .getCampusVistaEngine()
                .getPano(checkpointId);
    }

    private void showCrowdWarningsIfNeeded() {
        if (warningsShown || routeResult == null || routeResult.getWarnings().isEmpty()) {
            return;
        }
        String message = UiText.crowdWarningMessage(routeResult.getWarnings());
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
