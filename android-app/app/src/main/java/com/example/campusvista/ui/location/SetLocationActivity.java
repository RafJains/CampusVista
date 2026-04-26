package com.example.campusvista.ui.location;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.home.HomeMapActivity;
import com.example.campusvista.ui.search.SearchActivity;

import java.util.List;

public final class SetLocationActivity extends Activity {
    private TextView selectedLocationLabel;
    private LinearLayout checkpointList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_set_location);

        selectedLocationLabel = findViewById(R.id.selectedLocationLabel);
        checkpointList = findViewById(R.id.locationCheckpointList);

        findViewById(R.id.openSearchLocationButton).setOnClickListener(view ->
                startActivity(new Intent(this, SearchActivity.class)));
        findViewById(R.id.cameraDetectButton).setOnClickListener(view ->
                Toast.makeText(
                        this,
                        "Outdoor recognition model is not installed yet.",
                        Toast.LENGTH_SHORT
                ).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindCheckpoints();
    }

    private void bindCheckpoints() {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        String currentId = LocationStore.getCurrentCheckpointId(this);
        Checkpoint current = currentId == null
                ? null
                : app.getCheckpointRepository().getCheckpointById(currentId);
        selectedLocationLabel.setText(current == null
                ? "Current location: Not set"
                : "Current location: " + current.getCheckpointName());

        checkpointList.removeAllViews();
        List<Checkpoint> checkpoints = app.getCheckpointRepository().getAllCheckpoints();
        for (Checkpoint checkpoint : checkpoints) {
            Button button = ViewFactory.listButton(
                    this,
                    checkpoint.getCheckpointName() + "  -  "
                            + UiText.cleanType(checkpoint.getCheckpointType())
            );
            button.setOnClickListener(view -> {
                LocationStore.setCurrentCheckpointId(this, checkpoint.getCheckpointId());
                Toast.makeText(
                        this,
                        "Current location set to " + checkpoint.getCheckpointName(),
                        Toast.LENGTH_SHORT
                ).show();
                startActivity(new Intent(this, HomeMapActivity.class));
                finish();
            });
            checkpointList.addView(button);
        }
    }
}
