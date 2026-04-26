package com.example.campusvista.ui.route;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
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
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.navigation.OutdoorNavActivity;

import java.util.List;
import java.util.Locale;

public final class RoutePreviewActivity extends Activity {
    private String destinationCheckpointId;
    private String destinationName;
    private String destinationPlaceId;
    private RouteMode routeMode;
    private RouteResult routeResult;
    private LinearLayout routeStepsList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_preview);

        routeStepsList = findViewById(R.id.routeStepsList);
        destinationPlaceId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        destinationCheckpointId = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID);
        destinationName = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_NAME);
        routeMode = parseRouteMode(getIntent().getStringExtra(NavExtras.EXTRA_ROUTE_MODE));

        findViewById(R.id.startNavigationButton).setOnClickListener(view -> openNavigation());
        findViewById(R.id.changeRouteButton).setOnClickListener(view -> finish());

        computeAndBindRoute();
    }

    private void computeAndBindRoute() {
        String startId = LocationStore.getCurrentCheckpointId(this);
        if (startId == null || destinationCheckpointId == null) {
            Toast.makeText(this, "Start or destination is missing.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.previewMeta)).setText(
                "Calculating route with Python backend..."
        );
        routeStepsList.removeAllViews();
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
                        bindRouteOrUnavailable(startId, "Python backend (" + value.algorithm + ")");
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        routeResult = computeLocalRoute(startId);
                        bindRouteOrUnavailable(startId, "Offline fallback");
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

    private void bindRouteOrUnavailable(String startId, String sourceLabel) {
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

        ((TextView) findViewById(R.id.previewMeta)).setText(
                "Start: " + checkpointName(start, startId)
                        + "\nDestination: " + destinationName
                        + "\nCheckpoint: " + checkpointName(destination, destinationCheckpointId)
                        + "\nMode: " + UiText.routeModeLabel(routeMode)
                        + "\nSource: " + sourceLabel
                        + "\nDistance: " + String.format(
                                Locale.US,
                                "%.0f m",
                                routeResult.getTotalDistanceMeters()
                        )
                        + "\nCost: " + String.format(Locale.US, "%.0f", routeResult.getTotalCost())
        );

        routeStepsList.removeAllViews();
        List<String> instructions = routeResult.getInstructions();
        for (int i = 0; i < instructions.size(); i++) {
            routeStepsList.addView(ViewFactory.numberedStep(this, i + 1, instructions.get(i)));
        }
        ViewFactory.setVisible(findViewById(R.id.startNavigationButton), true);
    }

    private void bindUnavailable() {
        ((TextView) findViewById(R.id.previewMeta)).setText(
                "No outdoor route found between these points."
        );
        routeStepsList.removeAllViews();
        routeStepsList.addView(ViewFactory.cardText(this, "Choose another start point.", null));
        routeStepsList.addView(ViewFactory.cardText(this, "Choose another destination.", null));
        routeStepsList.addView(ViewFactory.cardText(this, "Return to map.", null));
        ViewFactory.setVisible(findViewById(R.id.startNavigationButton), false);
    }

    private void openNavigation() {
        if (routeResult == null || !routeResult.isRouteFound()) {
            return;
        }
        Intent intent = new Intent(this, OutdoorNavActivity.class);
        intent.putExtra(NavExtras.EXTRA_PLACE_ID, destinationPlaceId);
        intent.putExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID, destinationCheckpointId);
        intent.putExtra(NavExtras.EXTRA_DESTINATION_NAME, destinationName);
        intent.putExtra(NavExtras.EXTRA_ROUTE_MODE, routeMode.name());
        startActivity(intent);
    }

    private static RouteMode parseRouteMode(String value) {
        if (RouteMode.AVOID_CROWDED_PATH.name().equals(value)) {
            return RouteMode.AVOID_CROWDED_PATH;
        }
        return RouteMode.SHORTEST_PATH;
    }

    private static String checkpointName(Checkpoint checkpoint, String fallbackId) {
        return checkpoint == null ? fallbackId : checkpoint.getCheckpointName();
    }
}
