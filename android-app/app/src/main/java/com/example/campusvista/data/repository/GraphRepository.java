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

    public Edge getEdgeById(String edgeId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstEdgeOrNull(database.rawQuery(
                "SELECT * FROM edges WHERE edge_id = ? LIMIT 1",
                new String[]{edgeId}
        ));
    }

    public List<Edge> getOutgoingEdgeRows(String checkpointId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toEdges(database.rawQuery(
                "SELECT * FROM edges WHERE from_checkpoint_id = ? ORDER BY to_checkpoint_id",
                new String[]{checkpointId}
        ));
    }

    public List<Edge> getEdgeRowsTouchingCheckpoint(String checkpointId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toEdges(database.rawQuery(
                "SELECT * FROM edges " +
                        "WHERE from_checkpoint_id = ? OR to_checkpoint_id = ? " +
                        "ORDER BY edge_id",
                new String[]{checkpointId, checkpointId}
        ));
    }
}
