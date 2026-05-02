package com.example.campusvista.ui.common;

import com.example.campusvista.routing.RouteMode;

import java.util.Locale;

public final class UiText {
    private UiText() {
    }

    public static String cleanType(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "Outdoor";
        }
        String[] parts = value.toLowerCase(Locale.US).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.toString();
    }

    public static String routeModeLabel(RouteMode routeMode) {
        return "Shortest Path";
    }
}
