package com.example.campusvista.network;

import android.content.Context;

import com.example.campusvista.network.dto.BackendCheckpointDto;
import com.example.campusvista.network.dto.BackendNearestCheckpointDto;
import com.example.campusvista.network.dto.BackendPanoDto;
import com.example.campusvista.network.dto.BackendPlaceDto;
import com.example.campusvista.network.dto.BackendRecognitionRequest;
import com.example.campusvista.network.dto.BackendRecognitionResponse;
import com.example.campusvista.network.dto.BackendRouteRequest;
import com.example.campusvista.network.dto.BackendRouteResponse;
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
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();
        Gson gson = new GsonBuilder().create();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BackendConfig.getBaseUrl(context))
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        api = retrofit.create(CampusVistaApi.class);
    }

    public void searchPlaces(
            String query,
            String placeType,
            int limit,
            BackendCallback<List<BackendPlaceDto>> callback
    ) {
        enqueue(api.searchPlaces(query, placeType, limit), callback);
    }

    public void getPlace(String placeId, BackendCallback<BackendPlaceDto> callback) {
        enqueue(api.getPlace(placeId), callback);
    }

    public void getCheckpoints(BackendCallback<List<BackendCheckpointDto>> callback) {
        enqueue(api.getCheckpoints(), callback);
    }

    public void getCheckpoint(
            String checkpointId,
            BackendCallback<BackendCheckpointDto> callback
    ) {
        enqueue(api.getCheckpoint(checkpointId), callback);
    }

    public void getNearestCheckpoint(
            double xCoord,
            double yCoord,
            BackendCallback<BackendNearestCheckpointDto> callback
    ) {
        enqueue(api.getNearestCheckpoint(xCoord, yCoord), callback);
    }

    public void getPano(String checkpointId, BackendCallback<BackendPanoDto> callback) {
        enqueue(api.getPano(checkpointId), callback);
    }

    public void buildRoute(
            BackendRouteRequest request,
            BackendCallback<BackendRouteResponse> callback
    ) {
        enqueue(api.buildRoute(request), callback);
    }

    public void recognize(
            BackendRecognitionRequest request,
            BackendCallback<BackendRecognitionResponse> callback
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
}
