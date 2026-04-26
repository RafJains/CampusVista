package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendRecognitionCandidateDto {
    @SerializedName("checkpoint_id")
    public String checkpointId;
    @SerializedName("label_name")
    public String labelName;
    @SerializedName("model_label_index")
    public int modelLabelIndex;
    @SerializedName("confidence_threshold")
    public double confidenceThreshold;
    public Double confidence;
}
