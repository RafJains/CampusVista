package com.example.campusvista;

import android.app.Application;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.SeedDbCopier;
import com.example.campusvista.data.repository.MapConfigRepository;

public final class CampusVistaApp extends Application {
    private DBHelper dbHelper;
    private MapConfigRepository.MapConfig mapConfig;

    @Override
    public void onCreate() {
        super.onCreate();
        SeedDbCopier.copyDatabaseIfNeeded(this);
        dbHelper = DBHelper.getInstance(this);
        mapConfig = MapConfigRepository.getInstance().load(this);
    }

    public DBHelper getDbHelper() {
        return dbHelper;
    }

    public MapConfigRepository.MapConfig getMapConfig() {
        return mapConfig;
    }
}
