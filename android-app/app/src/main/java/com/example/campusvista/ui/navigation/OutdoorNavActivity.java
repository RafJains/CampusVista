package com.example.campusvista.ui.navigation;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
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
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.routing.RouteResult;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.home.HomeMapActivity;
import com.example.campusvista.ui.pano.OutdoorPanoActivity;

public final class OutdoorNavActivity extends Activity {
    private String destinationCheckpointId;
    private String destinationName;
    private String destinationPlaceId;
    private RouteMode routeMode;
    private RouteResult routeResult;
    private int instructionIndex;

    private TextView navSummary;
    private TextView navProgress;
    private TextView navInstruction;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outdoor_nav);

        navSummary = findViewById(R.id.navSummary);
        navProgress = findViewById(R.id.navProgress);
        navInstruction = findViewById(R.id.navInstruction);

        destinationCheckpointId = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID);
        destinationName = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_NAME);
        destinationPlaceId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        routeMode = parseRouteMode(getIntent().getStringExtra(NavExtras.EXTRA_ROUTE_MODE));

        findViewById(R.id.navNextButton).setOnClickListener(view -> moveNext());
        findViewById(R.id.navCompleteButton).setOnClickListener(view -> completeRoute());
        findViewById(R.id.navPanoButton).setOnClickListener(view -> openPano());

        navSummary.setText("Loading route from Python backend...");
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
                        routeResult = BackendMapper.toRouteResult(value, routeMode);
                        handleRouteLoaded();
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
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
                    "No outdoor route found between these points.",
                    Toast.LENGTH_SHORT
            ).show();
            finish();
            return;
        }
        updateStep();
    }

    private void updateStep() {
        if (routeResult == null || !routeResult.isRouteFound()) {
            return;
        }

        int total = routeResult.getInstructions().size();
        if (instructionIndex >= total) {
            navProgress.setText("Route complete");
            navInstruction.setText("You have arrived at " + destinationName + ".");
            ViewFactory.setVisible(findViewById(R.id.navNextButton), false);
            return;
        }

        navSummary.setText(UiText.routeModeLabel(routeMode)
                + " - " + Math.round(routeResult.getTotalDistanceMeters()) + " m");
        navProgress.setText("Step " + (instructionIndex + 1) + " of " + total);
        navInstruction.setText(routeResult.getInstructions().get(instructionIndex));
        bindPanoButton();
    }

    private void moveNext() {
        instructionIndex++;
        updateStep();
    }

    private void completeRoute() {
        LocationStore.setCurrentCheckpointId(this, destinationCheckpointId);
        Intent intent = new Intent(this, HomeMapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void bindPanoButton() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        Checkpoint checkpoint = checkpointForCurrentStep();
        boolean hasPano = checkpoint != null
                && app.getPanoRepository().hasOutdoorPano(checkpoint.getCheckpointId());
        ViewFactory.setVisible(findViewById(R.id.navPanoButton), hasPano);
        if (checkpoint != null) {
            BackendClient.getInstance(this).getPano(
                    checkpoint.getCheckpointId(),
                    new BackendCallback<PanoDto>() {
                        @Override
                        public void onSuccess(PanoDto value) {
                            ViewFactory.setVisible(findViewById(R.id.navPanoButton), true);
                        }

                        @Override
                        public void onFallback(Throwable throwable) {
                            ViewFactory.setVisible(findViewById(R.id.navPanoButton), hasPano);
                        }
                    }
            );
        }
    }

    private void openPano() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        Checkpoint checkpoint = checkpointForCurrentStep();
        if (checkpoint == null) {
            return;
        }
        OutdoorPano pano = app.getPanoRepository()
                .getOutdoorPanoForCheckpoint(checkpoint.getCheckpointId());
        if (pano == null) {
            return;
        }
        Intent intent = new Intent(this, OutdoorPanoActivity.class);
        intent.putExtra(NavExtras.EXTRA_CHECKPOINT_ID, checkpoint.getCheckpointId());
        intent.putExtra(NavExtras.EXTRA_CHECKPOINT_NAME, checkpoint.getCheckpointName());
        startActivity(intent);
    }

    private Checkpoint checkpointForCurrentStep() {
        if (routeResult == null || routeResult.getCheckpointPath().isEmpty()) {
            return null;
        }
        int checkpointIndex = Math.min(instructionIndex, routeResult.getCheckpointPath().size() - 1);
        return routeResult.getCheckpointPath().get(checkpointIndex);
    }

    private static RouteMode parseRouteMode(String value) {
        if (RouteMode.AVOID_CROWDED_PATH.name().equals(value)) {
            return RouteMode.AVOID_CROWDED_PATH;
        }
        return RouteMode.SHORTEST_PATH;
    }
}
