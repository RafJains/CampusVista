package com.example.campusvista.network.dto;

import com.google.gson.annotations.SerializedName;

public final class BackendNearestCheckpointDto {
    public BackendCheckpointDto checkpoint;
    @SerializedName("distance_pixels")
    public double distancePixels;
    @SerializedName("distance_meters")
    public double distanceMeters;
}
