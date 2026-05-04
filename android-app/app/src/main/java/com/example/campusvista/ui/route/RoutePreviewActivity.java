package com.example.campusvista.ui.route;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.RouteRequestDto;
import com.example.campusvista.network.BackendDtos.RouteResponseDto;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.routing.RouteResult;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.navigation.OutdoorNavActivity;

import java.util.Locale;

public final class RoutePreviewActivity extends Activity {
    private String destinationCheckpointId;
    private String destinationName;
    private String destinationPlaceId;
    private RouteMode routeMode;
    private RouteResult routeResult;
    private boolean warningsShown;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_preview);

        destinationPlaceId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        destinationCheckpointId = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID);
        destinationName = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_NAME);
        routeMode = RouteMode.SHORTEST_PATH;

        findViewById(R.id.navigationStepsButton).setOnClickListener(view -> openNavigation(false));
        findViewById(R.id.panoModeButton).setOnClickListener(view -> openNavigation(true));
        setRouteActionsEnabled(false);

        computeAndBindRoute();
    }

    private void computeAndBindRoute() {
        String startId = LocationStore.getCurrentCheckpointId(this);
        if (startId == null || destinationCheckpointId == null) {
            Toast.makeText(this, "Start or destination is missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.previewStatus)).setText("Calculating route...");
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
                        bindRouteOrUnavailable(startId);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        routeResult = computeLocalRoute(startId);
                        bindRouteOrUnavailable(startId);
                    }
                }
        );
    }

    private RouteResult computeLocalRoute(String startId) {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        return app.getRoutePlanner().computeRoute(
                startId,
                destinationCheckpointId,
                routeMode,
                destinationName
        );
    }

    private void bindRouteOrUnavailable(String startId) {
        if (!routeResult.isRouteFound()) {
            bindUnavailable();
            return;
        }

        CampusVistaApp app = (CampusVistaApp) getApplication();
        Checkpoint start = app.getCheckpointRepository().getCheckpointById(startId);
        Checkpoint destination = app.getCheckpointRepository()
                .getCheckpointById(destinationCheckpointId);
        if (routeResult.getCheckpointPath() != null && !routeResult.getCheckpointPath().isEmpty()) {
            start = routeResult.getCheckpointPath().get(0);
            destination = routeResult.getCheckpointPath()
                    .get(routeResult.getCheckpointPath().size() - 1);
        }

        bindMetrics(
                checkpointName(start, startId),
                destinationName == null ? checkpointName(destination, destinationCheckpointId) : destinationName,
                routeResult.getTotalDistanceMeters()
        );
        ((TextView) findViewById(R.id.previewStatus)).setText("");
        setRouteActionsEnabled(true);
        showCrowdWarningsIfNeeded();
    }

    private void bindUnavailable() {
        ((TextView) findViewById(R.id.previewStatus)).setText(
                "No route found. Choose a different start or end."
        );
        setRouteActionsEnabled(false);
    }

    private void bindMetrics(String startName, String endName, double distanceMeters) {
        ((TextView) findViewById(R.id.previewStart)).setText("Start\n" + startName);
        ((TextView) findViewById(R.id.previewEnd)).setText("End\n" + endName);
        ((TextView) findViewById(R.id.previewDistance)).setText(
                "Distance\n" + String.format(Locale.US, "%.0f m", distanceMeters)
        );
    }

    private void setRouteActionsEnabled(boolean enabled) {
        findViewById(R.id.navigationStepsButton).setEnabled(enabled);
        findViewById(R.id.panoModeButton).setEnabled(enabled);
    }

    private void openNavigation(boolean startInPano) {
        if (routeResult == null || !routeResult.isRouteFound()) {
            return;
        }
        Intent intent = new Intent(this, OutdoorNavActivity.class);
        intent.putExtra(NavExtras.EXTRA_PLACE_ID, destinationPlaceId);
        intent.putExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID, destinationCheckpointId);
        intent.putExtra(NavExtras.EXTRA_DESTINATION_NAME, destinationName);
        intent.putExtra(NavExtras.EXTRA_START_IN_PANO, startInPano);
        startActivity(intent);
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
