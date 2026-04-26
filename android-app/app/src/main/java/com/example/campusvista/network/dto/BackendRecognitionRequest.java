package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendRecognitionRequest {
    @SerializedName("image_base64")
    public String imageBase64;
    @SerializedName("label_name")
    public String labelName;
    @SerializedName("model_label_index")
    public Integer modelLabelIndex;
    public Double confidence;
}
