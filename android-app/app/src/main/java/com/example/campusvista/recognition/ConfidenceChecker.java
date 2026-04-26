package com.example.campusvista.recognition;

public final class ConfidenceChecker {
    public static final double AUTO_ACCEPT_THRESHOLD = 0.70;
    public static final double CONFIRMATION_THRESHOLD = 0.50;

    public ConfidenceDecision evaluate(RecognitionResult result) {
        if (result == null || !result.isModelAvailable()) {
            return ConfidenceDecision.FALLBACK;
        }
        if (result.getConfidence() >= AUTO_ACCEPT_THRESHOLD) {
            return ConfidenceDecision.AUTO_ACCEPT;
        }
        if (result.getConfidence() >= CONFIRMATION_THRESHOLD) {
            return ConfidenceDecision.NEEDS_CONFIRMATION;
        }
        return ConfidenceDecision.FALLBACK;
    }

    public enum ConfidenceDecision {
        AUTO_ACCEPT,
        NEEDS_CONFIRMATION,
        FALLBACK
    }
}
