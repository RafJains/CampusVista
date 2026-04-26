package com.example.campusvista.data.model;

public final class RecognitionRef {
    private final String recognitionId;
    private final String checkpointId;
    private final String labelName;
    private final int modelLabelIndex;
    private final double confidenceThreshold;

    public RecognitionRef(
            String recognitionId,
            String checkpointId,
            String labelName,
            int modelLabelIndex,
            double confidenceThreshold
    ) {
        this.recognitionId = recognitionId;
        this.checkpointId = checkpointId;
        this.labelName = labelName;
        this.modelLabelIndex = modelLabelIndex;
        this.confidenceThreshold = confidenceThreshold;
    }

    public String getRecognitionId() {
        return recognitionId;
    }

    public String getCheckpointId() {
        return checkpointId;
    }

    public String getLabelName() {
        return labelName;
    }

    public int getModelLabelIndex() {
        return modelLabelIndex;
    }

    public double getConfidenceThreshold() {
        return confidenceThreshold;
    }
}
