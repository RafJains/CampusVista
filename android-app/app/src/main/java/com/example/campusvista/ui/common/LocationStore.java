package com.example.campusvista.ui.common;

import android.content.Context;
import android.content.SharedPreferences;

public final class LocationStore {
    private static final String PREFS_NAME = "campusvista_location";
    private static final String KEY_CURRENT_CHECKPOINT_ID = "current_checkpoint_id";

    private LocationStore() {
    }

    public static void setCurrentCheckpointId(Context context, String checkpointId) {
        getPrefs(context).edit()
                .putString(KEY_CURRENT_CHECKPOINT_ID, checkpointId)
                .apply();
    }

    public static String getCurrentCheckpointId(Context context) {
        return getPrefs(context).getString(KEY_CURRENT_CHECKPOINT_ID, null);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
