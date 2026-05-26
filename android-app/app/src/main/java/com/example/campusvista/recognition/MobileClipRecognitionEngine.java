package com.example.campusvista.recognition;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

public final class MobileClipRecognitionEngine {
    private static MobileClipRecognitionEngine instance;

    public static synchronized MobileClipRecognitionEngine getInstance(Context context) {
        if (instance == null) {
            instance = new MobileClipRecognitionEngine();
        }
        return instance;
    }

    private MobileClipRecognitionEngine() {
    }

    public synchronized boolean isAvailable() {
        return false;
    }

    public List<RecognitionMatch> recognize(byte[] imageBytes, int limit) {
        return new ArrayList<>();
    }
}
