package com.example.campusvista.recognition;

import android.content.Context;
import android.graphics.Bitmap;

import com.example.campusvista.util.AssetUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class TFLiteRecognitionEngine {
    private static final String MODEL_ASSET_PATH = "ml/campus_location_model.tflite";
    private static final String LABELS_ASSET_PATH = "ml/labels.txt";

    private final Context context;
    private final List<String> labels;

    public TFLiteRecognitionEngine(Context context) {
        this.context = context.getApplicationContext();
        this.labels = loadLabels();
    }

    public boolean isModelAvailable() {
        try {
            context.getAssets().open(MODEL_ASSET_PATH).close();
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    public RecognitionResult recognize(Bitmap preprocessedBitmap) {
        if (preprocessedBitmap == null) {
            return RecognitionResult.unavailable("No captured outdoor image was provided.");
        }
        if (!isModelAvailable()) {
            return RecognitionResult.unavailable(
                    "Outdoor recognition model is not installed yet."
            );
        }

        return RecognitionResult.unavailable(
                "TFLite runtime wiring is ready, but interpreter integration is pending."
        );
    }

    public List<String> getLabels() {
        return labels;
    }

    private List<String> loadLabels() {
        List<String> loadedLabels = new ArrayList<>();
        try {
            String labelsText = AssetUtils.readAssetText(context, LABELS_ASSET_PATH);
            String[] lines = labelsText.split("\\r?\\n");
            for (String line : lines) {
                String label = line.trim();
                if (!label.isEmpty()) {
                    loadedLabels.add(label);
                }
            }
        } catch (IOException exception) {
            return loadedLabels;
        }
        return loadedLabels;
    }
}
