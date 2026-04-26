package com.example.campusvista.recognition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RecognitionResult {
    private final boolean modelAvailable;
    private final int labelIndex;
    private final String labelName;
    private final double confidence;
    private final List<Candidate> candidates;
    private final String message;

    private RecognitionResult(
            boolean modelAvailable,
            int labelIndex,
            String labelName,
            double confidence,
            List<Candidate> candidates,
            String message
    ) {
        this.modelAvailable = modelAvailable;
        this.labelIndex = labelIndex;
        this.labelName = labelName;
        this.confidence = confidence;
        this.candidates = candidates == null
                ? Collections.<Candidate>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(candidates));
        this.message = message;
    }

    public static RecognitionResult unavailable(String message) {
        return new RecognitionResult(
                false,
                -1,
                null,
                0.0,
                Collections.<Candidate>emptyList(),
                message
        );
    }

    public static RecognitionResult detected(
            int labelIndex,
            String labelName,
            double confidence,
            List<Candidate> candidates
    ) {
        return new RecognitionResult(
                true,
                labelIndex,
                labelName,
                confidence,
                candidates,
                null
        );
    }

    public boolean isModelAvailable() {
        return modelAvailable;
    }

    public int getLabelIndex() {
        return labelIndex;
    }

    public String getLabelName() {
        return labelName;
    }

    public double getConfidence() {
        return confidence;
    }

    public List<Candidate> getCandidates() {
        return candidates;
    }

    public String getMessage() {
        return message;
    }

    public static final class Candidate {
        private final int labelIndex;
        private final String labelName;
        private final double confidence;

        public Candidate(int labelIndex, String labelName, double confidence) {
            this.labelIndex = labelIndex;
            this.labelName = labelName;
            this.confidence = confidence;
        }

        public int getLabelIndex() {
            return labelIndex;
        }

        public String getLabelName() {
            return labelName;
        }

        public double getConfidence() {
            return confidence;
        }
    }
}
