package com.example.campusvista.network;

import android.content.Context;
import android.content.SharedPreferences;

public final class BackendConfig {
    private static final String PREFS_NAME = "campusvista_backend";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "http://10.0.2.2:8000/";

    private BackendConfig() {
    }

    public static String getBaseUrl(Context context) {
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String value = preferences.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
        if (value == null || value.trim().isEmpty()) {
            return DEFAULT_BASE_URL;
        }
        return ensureTrailingSlash(value.trim());
    }

    public static void setBaseUrl(Context context, String baseUrl) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, ensureTrailingSlash(baseUrl))
                .apply();
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
