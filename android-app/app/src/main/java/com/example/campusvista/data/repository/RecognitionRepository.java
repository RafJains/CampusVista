package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.RecognitionRef;

import java.util.List;

public final class RecognitionRepository {
    private static RecognitionRepository instance;
    private final DBHelper dbHelper;

    public static synchronized RecognitionRepository getInstance(Context context) {
        if (instance == null) {
            instance = new RecognitionRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public RecognitionRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public List<RecognitionRef> getAllRecognitionRefs() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toRecognitionRefs(database.rawQuery(
                "SELECT * FROM recognition_refs ORDER BY model_label_index",
                null
        ));
    }

    public RecognitionRef getRecognitionRefById(String recognitionId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstRecognitionRefOrNull(database.rawQuery(
                "SELECT * FROM recognition_refs WHERE recognition_id = ? LIMIT 1",
                new String[]{recognitionId}
        ));
    }

    public RecognitionRef getRecognitionRefForCheckpoint(String checkpointId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstRecognitionRefOrNull(database.rawQuery(
                "SELECT * FROM recognition_refs WHERE checkpoint_id = ? LIMIT 1",
                new String[]{checkpointId}
        ));
    }

    public RecognitionRef getRecognitionRefByLabelIndex(int modelLabelIndex) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstRecognitionRefOrNull(database.rawQuery(
                "SELECT * FROM recognition_refs WHERE model_label_index = ? LIMIT 1",
                new String[]{String.valueOf(modelLabelIndex)}
        ));
    }

    public RecognitionRef getRecognitionRefByLabelName(String labelName) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstRecognitionRefOrNull(database.rawQuery(
                "SELECT * FROM recognition_refs WHERE label_name = ? LIMIT 1",
                new String[]{labelName}
        ));
    }
}
