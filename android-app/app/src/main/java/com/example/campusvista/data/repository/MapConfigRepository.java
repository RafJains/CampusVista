package com.example.campusvista.data.repository;

import android.content.Context;

import com.example.campusvista.data.local.DBConfig;
import com.example.campusvista.util.AssetUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public final class MapConfigRepository {
    private static MapConfigRepository instance;
    private MapConfig cachedConfig;

    public static synchronized MapConfigRepository getInstance() {
        if (instance == null) {
            instance = new MapConfigRepository();
        }
        return instance;
    }

    private MapConfigRepository() {
    }

    public synchronized MapConfig load(Context context) {
        if (cachedConfig != null) {
            return cachedConfig;
        }

        try {
            String json = AssetUtils.readAssetText(context, DBConfig.MAP_CONFIG_ASSET_PATH);
            JSONObject object = new JSONObject(json);
            cachedConfig = new MapConfig(
                    object.getString("campus_map_file"),
                    object.getInt("campus_map_width_px"),
                    object.getInt("campus_map_height_px"),
                    object.getDouble("meters_per_pixel")
            );
            return cachedConfig;
        } catch (IOException | JSONException exception) {
            throw new IllegalStateException("Unable to load CampusVista map config", exception);
        }
    }

    public static final class MapConfig {
        private final String campusMapFile;
        private final int campusMapWidthPx;
        private final int campusMapHeightPx;
        private final double metersPerPixel;

        private MapConfig(
                String campusMapFile,
                int campusMapWidthPx,
                int campusMapHeightPx,
                double metersPerPixel
        ) {
            if (campusMapFile == null || campusMapFile.trim().isEmpty()) {
                throw new IllegalArgumentException("campus_map_file is required");
            }
            if (campusMapFile.contains("/") || campusMapFile.contains("\\")) {
                throw new IllegalArgumentException("campus_map_file must be a filename only");
            }
            if (campusMapWidthPx <= 0 || campusMapHeightPx <= 0) {
                throw new IllegalArgumentException("campus map dimensions must be positive");
            }
            if (metersPerPixel <= 0) {
                throw new IllegalArgumentException("meters_per_pixel must be positive");
            }
            this.campusMapFile = campusMapFile;
            this.campusMapWidthPx = campusMapWidthPx;
            this.campusMapHeightPx = campusMapHeightPx;
            this.metersPerPixel = metersPerPixel;
        }

        public String getCampusMapFile() {
            return campusMapFile;
        }

        public int getCampusMapWidthPx() {
            return campusMapWidthPx;
        }

        public int getCampusMapHeightPx() {
            return campusMapHeightPx;
        }

        public double getMetersPerPixel() {
            return metersPerPixel;
        }
    }
}
