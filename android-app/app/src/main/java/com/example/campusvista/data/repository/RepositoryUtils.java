package com.example.campusvista.data.repository;

import java.util.Locale;

final class RepositoryUtils {
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private RepositoryUtils() {
    }

    static String likeArg(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.US);
        return "%" + normalized + "%";
    }

    static String limitArg(int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return String.valueOf(safeLimit);
    }

    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
