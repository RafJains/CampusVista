package com.example.campusvista.recognition;

import android.graphics.Bitmap;

public final class ImagePreprocessor {
    public static final int DEFAULT_INPUT_SIZE = 224;

    public Bitmap prepareForModel(Bitmap source) {
        if (source == null) {
            return null;
        }

        int size = Math.min(source.getWidth(), source.getHeight());
        int left = Math.max(0, (source.getWidth() - size) / 2);
        int top = Math.max(0, (source.getHeight() - size) / 2);
        Bitmap cropped = Bitmap.createBitmap(source, left, top, size, size);
        return Bitmap.createScaledBitmap(
                cropped,
                DEFAULT_INPUT_SIZE,
                DEFAULT_INPUT_SIZE,
                true
        );
    }
}
