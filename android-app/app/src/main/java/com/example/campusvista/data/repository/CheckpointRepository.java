package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.Checkpoint;

import java.util.List;

public final class CheckpointRepository {
    private static CheckpointRepository instance;
    private final DBHelper dbHelper;

    public static synchronized CheckpointRepository getInstance(Context context) {
        if (instance == null) {
            instance = new CheckpointRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public CheckpointRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public List<Checkpoint> getAllCheckpoints() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toCheckpoints(database.rawQuery(
                "SELECT * FROM checkpoints ORDER BY checkpoint_id",
                null
        ));
    }

    public Checkpoint getCheckpointById(String checkpointId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstCheckpointOrNull(database.rawQuery(
                "SELECT * FROM checkpoints WHERE checkpoint_id = ? LIMIT 1",
                new String[]{checkpointId}
        ));
    }
}
