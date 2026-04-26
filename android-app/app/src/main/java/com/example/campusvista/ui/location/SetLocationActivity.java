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
import com.example.campusvista.network.BackendCallback;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.network.dto.BackendCheckpointDto;
import com.example.campusvista.network.dto.BackendNearestCheckpointDto;
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
                startActivity(new Intent(this, CameraLocationActivity.class)));
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
        checkpointList.addView(ViewFactory.sectionLine(this, "Loading checkpoints from Python backend..."));
        BackendClient.getInstance(this).getCheckpoints(new BackendCallback<List<BackendCheckpointDto>>() {
            @Override
            public void onSuccess(List<BackendCheckpointDto> value) {
                bindCheckpointButtons(BackendMapper.toCheckpoints(value));
            }

            @Override
            public void onFallback(Throwable throwable) {
                selectedLocationLabel.setText(selectedLocationLabel.getText()
                        + "\nPython backend unavailable. Showing offline checkpoint list.");
                bindCheckpointButtons(app.getCheckpointRepository().getAllCheckpoints());
            }
        });
    }

    private void bindCheckpointButtons(List<Checkpoint> checkpoints) {
        checkpointList.removeAllViews();
        for (Checkpoint checkpoint : checkpoints) {
            Button button = ViewFactory.listButton(
                    this,
                    checkpoint.getCheckpointName() + "  -  "
                            + UiText.cleanType(checkpoint.getCheckpointType())
            );
            button.setOnClickListener(view -> setLocationViaNearestCheckpoint(checkpoint));
            checkpointList.addView(button);
        }
    }

    private void setLocationViaNearestCheckpoint(Checkpoint selectedCheckpoint) {
        BackendClient.getInstance(this).getNearestCheckpoint(
                selectedCheckpoint.getXCoord(),
                selectedCheckpoint.getYCoord(),
                new BackendCallback<BackendNearestCheckpointDto>() {
                    @Override
                    public void onSuccess(BackendNearestCheckpointDto value) {
                        Checkpoint nearest = BackendMapper.toCheckpoint(value.checkpoint);
                        setCurrentLocation(nearest == null ? selectedCheckpoint : nearest);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        setCurrentLocation(selectedCheckpoint);
                    }
                }
        );
    }

    private void setCurrentLocation(Checkpoint checkpoint) {
        LocationStore.setCurrentCheckpointId(this, checkpoint.getCheckpointId());
        Toast.makeText(
                this,
                "Current location set to " + checkpoint.getCheckpointName(),
                Toast.LENGTH_SHORT
        ).show();
        startActivity(new Intent(this, HomeMapActivity.class));
        finish();
    }
}
