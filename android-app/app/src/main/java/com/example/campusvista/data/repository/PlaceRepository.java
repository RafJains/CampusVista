package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.Place;

import java.util.List;

public final class PlaceRepository {
    private static PlaceRepository instance;
    private final DBHelper dbHelper;

    public static synchronized PlaceRepository getInstance(Context context) {
        if (instance == null) {
            instance = new PlaceRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public PlaceRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public List<Place> getAllPlaces() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toPlaces(database.rawQuery(
                "SELECT * FROM places ORDER BY place_name",
                null
        ));
    }

    public Place getPlaceById(String placeId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstPlaceOrNull(database.rawQuery(
                "SELECT * FROM places WHERE place_id = ? LIMIT 1",
                new String[]{placeId}
        ));
    }

    public List<Place> getPlacesByType(String placeType) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toPlaces(database.rawQuery(
                "SELECT * FROM places WHERE place_type = ? ORDER BY place_name",
                new String[]{placeType}
        ));
    }

    public List<Place> searchPlaces(String query, int limit) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String likeQuery = RepositoryUtils.likeArg(query);
        return QueryMapper.toPlaces(database.rawQuery(
                "SELECT DISTINCT p.* FROM places p " +
                        "LEFT JOIN search_aliases sa ON sa.place_id = p.place_id " +
                        "WHERE LOWER(p.place_name) LIKE ? " +
                        "OR LOWER(p.keywords) LIKE ? " +
                        "OR LOWER(sa.alias_text) LIKE ? " +
                        "ORDER BY p.place_name LIMIT ?",
                new String[]{
                        likeQuery,
                        likeQuery,
                        likeQuery,
                        RepositoryUtils.limitArg(limit)
                }
        ));
    }

    public List<Place> searchPlacesByType(String query, String placeType, int limit) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        String likeQuery = RepositoryUtils.likeArg(query);
        return QueryMapper.toPlaces(database.rawQuery(
                "SELECT DISTINCT p.* FROM places p " +
                        "LEFT JOIN search_aliases sa ON sa.place_id = p.place_id " +
                        "WHERE p.place_type = ? AND (" +
                        "LOWER(p.place_name) LIKE ? " +
                        "OR LOWER(p.keywords) LIKE ? " +
                        "OR LOWER(sa.alias_text) LIKE ?) " +
                        "ORDER BY p.place_name LIMIT ?",
                new String[]{
                        placeType,
                        likeQuery,
                        likeQuery,
                        likeQuery,
                        RepositoryUtils.limitArg(limit)
                }
        ));
    }
}
