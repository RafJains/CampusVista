package com.example.campusvista.network;

import android.content.Context;

import com.example.campusvista.BuildConfig;

public final class BackendConfig {
    private BackendConfig() {
    }

    public static String getBaseUrl(Context context) {
        String value = BuildConfig.DEFAULT_BACKEND_URL;
        if (value == null || value.trim().isEmpty()) {
            return "/";
        }
        return ensureTrailingSlash(value.trim());
    }

    private static String ensureTrailingSlash(String value) {
        return value.endsWith("/") ? value : value + "/";
    }
}
