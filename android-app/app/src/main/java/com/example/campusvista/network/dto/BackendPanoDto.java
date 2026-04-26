package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendPanoDto {
    @SerializedName("pano_id")
    public String panoId;
    @SerializedName("checkpoint_id")
    public String checkpointId;
    @SerializedName("image_file")
    public String imageFile;
    @SerializedName("thumbnail_file")
    public String thumbnailFile;
    public String orientation;
    public String description;
    @SerializedName("image_url")
    public String imageUrl;
    @SerializedName("thumbnail_url")
    public String thumbnailUrl;
}
