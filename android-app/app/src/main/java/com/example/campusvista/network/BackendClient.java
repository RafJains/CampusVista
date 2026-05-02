package com.example.campusvista.network;

import android.content.Context;

import com.example.campusvista.network.BackendDtos.CheckpointDto;
import com.example.campusvista.network.BackendDtos.HealthDto;
import com.example.campusvista.network.BackendDtos.NearestCheckpointDto;
import com.example.campusvista.network.BackendDtos.PanoDto;
import com.example.campusvista.network.BackendDtos.PlaceDto;
import com.example.campusvista.network.BackendDtos.RecognitionRequestDto;
import com.example.campusvista.network.BackendDtos.RecognitionResponseDto;
import com.example.campusvista.network.BackendDtos.RouteRequestDto;
import com.example.campusvista.network.BackendDtos.RouteResponseDto;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public final class BackendClient {
    private static BackendClient instance;

    private final CampusVistaApi api;

    public static synchronized BackendClient getInstance(Context context) {
        if (instance == null) {
            instance = new BackendClient(context.getApplicationContext());
        }
        return instance;
    }

    private BackendClient(Context context) {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
        Gson gson = new GsonBuilder().create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BackendConfig.getBaseUrl(context))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        api = retrofit.create(CampusVistaApi.class);
    }

    public void checkHealth(BackendCallback<HealthDto> callback) {
        enqueue(api.health(), callback);
    }

    public void searchPlaces(
            String query,
            String placeType,
            int limit,
            BackendCallback<List<PlaceDto>> callback
    ) {
        enqueue(api.searchPlaces(query, placeType, limit), callback);
    }

    public void getPlace(String placeId, BackendCallback<PlaceDto> callback) {
        enqueue(api.getPlace(placeId), callback);
    }

    public void getCheckpoints(BackendCallback<List<CheckpointDto>> callback) {
        enqueue(api.getCheckpoints(), callback);
    }

    public void getCheckpoint(
            String checkpointId,
            BackendCallback<CheckpointDto> callback
    ) {
        enqueue(api.getCheckpoint(checkpointId), callback);
    }

    public void getNearestCheckpoint(
            double xCoord,
            double yCoord,
            BackendCallback<NearestCheckpointDto> callback
    ) {
        enqueue(api.getNearestCheckpoint(xCoord, yCoord), callback);
    }

    public void getPano(String checkpointId, BackendCallback<PanoDto> callback) {
        enqueue(api.getPano(checkpointId), callback);
    }

    public void buildRoute(
            RouteRequestDto request,
            BackendCallback<RouteResponseDto> callback
    ) {
        enqueue(api.buildRoute(request), callback);
    }

    public void recognize(
            RecognitionRequestDto request,
            BackendCallback<RecognitionResponseDto> callback
    ) {
        enqueue(api.recognize(request), callback);
    }

    private static <T> void enqueue(Call<T> call, BackendCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful() && response.body() != null) {
                    callback.onSuccess(response.body());
                    return;
                }
                callback.onFallback(new IllegalStateException(
                        "Backend returned HTTP " + response.code()
                ));
            }

            @Override
            public void onFailure(Call<T> call, Throwable throwable) {
                callback.onFallback(throwable);
            }
        });
    }

    public interface BackendCallback<T> {
        void onSuccess(T value);

        void onFallback(Throwable throwable);
    }

    public interface CampusVistaApi {
        @GET("health")
        Call<HealthDto> health();

        @GET("places/search")
        Call<List<PlaceDto>> searchPlaces(
                @Query("q") String query,
                @Query("place_type") String placeType,
                @Query("limit") int limit
        );

        @GET("places/{place_id}")
        Call<PlaceDto> getPlace(@Path("place_id") String placeId);

        @GET("checkpoints")
        Call<List<CheckpointDto>> getCheckpoints();

        @GET("checkpoints/{checkpoint_id}")
        Call<CheckpointDto> getCheckpoint(@Path("checkpoint_id") String checkpointId);

        @GET("checkpoints/nearest")
        Call<NearestCheckpointDto> getNearestCheckpoint(
                @Query("x") double xCoord,
                @Query("y") double yCoord
        );

        @GET("panos/{checkpoint_id}")
        Call<PanoDto> getPano(@Path("checkpoint_id") String checkpointId);

        @POST("route")
        Call<RouteResponseDto> buildRoute(@Body RouteRequestDto request);

        @POST("recognize")
        Call<RecognitionResponseDto> recognize(@Body RecognitionRequestDto request);
    }
}
