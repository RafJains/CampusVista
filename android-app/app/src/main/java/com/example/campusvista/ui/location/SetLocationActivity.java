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
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.CheckpointDto;
import com.example.campusvista.network.BackendDtos.NearestCheckpointDto;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.ui.common.LocationStore;
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
        findViewById(R.id.openPhotoRecognitionButton).setOnClickListener(view ->
                startActivity(new Intent(this, PhotoRecognitionActivity.class)));
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
                ? "Start: Not set"
                : "Start: " + current.getCheckpointName());

        checkpointList.removeAllViews();
        checkpointList.addView(ViewFactory.sectionLine(this, "Loading campus locations..."));
        BackendClient.getInstance(this).getCheckpoints(new BackendCallback<List<CheckpointDto>>() {
            @Override
            public void onSuccess(List<CheckpointDto> value) {
                bindCheckpointButtons(BackendMapper.toCheckpoints(value), null);
            }

            @Override
            public void onFallback(Throwable throwable) {
                bindCheckpointButtons(
                        app.getCheckpointRepository().getAllCheckpoints(),
                        "Showing available locations."
                );
            }
        });
    }

    private void bindCheckpointButtons(List<Checkpoint> checkpoints, String note) {
        checkpointList.removeAllViews();
        if (note != null && !note.trim().isEmpty()) {
            checkpointList.addView(ViewFactory.sectionLine(this, note));
        }
        for (Checkpoint checkpoint : checkpoints) {
            Button button = ViewFactory.listButton(
                    this,
                    checkpoint.getCheckpointName()
            );
            button.setOnClickListener(view -> setLocationViaNearestCheckpoint(checkpoint));
            checkpointList.addView(button);
        }
    }

    private void setLocationViaNearestCheckpoint(Checkpoint selectedCheckpoint) {
        BackendClient.getInstance(this).getNearestCheckpoint(
                selectedCheckpoint.getMapX(),
                selectedCheckpoint.getMapY(),
                new BackendCallback<NearestCheckpointDto>() {
                    @Override
                    public void onSuccess(NearestCheckpointDto value) {
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
                "Start set: " + checkpoint.getCheckpointName(),
                Toast.LENGTH_SHORT
        ).show();
        startActivity(new Intent(this, HomeMapActivity.class));
        finish();
    }
}
