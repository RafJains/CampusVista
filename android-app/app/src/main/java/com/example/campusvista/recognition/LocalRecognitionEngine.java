package com.example.campusvista.recognition;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.network.BackendDtos.RecognitionMatchDto;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LocalRecognitionEngine {
    private static final String INDEX_ASSET = "ml/recognition_index.bin";
    private static final String LABELS_ASSET = "ml/recognition_index_labels.csv";
    private static final int TOP_REFERENCE_LIMIT = 80;
    private static LocalRecognitionEngine instance;

    private final Context context;
    private float[] embeddings;
    private String[] checkpointIds;
    private String[] imageFiles;
    private int referenceCount;
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

    public List<RecognitionMatchDto> recognize(byte[] imageBytes, int limit) throws IOException {
        ensureLoaded();
        int matchLimit = Math.max(0, limit);
        if (referenceCount == 0 || matchLimit == 0) {
            return new ArrayList<>();
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        if (bitmap == null || VisualFeatureExtractor.isLowInformation(bitmap)) {
            return new ArrayList<>();
        }
        List<float[]> queries = VisualFeatureExtractor.queryEmbeddings(bitmap);
        int[] topIndexes = new int[Math.min(TOP_REFERENCE_LIMIT, referenceCount)];
        float[] topScores = new float[topIndexes.length];
        for (int i = 0; i < topIndexes.length; i++) {
            topIndexes[i] = -1;
            topScores[i] = -Float.MAX_VALUE;
        }
        for (int index = 0; index < referenceCount; index++) {
            float score = bestSimilarity(queries, index);
            insertTopReference(topIndexes, topScores, index, score);
        }
        List<ScoredCheckpoint> ranked = aggregateScores(topIndexes, topScores);
        List<RecognitionMatchDto> matches = new ArrayList<>();
        int count = Math.min(matchLimit, ranked.size());
        for (int i = 0; i < count; i++) {
            ScoredCheckpoint scored = ranked.get(i);
            RecognitionMatchDto dto = new RecognitionMatchDto();
            dto.checkpointId = scored.checkpointId;
            dto.checkpointName = checkpointName(scored.checkpointId);
            dto.confidencePercent = confidencePercent(scored.score);
            dto.rank = i + 1;
            dto.referenceImageUrl = "/assets/pano/outdoor/" + scored.imageFile;
            dto.supportingViews = Math.min(scored.scores.size(), 5);
            matches.add(dto);
        }
        return matches;
    }

    private synchronized void ensureLoaded() throws IOException {
        if (loaded) {
            return;
        }
        AssetManager assets = context.getAssets();
        byte[] rawIndex = readAsset(assets, INDEX_ASSET);
        ByteBuffer buffer = ByteBuffer.wrap(rawIndex).order(ByteOrder.LITTLE_ENDIAN);
        referenceCount = buffer.getInt();
        int dimension = buffer.getInt();
        if (referenceCount < 0) {
            throw new IOException("Recognition index has invalid reference count.");
        }
        if (dimension != VisualFeatureExtractor.EMBEDDING_DIMENSION) {
            throw new IOException("Recognition index has unexpected dimension.");
        }
        long expectedBytes = 8L + (long) referenceCount
                * VisualFeatureExtractor.EMBEDDING_DIMENSION
                * Float.BYTES;
        if (rawIndex.length != expectedBytes) {
            throw new IOException("Recognition index is corrupt or incomplete.");
        }
        embeddings = new float[referenceCount * VisualFeatureExtractor.EMBEDDING_DIMENSION];
        for (int i = 0; i < embeddings.length; i++) {
            embeddings[i] = buffer.getFloat();
        }
        int labelCount = loadLabels(assets);
        if (labelCount != referenceCount) {
            throw new IOException("Recognition labels do not match the index.");
        }
        loaded = true;
    }

    private int loadLabels(AssetManager assets) throws IOException {
        checkpointIds = new String[referenceCount];
        imageFiles = new String[referenceCount];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open(LABELS_ASSET),
                StandardCharsets.UTF_8
        ))) {
            String line = reader.readLine();
            int index = 0;
            while ((line = reader.readLine()) != null && index < referenceCount) {
                String[] parts = line.split(",", 2);
                checkpointIds[index] = parts.length > 0 ? parts[0] : "";
                imageFiles[index] = parts.length > 1 ? parts[1] : "";
                if (checkpointIds[index].isEmpty() || imageFiles[index].isEmpty()) {
                    throw new IOException("Recognition label row is incomplete.");
                }
                index++;
            }
            return index;
        }
    }

    private static byte[] readAsset(AssetManager assets, String path) throws IOException {
        try (InputStream input = assets.open(path);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private float dot(float[] query, int referenceIndex) {
        int offset = referenceIndex * VisualFeatureExtractor.EMBEDDING_DIMENSION;
        float sum = 0f;
        for (int i = 0; i < VisualFeatureExtractor.EMBEDDING_DIMENSION; i++) {
            sum += query[i] * embeddings[offset + i];
        }
        return sum;
    }

    private float bestSimilarity(List<float[]> queries, int referenceIndex) {
        float best = -Float.MAX_VALUE;
        for (float[] query : queries) {
            best = Math.max(best, dot(query, referenceIndex));
        }
        return best;
    }

    private static void insertTopReference(
            int[] topIndexes,
            float[] topScores,
            int index,
            float score
    ) {
        for (int i = 0; i < topScores.length; i++) {
            if (score <= topScores[i]) {
                continue;
            }
            for (int shift = topScores.length - 1; shift > i; shift--) {
                topScores[shift] = topScores[shift - 1];
                topIndexes[shift] = topIndexes[shift - 1];
            }
            topScores[i] = score;
            topIndexes[i] = index;
            return;
        }
    }

    private List<ScoredCheckpoint> aggregateScores(int[] topIndexes, float[] topScores) {
        Map<String, ScoredCheckpoint> byCheckpoint = new HashMap<>();
        for (int i = 0; i < topIndexes.length; i++) {
            int referenceIndex = topIndexes[i];
            if (referenceIndex < 0) {
                continue;
            }
            String checkpointId = checkpointIds[referenceIndex];
            ScoredCheckpoint scored = byCheckpoint.get(checkpointId);
            if (scored == null) {
                scored = new ScoredCheckpoint(checkpointId, imageFiles[referenceIndex]);
                byCheckpoint.put(checkpointId, scored);
            }
            scored.scores.add(topScores[i]);
        }
        List<ScoredCheckpoint> ranked = new ArrayList<>(byCheckpoint.values());
        for (ScoredCheckpoint scored : ranked) {
            scored.score = scored.aggregateScore();
        }
        ranked.sort((left, right) -> Float.compare(right.score, left.score));
        return ranked;
    }

    private String checkpointName(String checkpointId) {
        Checkpoint checkpoint = ((CampusVistaApp) context).getCheckpointRepository()
                .getCheckpointById(checkpointId);
        return checkpoint == null ? checkpointId : checkpoint.getCheckpointName();
    }

    private static double confidencePercent(float score) {
        double percent = (score - 0.90) / 0.14 * 99.0;
        return Math.round(Math.max(0.0, Math.min(99.0, percent)) * 10.0) / 10.0;
    }

    private static final class ScoredCheckpoint {
        private final String checkpointId;
        private final String imageFile;
        private final List<Float> scores = new ArrayList<>();
        private float score;

        private ScoredCheckpoint(String checkpointId, String imageFile) {
            this.checkpointId = checkpointId;
            this.imageFile = imageFile;
        }

        private float aggregateScore() {
            scores.sort((left, right) -> Float.compare(right, left));
            int count = Math.min(scores.size(), 5);
            float sum = 0f;
            for (int i = 0; i < count; i++) {
                sum += scores.get(i);
            }
            return sum / Math.max(1, count) + count * 0.01f;
        }
    }
}
