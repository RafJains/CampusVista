package com.example.campusvista.ui.home;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.data.repository.MapConfigRepository;
import com.example.campusvista.network.BackendCallback;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.network.dto.BackendNearestCheckpointDto;
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
        campusMapImage.setOnTouchListener((view, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                snapMapTouchToCheckpoint(event.getX(), event.getY());
            }
            return true;
        });
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
            campusMapImage.setClickable(true);
            return true;
        } catch (IOException exception) {
            campusMapImage.setVisibility(View.GONE);
            return false;
        }
    }

    private void snapMapTouchToCheckpoint(float touchX, float touchY) {
        Drawable drawable = campusMapImage.getDrawable();
        if (drawable == null) {
            return;
        }

        int imageWidth = drawable.getIntrinsicWidth();
        int imageHeight = drawable.getIntrinsicHeight();
        int viewWidth = campusMapImage.getWidth();
        int viewHeight = campusMapImage.getHeight();
        if (imageWidth <= 0 || imageHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        float scale = Math.min((float) viewWidth / imageWidth, (float) viewHeight / imageHeight);
        float displayedWidth = imageWidth * scale;
        float displayedHeight = imageHeight * scale;
        float left = (viewWidth - displayedWidth) / 2f;
        float top = (viewHeight - displayedHeight) / 2f;
        if (touchX < left || touchX > left + displayedWidth
                || touchY < top || touchY > top + displayedHeight) {
            return;
        }

        double mapX = (touchX - left) / scale;
        double mapY = (touchY - top) / scale;
        BackendClient.getInstance(this).getNearestCheckpoint(
                mapX,
                mapY,
                new BackendCallback<BackendNearestCheckpointDto>() {
                    @Override
                    public void onSuccess(BackendNearestCheckpointDto value) {
                        Checkpoint checkpoint = BackendMapper.toCheckpoint(value.checkpoint);
                        if (checkpoint != null) {
                            setCurrentFromMap(checkpoint, "Python backend");
                        }
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        Checkpoint checkpoint = ((CampusVistaApp) getApplication())
                                .getNearestCheckpointFinder()
                                .findNearest(mapX, mapY);
                        if (checkpoint != null) {
                            setCurrentFromMap(checkpoint, "offline fallback");
                        }
                    }
                }
        );
    }

    private void setCurrentFromMap(Checkpoint checkpoint, String source) {
        LocationStore.setCurrentCheckpointId(this, checkpoint.getCheckpointId());
        Toast.makeText(
                this,
                "Snapped to " + checkpoint.getCheckpointName() + " via " + source,
                Toast.LENGTH_SHORT
        ).show();
        bindHomeData();
    }
}
