package com.example.campusvista.ui.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
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
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.NearestCheckpointDto;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.location.PhotoRecognitionActivity;
import com.example.campusvista.ui.place.PlaceDetailsActivity;
import com.example.campusvista.ui.route.RoutePreviewActivity;
import com.example.campusvista.ui.search.SearchActivity;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class HomeMapActivity extends Activity {
    private static final float MAP_MAX_ZOOM_MULTIPLIER = 5f;

    private ImageView campusMapImage;
    private TextView startLocationField;
    private TextView endLocationField;
    private LinearLayout categoryRow;
    private LinearLayout featuredPlacesList;
    private Place selectedDestinationPlace;
    private final Matrix mapMatrix = new Matrix();
    private ScaleGestureDetector mapScaleDetector;
    private GestureDetector mapGestureDetector;
    private Bitmap campusMapBitmap;
    private float mapMinScale;
    private float mapMaxScale;
    private float mapScale;
    private float mapTranslateX;
    private float mapTranslateY;
    private float mapLastX;
    private float mapLastY;
    private float mapLastFocusX;
    private float mapLastFocusY;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home_map);

        campusMapImage = findViewById(R.id.campusMapImage);
        startLocationField = findViewById(R.id.startLocationField);
        endLocationField = findViewById(R.id.endLocationField);
        categoryRow = findViewById(R.id.categoryRow);
        featuredPlacesList = findViewById(R.id.featuredPlacesList);
        configureMapGestures();

        startLocationField.setOnClickListener(view -> showStartPicker());
        endLocationField.setOnClickListener(view -> showEndPicker());
        findViewById(R.id.recognizeLocationButton).setOnClickListener(view ->
                startActivity(new Intent(this, PhotoRecognitionActivity.class)));
        findViewById(R.id.startNavigationButton).setOnClickListener(view -> startSelectedRoute());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindHomeData();
    }

    private void bindHomeData() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        MapConfigRepository.MapConfig config = app.getMapConfig();
        List<Place> places = app.getPlaceRepository().getAllPlaces();

        loadCampusMap(config);
        bindSelectionLabels();

        bindCategories(places);
        bindPlaces(places);
    }

    private void bindSelectionLabels() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        String currentId = LocationStore.getCurrentCheckpointId(this);
        Checkpoint current = currentId == null
                ? null
                : app.getCheckpointRepository().getCheckpointById(currentId);
        startLocationField.setText(current == null
                ? "Start\nChoose location"
                : "Start\n" + current.getCheckpointName());
        endLocationField.setText(selectedDestinationPlace == null
                ? "End\nChoose destination"
                : "End\n" + selectedDestinationPlace.getPlaceName());
    }

    private void bindCategories(List<Place> places) {
        categoryRow.removeAllViews();
        Set<String> placeTypes = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Place place : places) {
            String placeType = place.getPlaceType();
            if (placeType != null && !placeType.trim().isEmpty()) {
                placeTypes.add(placeType.trim());
            }
        }
        for (String placeType : placeTypes) {
            Button chip = ViewFactory.chipButton(this, UiText.cleanType(placeType));
            chip.setOnClickListener(view -> {
                Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_INITIAL_TYPE, placeType);
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
                    place.getPlaceName() + "\n" + UiText.cleanType(place.getPlaceType())
            );
            button.setOnClickListener(view -> openPlace(place.getPlaceId()));
            featuredPlacesList.addView(button);
        }
    }

    private void openPlace(String placeId) {
        Intent intent = new Intent(this, PlaceDetailsActivity.class);
        intent.putExtra(NavExtras.EXTRA_PLACE_ID, placeId);
        startActivity(intent);
    }

    private void showStartPicker() {
        List<Checkpoint> checkpoints = ((CampusVistaApp) getApplication())
                .getCheckpointRepository()
                .getAllCheckpoints();
        if (checkpoints.isEmpty()) {
            Toast.makeText(this, "No start locations available.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[checkpoints.size()];
        for (int i = 0; i < checkpoints.size(); i++) {
            names[i] = checkpoints.get(i).getCheckpointName();
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose start")
                .setItems(names, (dialog, which) ->
                        setLocationViaNearestCheckpoint(checkpoints.get(which)))
                .show();
    }

    private void showEndPicker() {
        List<Place> places = ((CampusVistaApp) getApplication())
                .getPlaceRepository()
                .getAllPlaces();
        if (places.isEmpty()) {
            Toast.makeText(this, "No destinations available.", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[places.size()];
        for (int i = 0; i < places.size(); i++) {
            names[i] = places.get(i).getPlaceName();
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose end")
                .setItems(names, (dialog, which) -> {
                    selectedDestinationPlace = places.get(which);
                    bindSelectionLabels();
                })
                .show();
    }

    private void startSelectedRoute() {
        if (LocationStore.getCurrentCheckpointId(this) == null) {
            Toast.makeText(this, "Choose a start location.", Toast.LENGTH_SHORT).show();
            showStartPicker();
            return;
        }
        if (selectedDestinationPlace == null) {
            Toast.makeText(this, "Choose a destination.", Toast.LENGTH_SHORT).show();
            showEndPicker();
            return;
        }
        Intent intent = new Intent(this, RoutePreviewActivity.class);
        intent.putExtra(NavExtras.EXTRA_PLACE_ID, selectedDestinationPlace.getPlaceId());
        intent.putExtra(
                NavExtras.EXTRA_DESTINATION_CHECKPOINT_ID,
                selectedDestinationPlace.getCheckpointId()
        );
        intent.putExtra(NavExtras.EXTRA_DESTINATION_NAME, selectedDestinationPlace.getPlaceName());
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
            campusMapImage.setAdjustViewBounds(false);
            campusMapImage.setScaleType(ImageView.ScaleType.MATRIX);
            campusMapImage.setClickable(true);
            campusMapBitmap = bitmap;
            campusMapImage.post(this::resetMapViewport);
            return true;
        } catch (IOException exception) {
            campusMapBitmap = null;
            campusMapImage.setVisibility(View.GONE);
            return false;
        }
    }

    private void configureMapGestures() {
        campusMapImage.setScaleType(ImageView.ScaleType.MATRIX);
        mapScaleDetector = new ScaleGestureDetector(
                this,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScaleBegin(ScaleGestureDetector detector) {
                        mapLastFocusX = detector.getFocusX();
                        mapLastFocusY = detector.getFocusY();
                        requestMapParentInterception(true);
                        return true;
                    }

                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float focusX = detector.getFocusX();
                        float focusY = detector.getFocusY();
                        panMap(focusX - mapLastFocusX, focusY - mapLastFocusY);
                        mapLastFocusX = focusX;
                        mapLastFocusY = focusY;
                        zoomMap(detector.getScaleFactor(), focusX, focusY);
                        return true;
                    }
                }
        );
        mapGestureDetector = new GestureDetector(
                this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(MotionEvent event) {
                        snapMapTouchToCheckpoint(event.getX(), event.getY());
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        if (mapScale <= mapMinScale * 1.2f) {
                            setMapScale(mapMinScale * 2.5f, event.getX(), event.getY());
                        } else {
                            resetMapViewport();
                        }
                        return true;
                    }
                }
        );
        campusMapImage.setOnTouchListener((view, event) -> handleMapTouch(event));
    }

    private boolean handleMapTouch(MotionEvent event) {
        if (campusMapBitmap == null) {
            return false;
        }

        mapScaleDetector.onTouchEvent(event);
        mapGestureDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mapLastX = event.getX();
                mapLastY = event.getY();
                requestMapParentInterception(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!mapScaleDetector.isInProgress() && event.getPointerCount() == 1) {
                    float dx = event.getX() - mapLastX;
                    float dy = event.getY() - mapLastY;
                    mapLastX = event.getX();
                    mapLastY = event.getY();
                    panMap(dx, dy);
                }
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                mapLastFocusX = mapScaleDetector.getFocusX();
                mapLastFocusY = mapScaleDetector.getFocusY();
                requestMapParentInterception(true);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                updateLastMapPointAfterPointerUp(event);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestMapParentInterception(false);
                return true;
            default:
                return true;
        }
    }

    private void resetMapViewport() {
        if (campusMapBitmap == null) {
            return;
        }
        int viewWidth = campusMapImage.getWidth();
        int viewHeight = campusMapImage.getHeight();
        if (viewWidth <= 0 || viewHeight <= 0) {
            return;
        }

        mapMinScale = Math.min(
                (float) viewWidth / campusMapBitmap.getWidth(),
                (float) viewHeight / campusMapBitmap.getHeight()
        );
        mapMaxScale = mapMinScale * MAP_MAX_ZOOM_MULTIPLIER;
        mapScale = mapMinScale;
        mapTranslateX = (viewWidth - campusMapBitmap.getWidth() * mapScale) / 2f;
        mapTranslateY = (viewHeight - campusMapBitmap.getHeight() * mapScale) / 2f;
        applyMapMatrix();
    }

    private void zoomMap(float scaleFactor, float focusX, float focusY) {
        if (mapScale <= 0f) {
            resetMapViewport();
        }
        setMapScale(mapScale * scaleFactor, focusX, focusY);
    }

    private void setMapScale(float targetScale, float focusX, float focusY) {
        if (campusMapBitmap == null || mapScale <= 0f) {
            return;
        }

        float nextScale = Math.max(mapMinScale, Math.min(mapMaxScale, targetScale));
        float mapFocusX = (focusX - mapTranslateX) / mapScale;
        float mapFocusY = (focusY - mapTranslateY) / mapScale;
        mapTranslateX = focusX - mapFocusX * nextScale;
        mapTranslateY = focusY - mapFocusY * nextScale;
        mapScale = nextScale;
        clampMapToViewport();
        applyMapMatrix();
    }

    private void panMap(float dx, float dy) {
        if (campusMapBitmap == null || mapScale <= 0f) {
            return;
        }
        mapTranslateX += dx;
        mapTranslateY += dy;
        clampMapToViewport();
        applyMapMatrix();
    }

    private void updateLastMapPointAfterPointerUp(MotionEvent event) {
        int liftedIndex = event.getActionIndex();
        int nextIndex = liftedIndex == 0 ? 1 : 0;
        if (nextIndex < event.getPointerCount()) {
            mapLastX = event.getX(nextIndex);
            mapLastY = event.getY(nextIndex);
        }
    }

    private void clampMapToViewport() {
        int viewWidth = campusMapImage.getWidth();
        int viewHeight = campusMapImage.getHeight();
        float scaledWidth = campusMapBitmap.getWidth() * mapScale;
        float scaledHeight = campusMapBitmap.getHeight() * mapScale;

        if (scaledWidth <= viewWidth) {
            mapTranslateX = (viewWidth - scaledWidth) / 2f;
        } else {
            mapTranslateX = Math.max(viewWidth - scaledWidth, Math.min(0f, mapTranslateX));
        }

        if (scaledHeight <= viewHeight) {
            mapTranslateY = (viewHeight - scaledHeight) / 2f;
        } else {
            mapTranslateY = Math.max(viewHeight - scaledHeight, Math.min(0f, mapTranslateY));
        }
    }

    private void applyMapMatrix() {
        mapMatrix.reset();
        mapMatrix.postScale(mapScale, mapScale);
        mapMatrix.postTranslate(mapTranslateX, mapTranslateY);
        campusMapImage.setImageMatrix(mapMatrix);
    }

    private void requestMapParentInterception(boolean disallowIntercept) {
        if (campusMapImage.getParent() != null) {
            campusMapImage.getParent().requestDisallowInterceptTouchEvent(disallowIntercept);
        }
    }

    private void snapMapTouchToCheckpoint(float touchX, float touchY) {
        double[] mapPoint = viewPointToMapPoint(touchX, touchY);
        if (mapPoint == null) {
            return;
        }

        double mapX = mapPoint[0];
        double mapY = mapPoint[1];
        BackendClient.getInstance(this).getNearestCheckpoint(
                mapX,
                mapY,
                new BackendCallback<NearestCheckpointDto>() {
                    @Override
                    public void onSuccess(NearestCheckpointDto value) {
                        Checkpoint checkpoint = BackendMapper.toCheckpoint(value.checkpoint);
                        if (checkpoint != null) {
                            setCurrentFromMap(checkpoint);
                        }
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        Checkpoint checkpoint = ((CampusVistaApp) getApplication())
                                .getNearestCheckpointFinder()
                                .findNearest(mapX, mapY);
                        if (checkpoint != null) {
                            setCurrentFromMap(checkpoint);
                        }
                    }
                }
        );
    }

    private double[] viewPointToMapPoint(float touchX, float touchY) {
        if (campusMapBitmap == null || mapScale <= 0f) {
            return null;
        }

        double mapX = (touchX - mapTranslateX) / mapScale;
        double mapY = (touchY - mapTranslateY) / mapScale;
        if (mapX < 0 || mapY < 0
                || mapX > campusMapBitmap.getWidth()
                || mapY > campusMapBitmap.getHeight()) {
            return null;
        }
        return new double[]{mapX, mapY};
    }

    private void setLocationViaNearestCheckpoint(Checkpoint selectedCheckpoint) {
        BackendClient.getInstance(this).getNearestCheckpoint(
                selectedCheckpoint.getMapX(),
                selectedCheckpoint.getMapY(),
                new BackendCallback<NearestCheckpointDto>() {
                    @Override
                    public void onSuccess(NearestCheckpointDto value) {
                        Checkpoint nearest = BackendMapper.toCheckpoint(value.checkpoint);
                        setCurrentFromMap(nearest == null ? selectedCheckpoint : nearest);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        setCurrentFromMap(selectedCheckpoint);
                    }
                }
        );
    }

    private void setCurrentFromMap(Checkpoint checkpoint) {
        LocationStore.setCurrentCheckpointId(this, checkpoint.getCheckpointId());
        Toast.makeText(
                this,
                "Start set: " + checkpoint.getCheckpointName(),
                Toast.LENGTH_SHORT
        ).show();
        bindSelectionLabels();
    }
}
