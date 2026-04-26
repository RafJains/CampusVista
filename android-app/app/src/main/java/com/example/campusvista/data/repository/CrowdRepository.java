package com.example.campusvista.data.repository;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.example.campusvista.data.local.DBHelper;
import com.example.campusvista.data.local.QueryMapper;
import com.example.campusvista.data.model.CrowdRule;

import java.util.List;

public final class CrowdRepository {
    private static CrowdRepository instance;
    private final DBHelper dbHelper;

    public static synchronized CrowdRepository getInstance(Context context) {
        if (instance == null) {
            instance = new CrowdRepository(DBHelper.getInstance(context));
        }
        return instance;
    }

    public CrowdRepository(DBHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public List<CrowdRule> getAllCrowdRules() {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toCrowdRules(database.rawQuery(
                "SELECT * FROM crowd_rules ORDER BY checkpoint_id, day_type, start_time",
                null
        ));
    }

    public CrowdRule getCrowdRuleById(String crowdRuleId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstCrowdRuleOrNull(database.rawQuery(
                "SELECT * FROM crowd_rules WHERE crowd_rule_id = ? LIMIT 1",
                new String[]{crowdRuleId}
        ));
    }

    public List<CrowdRule> getCrowdRulesForCheckpoint(String checkpointId) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toCrowdRules(database.rawQuery(
                "SELECT * FROM crowd_rules " +
                        "WHERE checkpoint_id = ? " +
                        "ORDER BY day_type, start_time",
                new String[]{checkpointId}
        ));
    }

    public List<CrowdRule> getActiveCrowdRules(String dayType, String currentTime) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.toCrowdRules(database.rawQuery(
                "SELECT * FROM crowd_rules " +
                        "WHERE day_type = ? AND start_time <= ? AND end_time > ? " +
                        "ORDER BY checkpoint_id",
                new String[]{dayType, currentTime, currentTime}
        ));
    }

    public CrowdRule getActiveCrowdRuleForCheckpoint(
            String checkpointId,
            String dayType,
            String currentTime
    ) {
        SQLiteDatabase database = dbHelper.getReadableDatabase();
        return QueryMapper.firstCrowdRuleOrNull(database.rawQuery(
                "SELECT * FROM crowd_rules " +
                        "WHERE checkpoint_id = ? " +
                        "AND day_type = ? " +
                        "AND start_time <= ? " +
                        "AND end_time > ? " +
                        "ORDER BY penalty_cost DESC LIMIT 1",
                new String[]{checkpointId, dayType, currentTime, currentTime}
        ));
    }
}
