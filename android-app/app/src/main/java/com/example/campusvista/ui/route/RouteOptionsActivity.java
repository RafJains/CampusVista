package com.example.campusvista.ui.route;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.routing.RouteMode;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.location.SetLocationActivity;

public final class RouteOptionsActivity extends Activity {
    private String destinationCheckpointId;
    private String destinationName;
    private String destinationPlaceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_options);

        resolveDestination();
        if (destinationCheckpointId == null) {
            Toast.makeText(this, "Destination not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindRouteLabels();

        findViewById(R.id.shortestPathButton).setOnClickListener(view ->
                openPreview(RouteMode.SHORTEST_PATH));
        findViewById(R.id.changeStartButton).setOnClickListener(view ->
                startActivity(new Intent(this, SetLocationActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindRouteLabels();
    }

    private void resolveDestination() {
        destinationCheckpointId = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID);
        destinationName = getIntent().getStringExtra(NavExtras.EXTRA_DESTINATION_NAME);

        destinationPlaceId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        if (destinationCheckpointId == null && destinationPlaceId != null) {
            Place place = ((CampusVistaApp) getApplication())
                    .getPlaceRepository()
                    .getPlaceById(destinationPlaceId);
            if (place != null) {
                destinationCheckpointId = place.getCheckpointId();
                destinationName = place.getPlaceName();
            }
        }
    }

    private void bindRouteLabels() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        String currentId = LocationStore.getCurrentCheckpointId(this);
        Checkpoint current = currentId == null
                ? null
                : app.getCheckpointRepository().getCheckpointById(currentId);
        Checkpoint destination = app.getCheckpointRepository()
                .getCheckpointById(destinationCheckpointId);

        ((TextView) findViewById(R.id.routeStart)).setText(current == null
                ? "Start: Not set"
                : "Start: " + current.getCheckpointName());
        ((TextView) findViewById(R.id.routeDestination)).setText(destination == null
                ? "Destination: " + destinationName
                : "Destination: " + destinationName + "\nCheckpoint: "
                        + destination.getCheckpointName());

        boolean hasStart = current != null;
        findViewById(R.id.shortestPathButton).setEnabled(hasStart);
    }

    private void openPreview(RouteMode routeMode) {
        String currentId = LocationStore.getCurrentCheckpointId(this);
        if (currentId == null) {
            Toast.makeText(this, "Set current location first.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, SetLocationActivity.class));
            return;
        }
        Intent intent = new Intent(this, RoutePreviewActivity.class);
        intent.putExtra(NavExtras.EXTRA_PLACE_ID, destinationPlaceId);
        intent.putExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID, destinationCheckpointId);
        intent.putExtra(NavExtras.EXTRA_DESTINATION_NAME, destinationName);
        intent.putExtra(NavExtras.EXTRA_ROUTE_MODE, routeMode.name());
        startActivity(intent);
    }
}
