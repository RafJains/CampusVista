package com.example.campusvista.ui.search;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Place;
import com.example.campusvista.ui.common.NavExtras;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.place.PlaceDetailsActivity;

import java.util.List;

public final class SearchActivity extends Activity {
    public static final String EXTRA_INITIAL_TYPE = "com.example.campusvista.INITIAL_TYPE";
    private static final int RESULT_LIMIT = 25;

    private EditText searchInput;
    private TextView emptyState;
    private LinearLayout resultList;
    private String initialType;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        searchInput = findViewById(R.id.searchInput);
        emptyState = findViewById(R.id.searchEmptyState);
        resultList = findViewById(R.id.searchResultList);
        initialType = getIntent().getStringExtra(EXTRA_INITIAL_TYPE);

        Button searchButton = findViewById(R.id.searchButton);
        searchButton.setOnClickListener(view -> runSearch());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_DONE) {
                runSearch();
                return true;
            }
            return false;
        });

        if (initialType != null) {
            searchInput.setHint(UiText.cleanType(initialType));
        }
        runSearch();
    }

    private void runSearch() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        String query = searchInput.getText().toString().trim();
        List<Place> places;

        if (query.isEmpty() && initialType != null) {
            places = app.getPlaceRepository().getPlacesByType(initialType);
        } else if (initialType != null) {
            places = app.getPlaceRepository().searchPlacesByType(query, initialType, RESULT_LIMIT);
        } else if (query.isEmpty()) {
            places = app.getPlaceRepository().getAllPlaces();
        } else {
            places = app.getPlaceRepository().searchPlaces(query, RESULT_LIMIT);
        }

        bindResults(places);
    }

    private void bindResults(List<Place> places) {
        resultList.removeAllViews();
        emptyState.setText(places.isEmpty() ? "No outdoor places found." : "");

        for (Place place : places) {
            Button button = ViewFactory.listButton(
                    this,
                    place.getPlaceName() + "\n"
                            + UiText.cleanType(place.getPlaceType()) + "  -  "
                            + place.getCheckpointId()
            );
            button.setOnClickListener(view -> {
                Intent intent = new Intent(this, PlaceDetailsActivity.class);
                intent.putExtra(NavExtras.EXTRA_PLACE_ID, place.getPlaceId());
                startActivity(intent);
            });
            resultList.addView(button);
        }
    }
}
