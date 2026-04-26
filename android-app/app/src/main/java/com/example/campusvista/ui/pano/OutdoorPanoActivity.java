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
import com.example.campusvista.network.BackendCallback;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.network.dto.BackendPanoDto;
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
            Toast.makeText(this, "No outdoor pano is available here.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ((TextView) findViewById(R.id.panoLocationName)).setText("Loading outdoor panorama...");
        loadPanoFromBackend();
    }

    private void loadPanoFromBackend() {
        BackendClient.getInstance(this).getPano(
                checkpointId,
                new BackendCallback<BackendPanoDto>() {
                    @Override
                    public void onSuccess(BackendPanoDto value) {
                        OutdoorPano pano = BackendMapper.toPano(value);
                        bindPano(pano, "Python backend");
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        OutdoorPano pano = ((CampusVistaApp) getApplication())
                                .getPanoRepository()
                                .getOutdoorPanoForCheckpoint(checkpointId);
                        if (pano == null) {
                            Toast.makeText(
                                    OutdoorPanoActivity.this,
                                    "No outdoor pano is available here.",
                                    Toast.LENGTH_SHORT
                            ).show();
                            finish();
                            return;
                        }
                        bindPano(pano, "offline fallback");
                    }
                }
        );
    }

    private void bindPano(OutdoorPano pano, String source) {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        Checkpoint checkpoint = app.getCheckpointRepository().getCheckpointById(checkpointId);
        String checkpointName = checkpoint == null
                ? getIntent().getStringExtra(NavExtras.EXTRA_CHECKPOINT_NAME)
                : checkpoint.getCheckpointName();
        if (checkpointName == null || checkpointName.trim().isEmpty()) {
            checkpointName = checkpointId;
        }

        ((TextView) findViewById(R.id.panoLocationName)).setText(checkpointName);
        ((TextView) findViewById(R.id.panoDescription)).setText(
                (pano.getDescription() == null
                        ? "Outdoor visual orientation point."
                        : pano.getDescription())
                        + "\nMetadata: " + source
        );

        ImageView imageView = findViewById(R.id.panoImage);
        boolean loaded = new OutdoorPanoViewer(this, app.getPanoRepository())
                .loadPano(imageView, pano, false);
        if (!loaded) {
            ((TextView) findViewById(R.id.panoDescription)).append(
                    "\n\nImage asset could not be decoded, so the placeholder is shown."
            );
        }
    }
}
