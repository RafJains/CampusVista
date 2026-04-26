package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendPlaceDto {
    @SerializedName("place_id")
    public String placeId;
    @SerializedName("place_name")
    public String placeName;
    @SerializedName("place_type")
    public String placeType;
    @SerializedName("checkpoint_id")
    public String checkpointId;
    public String description;
    public String keywords;
    @SerializedName("match_score")
    public double matchScore;
    @SerializedName("match_source")
    public String matchSource;
    @SerializedName("matched_text")
    public String matchedText;
}
