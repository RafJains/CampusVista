package com.example.campusvista.ui.pano;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.pano.OutdoorPanoViewer;
import com.example.campusvista.ui.common.NavExtras;

public final class OutdoorPanoActivity extends Activity {
    private String checkpointId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_outdoor_pano);

        checkpointId = getIntent().getStringExtra(NavExtras.EXTRA_CHECKPOINT_ID);
        findViewById(R.id.backToMapButton).setOnClickListener(view -> finish());
        if (checkpointId == null) {
            Toast.makeText(this, "Pano is not ready here.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.panoLocationName)).setText("Loading panorama...");
        OutdoorPano pano = ((CampusVistaApp) getApplication()).getCampusVistaEngine().getPano(checkpointId);
        bindPano(pano);
    }

    private void bindPano(OutdoorPano pano) {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        ((TextView) findViewById(R.id.panoLocationName)).setText("Current view");
        ((TextView) findViewById(R.id.panoDescription)).setText(
                (pano == null
                        ? "Under work: panorama view is being prepared."
                        : pano.getDescription() == null
                        ? "Panorama view."
                        : pano.getDescription())
        );

        ImageView imageView = findViewById(R.id.panoImage);
        boolean loaded = new OutdoorPanoViewer(this, app.getPanoRepository())
                .loadPano(imageView, pano, false);
        if (!loaded) {
            ((TextView) findViewById(R.id.panoDescription)).setText(
                    "Under work: panorama view is being prepared."
            );
        }
    }
}
