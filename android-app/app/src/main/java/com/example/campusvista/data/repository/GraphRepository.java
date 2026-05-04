package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.Edge;

import java.util.List;

public final class GraphRepository {
    private static GraphRepository instance;
    private final DBHelper dbHelper;

    public static synchronized GraphRepository getInstance(Context context) {
        if (instance == null) {
            instance = new GraphRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public GraphRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public List<Edge> getAllEdges() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toEdges(database.rawQuery(
                "SELECT * FROM edges ORDER BY edge_id",
                null
        ));
    }

}
