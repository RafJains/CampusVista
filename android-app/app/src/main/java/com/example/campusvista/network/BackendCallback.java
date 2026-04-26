package com.example.campusvista.network;

public interface BackendCallback<T> {
    void onSuccess(T value);

    void onFallback(Throwable throwable);
}
