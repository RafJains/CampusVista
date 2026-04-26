package com.example.campusvista.ui.place;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.location.SetLocationActivity;
import com.example.campusvista.ui.route.RouteOptionsActivity;

public final class PlaceDetailsActivity extends Activity {
    private Place place;
    private Checkpoint checkpoint;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_details);

        String placeId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        CampusVistaApp app = (CampusVistaApp) getApplication();
        place = placeId == null ? null : app.getPlaceRepository().getPlaceById(placeId);
        if (place == null) {
            Toast.makeText(this, "Place not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        checkpoint = app.getCheckpointRepository().getCheckpointById(place.getCheckpointId());
        bindPlace();

        findViewById(R.id.setCurrentButton).setOnClickListener(view -> {
            LocationStore.setCurrentCheckpointId(this, place.getCheckpointId());
            Toast.makeText(this, "Current location set.", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.routeHereButton).setOnClickListener(view -> {
            if (LocationStore.getCurrentCheckpointId(this) == null) {
                Toast.makeText(this, "Set current location first.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, SetLocationActivity.class));
                return;
            }
            Intent intent = new Intent(this, RouteOptionsActivity.class);
            intent.putExtra(NavExtras.EXTRA_PLACE_ID, place.getPlaceId());
            intent.putExtra(NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID, place.getCheckpointId());
            intent.putExtra(NavExtras.EXTRA_DESTINATION_NAME, place.getPlaceName());
            startActivity(intent);
        });
    }

    private void bindPlace() {
        ((TextView) findViewById(R.id.placeName)).setText(place.getPlaceName());
        ((TextView) findViewById(R.id.placeType)).setText(UiText.cleanType(place.getPlaceType()));
        ((TextView) findViewById(R.id.placeDescription)).setText(
                place.getDescription() == null ? "" : place.getDescription()
        );
        ((TextView) findViewById(R.id.placeCheckpoint)).setText(checkpoint == null
                ? "Checkpoint: " + place.getCheckpointId()
                : "Checkpoint: " + checkpoint.getCheckpointName());
    }
}
