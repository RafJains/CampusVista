package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.SearchAlias;

import java.util.List;

public final class SearchAliasRepository {
    private static SearchAliasRepository instance;
    private final DBHelper dbHelper;

    public static synchronized SearchAliasRepository getInstance(Context context) {
        if (instance == null) {
            instance = new SearchAliasRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public SearchAliasRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public List<SearchAlias> getAllSearchAliases() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toSearchAliases(database.rawQuery(
                "SELECT * FROM search_aliases ORDER BY alias_text",
                null
        ));
    }

    public SearchAlias getSearchAliasById(String aliasId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstSearchAliasOrNull(database.rawQuery(
                "SELECT * FROM search_aliases WHERE alias_id = ? LIMIT 1",
                new String[]{aliasId}
        ));
    }

    public List<SearchAlias> getAliasesForPlace(String placeId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toSearchAliases(database.rawQuery(
                "SELECT * FROM search_aliases WHERE place_id = ? ORDER BY alias_text",
                new String[]{placeId}
        ));
    }

    public List<SearchAlias> searchAliases(String query, int limit) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toSearchAliases(database.rawQuery(
                "SELECT * FROM search_aliases " +
                        "WHERE LOWER(alias_text) LIKE ? " +
                        "ORDER BY alias_text LIMIT ?",
                new String[]{RepositoryUtils.likeArg(query), RepositoryUtils.limitArg(limit)}
        ));
    }
}
