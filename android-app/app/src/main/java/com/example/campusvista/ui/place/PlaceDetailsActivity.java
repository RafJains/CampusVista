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
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.CheckpointDto;
import com.example.campusvista.network.BackendDtos.PanoDto;
import com.example.campusvista.network.BackendDtos.PlaceDto;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.location.SetLocationActivity;
import com.example.campusvista.ui.pano.OutdoorPanoActivity;
import com.example.campusvista.ui.route.RoutePreviewActivity;

public final class PlaceDetailsActivity extends Activity {
    private Place place;
    private Checkpoint checkpoint;
    private boolean actionsBound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_details);

        String placeId = getIntent().getStringExtra(NavExtras.EXTRA_PLACE_ID);
        if (placeId == null) {
            Toast.makeText(this, "Place not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.placeName)).setText("Loading place...");
        ViewFactory.setVisible(findViewById(R.id.placePanoButton), false);
        loadPlaceFromBackend(placeId);
    }

    private void loadPlaceFromBackend(String placeId) {
        BackendClient.getInstance(this).getPlace(placeId, new BackendCallback<PlaceDto>() {
            @Override
            public void onSuccess(PlaceDto value) {
                place = BackendMapper.toPlace(value);
                loadCheckpointFromBackend(place.getCheckpointId());
            }

            @Override
            public void onFallback(Throwable throwable) {
                loadPlaceLocal(placeId);
            }
        });
    }

    private void loadPlaceLocal(String placeId) {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        place = app.getPlaceRepository().getPlaceById(placeId);
        if (place == null) {
            Toast.makeText(this, "Place not found.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        checkpoint = app.getCheckpointRepository().getCheckpointById(place.getCheckpointId());
        bindPlace();
        bindActions();
        bindPanoAvailabilityLocal();
    }

    private void loadCheckpointFromBackend(String checkpointId) {
        BackendClient.getInstance(this).getCheckpoint(
                checkpointId,
                new BackendCallback<CheckpointDto>() {
                    @Override
                    public void onSuccess(CheckpointDto value) {
                        checkpoint = BackendMapper.toCheckpoint(value);
                        bindPlace();
                        bindActions();
                        bindPanoAvailabilityFromBackend();
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        checkpoint = ((CampusVistaApp) getApplication())
                                .getCheckpointRepository()
                                .getCheckpointById(checkpointId);
                        bindPlace();
                        bindActions();
                        bindPanoAvailabilityLocal();
                    }
                }
        );
    }

    private void bindPanoAvailabilityFromBackend() {
        if (checkpoint == null) {
            ViewFactory.setVisible(findViewById(R.id.placePanoButton), false);
            return;
        }
        BackendClient.getInstance(this).getPano(
                checkpoint.getCheckpointId(),
                new BackendCallback<PanoDto>() {
                    @Override
                    public void onSuccess(PanoDto value) {
                        ViewFactory.setVisible(findViewById(R.id.placePanoButton), true);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        bindPanoAvailabilityLocal();
                    }
                }
        );
    }

    private void bindPanoAvailabilityLocal() {
        boolean hasPano = checkpoint != null
                && ((CampusVistaApp) getApplication()).getPanoRepository()
                .hasOutdoorPano(checkpoint.getCheckpointId());
        ViewFactory.setVisible(findViewById(R.id.placePanoButton), hasPano);
    }

    private void bindActions() {
        if (actionsBound) {
            return;
        }
        actionsBound = true;

        findViewById(R.id.placePanoButton).setOnClickListener(view -> {
            if (checkpoint == null) {
                return;
            }
            Intent intent = new Intent(this, OutdoorPanoActivity.class);
            intent.putExtra(NavExtras.EXTRA_CHECKPOINT_ID, checkpoint.getCheckpointId());
            intent.putExtra(NavExtras.EXTRA_CHECKPOINT_NAME, checkpoint.getCheckpointName());
            startActivity(intent);
        });

        findViewById(R.id.setCurrentButton).setOnClickListener(view -> {
            if (place == null) {
                return;
            }
            LocationStore.setCurrentCheckpointId(this, place.getCheckpointId());
            Toast.makeText(this, "Start set.", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.routeHereButton).setOnClickListener(view -> {
            if (place == null) {
                return;
            }
            if (LocationStore.getCurrentCheckpointId(this) == null) {
                Toast.makeText(this, "Choose a start first.", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, SetLocationActivity.class));
                return;
            }
            Intent intent = new Intent(this, RoutePreviewActivity.class);
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
    }
}
