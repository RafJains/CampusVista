package com.example.campusvista.recognition;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.campusvista.CampusVistaApp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class LocalRecognitionEngine {
    private static final String INDEX_ASSET = "ml/recognition_index.bin";
    private static final String LABELS_ASSET = "ml/recognition_index_labels.csv";
    private static final int TOP_REFERENCE_LIMIT = 80;
    private static final float SUPPORT_BONUS = 0.01f;
    private static LocalRecognitionEngine instance;

    private final Context context;
    private RecognitionIndex index;
    private boolean loaded;

    public static synchronized LocalRecognitionEngine getInstance(Context context) {
        if (instance == null) {
            instance = new LocalRecognitionEngine(context.getApplicationContext());
        }
        return instance;
    }

    private LocalRecognitionEngine(Context context) {
        this.context = context;
    }

    public List<RecognitionMatch> recognize(byte[] imageBytes, int limit) throws IOException {
        ensureLoaded();
        int matchLimit = Math.max(0, limit);
        if (index.referenceCount() == 0 || matchLimit == 0) {
            return new ArrayList<>();
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        if (bitmap == null || VisualFeatureExtractor.isLowInformation(bitmap)) {
            return new ArrayList<>();
        }
        List<float[]> queries = VisualFeatureExtractor.queryEmbeddings(bitmap);
        CampusVistaApp app = (CampusVistaApp) context;
        return index.toMatches(
                index.rank(queries, TOP_REFERENCE_LIMIT, SUPPORT_BONUS),
                matchLimit,
                app.getCheckpointRepository(),
                LocalRecognitionEngine::confidencePercent
        );
    }

    private synchronized void ensureLoaded() throws IOException {
        if (loaded) {
            return;
        }
        index = RecognitionIndex.load(
                context.getAssets(),
                INDEX_ASSET,
                LABELS_ASSET,
                VisualFeatureExtractor.EMBEDDING_DIMENSION
        );
        loaded = true;
    }

    private static double confidencePercent(float score) {
        double percent = (score - 0.90) / 0.14 * 99.0;
        return Math.round(Math.max(0.0, Math.min(99.0, percent)) * 10.0) / 10.0;
    }
}
