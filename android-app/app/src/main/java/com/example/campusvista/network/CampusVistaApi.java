package com.example.campusvista.network;

import com.example.campusvista.network.dto.BackendCheckpointDto;
import com.example.campusvista.network.dto.BackendNearestCheckpointDto;
import com.example.campusvista.network.dto.BackendPanoDto;
import com.example.campusvista.network.dto.BackendPlaceDto;
import com.example.campusvista.network.dto.BackendRecognitionRequest;
import com.example.campusvista.network.dto.BackendRecognitionResponse;
import com.example.campusvista.network.dto.BackendRouteRequest;
import com.example.campusvista.network.dto.BackendRouteResponse;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface CampusVistaApi {
    @GET("places/search")
    Call<List<BackendPlaceDto>> searchPlaces(
            @Query("q") String query,
            @Query("place_type") String placeType,
            @Query("limit") int limit
    );

    @GET("places/{place_id}")
    Call<BackendPlaceDto> getPlace(@Path("place_id") String placeId);

    @GET("checkpoints")
    Call<List<BackendCheckpointDto>> getCheckpoints();

    @GET("checkpoints/{checkpoint_id}")
    Call<BackendCheckpointDto> getCheckpoint(@Path("checkpoint_id") String checkpointId);

    @GET("checkpoints/nearest")
    Call<BackendNearestCheckpointDto> getNearestCheckpoint(
            @Query("x") double xCoord,
            @Query("y") double yCoord
    );

    @GET("panos/{checkpoint_id}")
    Call<BackendPanoDto> getPano(@Path("checkpoint_id") String checkpointId);

    @POST("route")
    Call<BackendRouteResponse> buildRoute(@Body BackendRouteRequest request);

    @POST("recognize")
    Call<BackendRecognitionResponse> recognize(@Body BackendRecognitionRequest request);
}
