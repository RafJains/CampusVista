package com.example.campusvista.data.local;

import android.content.Context;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DBHelper extends SQLiteOpenHelper {
    private static DBHelper instance;

    private static final String[] MVP_TABLES = {
            "checkpoints",
            "places",
            "edges",
            "crowd_rules",
            "outdoor_panos",
            "recognition_refs",
            "search_aliases"
    };

    public static synchronized DBHelper getInstance(Context context) {
        Context appContext = context.getApplicationContext();
        SeedDbCopier.copyDatabaseIfNeeded(appContext);
        if (instance == null) {
            instance = new DBHelper(appContext);
        }
        return instance;
    }

    private DBHelper(Context context) {
        super(context, DBConfig.DB_NAME, null, DBConfig.DB_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS checkpoints (" +
                "checkpoint_id TEXT PRIMARY KEY, " +
                "checkpoint_name TEXT NOT NULL, " +
                "checkpoint_type TEXT NOT NULL, " +
                "x_coord REAL NOT NULL, " +
                "y_coord REAL NOT NULL, " +
                "latitude REAL, " +
                "longitude REAL, " +
                "description TEXT, " +
                "orientation TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS places (" +
                "place_id TEXT PRIMARY KEY, " +
                "place_name TEXT NOT NULL, " +
                "place_type TEXT NOT NULL, " +
                "checkpoint_id TEXT NOT NULL, " +
                "description TEXT, " +
                "keywords TEXT, " +
                "FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS edges (" +
                "edge_id TEXT PRIMARY KEY, " +
                "from_checkpoint_id TEXT NOT NULL, " +
                "to_checkpoint_id TEXT NOT NULL, " +
                "distance_meters REAL NOT NULL, " +
                "is_bidirectional INTEGER NOT NULL DEFAULT 1, " +
                "edge_type TEXT, " +
                "FOREIGN KEY (from_checkpoint_id) REFERENCES checkpoints(checkpoint_id), " +
                "FOREIGN KEY (to_checkpoint_id) REFERENCES checkpoints(checkpoint_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS crowd_rules (" +
                "crowd_rule_id TEXT PRIMARY KEY, " +
                "checkpoint_id TEXT NOT NULL, " +
                "day_type TEXT NOT NULL, " +
                "start_time TEXT NOT NULL, " +
                "end_time TEXT NOT NULL, " +
                "crowd_level TEXT NOT NULL, " +
                "penalty_cost REAL NOT NULL, " +
                "description TEXT, " +
                "FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS outdoor_panos (" +
                "pano_id TEXT PRIMARY KEY, " +
                "checkpoint_id TEXT NOT NULL, " +
                "image_file TEXT NOT NULL, " +
                "thumbnail_file TEXT, " +
                "orientation TEXT, " +
                "description TEXT, " +
                "FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS recognition_refs (" +
                "recognition_id TEXT PRIMARY KEY, " +
                "checkpoint_id TEXT NOT NULL, " +
                "label_name TEXT NOT NULL, " +
                "model_label_index INTEGER NOT NULL, " +
                "confidence_threshold REAL DEFAULT 0.70, " +
                "FOREIGN KEY (checkpoint_id) REFERENCES checkpoints(checkpoint_id))");
        db.execSQL("CREATE TABLE IF NOT EXISTS search_aliases (" +
                "alias_id TEXT PRIMARY KEY, " +
                "place_id TEXT NOT NULL, " +
                "alias_text TEXT NOT NULL, " +
                "alias_type TEXT, " +
                "FOREIGN KEY (place_id) REFERENCES places(place_id))");

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_from ON edges(from_checkpoint_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_edges_to ON edges(to_checkpoint_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_type ON places(place_type)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_places_checkpoint ON places(checkpoint_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_crowd_checkpoint_time " +
                "ON crowd_rules(checkpoint_id, day_type, start_time, end_time)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_panos_checkpoint ON outdoor_panos(checkpoint_id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_recognition_label " +
                "ON recognition_refs(model_label_index)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_alias_text ON search_aliases(alias_text)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS search_aliases");
        db.execSQL("DROP TABLE IF EXISTS recognition_refs");
        db.execSQL("DROP TABLE IF EXISTS outdoor_panos");
        db.execSQL("DROP TABLE IF EXISTS crowd_rules");
        db.execSQL("DROP TABLE IF EXISTS edges");
        db.execSQL("DROP TABLE IF EXISTS places");
        db.execSQL("DROP TABLE IF EXISTS checkpoints");
        onCreate(db);
    }

    public Map<String, Long> getMvpTableCounts() {
        SQLiteDatabase database = getReadableDatabase();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : MVP_TABLES) {
            counts.put(table, DatabaseUtils.queryNumEntries(database, table));
        }
        return Collections.unmodifiableMap(counts);
    }
}
