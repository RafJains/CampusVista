package com.example.campusvista.util;

import android.content.Context;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class AssetUtils {
    private AssetUtils() {
    }

    public static String readAssetText(Context context, String assetPath) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }
}
