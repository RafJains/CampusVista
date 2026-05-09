package com.example.campusvista.ui.common;

import com.example.campusvista.data.model.Checkpoint;

import java.util.List;
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

    public static String checkpointName(Checkpoint checkpoint, String fallbackId) {
        return checkpoint == null ? fallbackId : checkpoint.getCheckpointName();
    }

    public static String crowdWarningMessage(List<String> warnings) {
        StringBuilder message = new StringBuilder();
        for (String warning : warnings) {
            if (warning != null && warning.toLowerCase(Locale.US).contains("may be")) {
                if (message.length() > 0) {
                    message.append("\n\n");
                }
                message.append(warning);
            }
        }
        return message.toString();
    }
}
