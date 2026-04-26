package com.example.campusvista;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.repository.MapConfigRepository;

import java.util.Map;

public final class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        TextView statusView = findViewById(R.id.startupStatus);
        CampusVistaApp app = (CampusVistaApp) getApplication();
        DBHelper dbHelper = app.getDbHelper();
        MapConfigRepository.MapConfig mapConfig = app.getMapConfig();
        Map<String, Long> tableCounts = dbHelper.getMvpTableCounts();

        statusView.setText(getString(R.string.startup_status_format,
                getString(R.string.startup_ready),
                mapConfig.getCampusMapWidthPx(),
                mapConfig.getCampusMapHeightPx(),
                tableCounts.get("checkpoints"),
                tableCounts.get("places")));
    }
}
