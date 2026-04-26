package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public final class BackendRecognitionResponse {
    public boolean available;
    public String status;
    public String message;
    @SerializedName("checkpoint_id")
    public String checkpointId;
    public Double confidence;
    public List<BackendRecognitionCandidateDto> candidates;
    @SerializedName("fallback_options")
    public List<String> fallbackOptions;
}
