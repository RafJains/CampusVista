package com.example.campusvista.ui.pano;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.pano.OutdoorPanoViewer;
import com.example.campusvista.ui.common.NavExtras;

public final class OutdoorPanoActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outdoor_pano);

        String checkpointId = getIntent().getStringExtra(NavExtras.EXTRA_CHECKPOINT_ID);
        CampusVistaApp app = (CampusVistaApp) getApplication();
        Checkpoint checkpoint = checkpointId == null
                ? null
                : app.getCheckpointRepository().getCheckpointById(checkpointId);
        OutdoorPano pano = checkpointId == null
                ? null
                : app.getPanoRepository().getOutdoorPanoForCheckpoint(checkpointId);

        if (checkpoint == null || pano == null) {
            Toast.makeText(this, "No outdoor pano is available here.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.panoLocationName)).setText(checkpoint.getCheckpointName());
        ((TextView) findViewById(R.id.panoDescription)).setText(
                pano.getDescription() == null ? "Outdoor visual orientation point." : pano.getDescription()
        );

        ImageView imageView = findViewById(R.id.panoImage);
        boolean loaded = new OutdoorPanoViewer(this, app.getPanoRepository())
                .loadPano(imageView, pano, false);
        if (!loaded) {
            ((TextView) findViewById(R.id.panoDescription)).append(
                    "\n\nImage asset could not be decoded, so the placeholder is shown."
            );
        }

        findViewById(R.id.backToMapButton).setOnClickListener(view -> finish());
    }
}
