package com.example.campusvista.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.data.repository.MapConfigRepository;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.location.SetLocationActivity;
import com.example.campusvista.ui.place.PlaceDetailsActivity;
import com.example.campusvista.ui.search.SearchActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public final class HomeMapActivity extends Activity {
    private ImageView campusMapImage;
    private TextView currentLocationLabel;
    private TextView mapSummary;
    private LinearLayout categoryRow;
    private LinearLayout featuredPlacesList;
    private LinearLayout checkpointList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_map);

        campusMapImage = findViewById(R.id.campusMapImage);
        currentLocationLabel = findViewById(R.id.currentLocationLabel);
        mapSummary = findViewById(R.id.mapSummary);
        categoryRow = findViewById(R.id.categoryRow);
        featuredPlacesList = findViewById(R.id.featuredPlacesList);
        checkpointList = findViewById(R.id.checkpointList);

        findViewById(R.id.openSearchButton).setOnClickListener(view ->
                startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.setLocationButton).setOnClickListener(view ->
                startActivity(new Intent(this, SetLocationActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindHomeData();
    }

    private void bindHomeData() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        MapConfigRepository.MapConfig config = app.getMapConfig();
        List<Checkpoint> checkpoints = app.getCheckpointRepository().getAllCheckpoints();
        List<Place> places = app.getPlaceRepository().getAllPlaces();

        String currentId = LocationStore.getCurrentCheckpointId(this);
        Checkpoint current = currentId == null
                ? null
                : app.getCheckpointRepository().getCheckpointById(currentId);
        currentLocationLabel.setText(current == null
                ? "Current location: Not set"
                : "Current location: " + current.getCheckpointName());

        boolean mapLoaded = loadCampusMap(config);
        mapSummary.setText((mapLoaded ? "Campus map" : "Campus map asset unavailable")
                + "\n" + config.getCampusMapWidthPx() + " x " + config.getCampusMapHeightPx() + " px"
                + "\n" + checkpoints.size() + " checkpoints ready");

        bindCategories();
        bindPlaces(places);
        bindCheckpoints(checkpoints);
    }

    private void bindCategories() {
        categoryRow.removeAllViews();
        String[] categories = {
                "gate", "building", "canteen", "library", "parking", "landmark", "facility"
        };
        for (String category : categories) {
            Button chip = ViewFactory.chipButton(this, UiText.cleanType(category));
            chip.setOnClickListener(view -> {
                Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_INITIAL_TYPE, category);
                startActivity(intent);
            });
            categoryRow.addView(chip);
        }
    }

    private void bindPlaces(List<Place> places) {
        featuredPlacesList.removeAllViews();
        int count = Math.min(places.size(), 8);
        for (int i = 0; i < count; i++) {
            Place place = places.get(i);
            Button button = ViewFactory.listButton(
                    this,
                    place.getPlaceName() + "  -  " + UiText.cleanType(place.getPlaceType())
            );
            button.setOnClickListener(view -> openPlace(place.getPlaceId()));
            featuredPlacesList.addView(button);
        }
    }

    private void bindCheckpoints(List<Checkpoint> checkpoints) {
        checkpointList.removeAllViews();
        int count = Math.min(checkpoints.size(), 8);
        for (int i = 0; i < count; i++) {
            Checkpoint checkpoint = checkpoints.get(i);
            checkpointList.addView(ViewFactory.cardText(
                    this,
                    checkpoint.getCheckpointName(),
                    UiText.cleanType(checkpoint.getCheckpointType())
                            + " - x " + Math.round(checkpoint.getXCoord())
                            + ", y " + Math.round(checkpoint.getYCoord())
            ));
        }
    }

    private void openPlace(String placeId) {
        Intent intent = new Intent(this, PlaceDetailsActivity.class);
        intent.putExtra(NavExtras.EXTRA_PLACE_ID, placeId);
        startActivity(intent);
    }

    private boolean loadCampusMap(MapConfigRepository.MapConfig config) {
        String assetPath = "maps/" + config.getCampusMapFile();
        try (InputStream inputStream = getAssets().open(assetPath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                campusMapImage.setVisibility(View.GONE);
                return false;
            }
            campusMapImage.setVisibility(View.VISIBLE);
            campusMapImage.setImageBitmap(bitmap);
            return true;
        } catch (IOException exception) {
            campusMapImage.setVisibility(View.GONE);
            return false;
        }
    }
}
