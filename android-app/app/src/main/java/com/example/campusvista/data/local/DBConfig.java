package com.example.campusvista.data.local;

public final class DBConfig {
    public static final String DB_NAME = "campus_seed.db";
    public static final int DB_VERSION = 3;
    public static final String SEED_DB_ASSET_PATH = "seed/" + DB_NAME;
    public static final String MAP_CONFIG_ASSET_PATH = "config/map_config.json";

    static final String PREFS_NAME = "campusvista_database";
    static final String PREF_COPIED_DB_VERSION = "copied_db_version";
    static final int COPY_BUFFER_SIZE_BYTES = 16 * 1024;

    private DBConfig() {
    }
}
