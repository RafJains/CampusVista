package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.OutdoorPano;

public final class PanoRepository {
    private static final String PANO_ASSET_DIR = "pano/outdoor/";
    private static PanoRepository instance;
    private final DBHelper dbHelper;

    public static synchronized PanoRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PanoRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public PanoRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public OutdoorPano getOutdoorPanoForCheckpoint(String checkpointId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstOutdoorPanoOrNull(database.rawQuery(
                "SELECT * FROM outdoor_panos WHERE checkpoint_id = ? LIMIT 1",
                new String[]{checkpointId}
        ));
    }

    public boolean hasOutdoorPano(String checkpointId) {
        return getOutdoorPanoForCheckpoint(checkpointId) != null;
    }

    public String getImageAssetPath(OutdoorPano pano) {
        if (pano == null) {
            return null;
        }
        String imageFile = RepositoryUtils.trimToNull(pano.getImageFile());
        return imageFile == null ? null : assetPathForFilename(imageFile);
    }

    public String getThumbnailAssetPath(OutdoorPano pano) {
        if (pano == null) {
            return null;
        }
        String thumbnailFile = RepositoryUtils.trimToNull(pano.getThumbnailFile());
        if (thumbnailFile == null) {
            return getImageAssetPath(pano);
        }
        return assetPathForFilename(thumbnailFile);
    }

    public String assetPathForFilename(String filename) {
        if (filename == null || filename.contains("/") || filename.contains("\\")) {
            throw new IllegalArgumentException("pano image fields must store filenames only");
        }
        return PANO_ASSET_DIR + filename;
    }
}
