package com.example.campusvista.recognition;

import android.content.res.AssetManager;

import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.repository.CheckpointRepository;

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

final class RecognitionIndex {
    private static final int MAX_SUPPORTING_VIEWS = 5;

    private final float[] embeddings;
    private final String[] checkpointIds;
    private final String[] imageFiles;
    private final int referenceCount;
    private final int embeddingDimension;

    private RecognitionIndex(
            float[] embeddings,
            String[] checkpointIds,
            String[] imageFiles,
            int referenceCount,
            int embeddingDimension
    ) {
        this.embeddings = embeddings;
        this.checkpointIds = checkpointIds;
        this.imageFiles = imageFiles;
        this.referenceCount = referenceCount;
        this.embeddingDimension = embeddingDimension;
    }

    static RecognitionIndex load(
            AssetManager assets,
            String indexAsset,
            String labelsAsset,
            int expectedDimension
    ) throws IOException {
        byte[] rawIndex = readAsset(assets, indexAsset);
        ByteBuffer buffer = ByteBuffer.wrap(rawIndex).order(ByteOrder.LITTLE_ENDIAN);
        int referenceCount = buffer.getInt();
        int embeddingDimension = buffer.getInt();
        validateIndex(rawIndex.length, referenceCount, embeddingDimension, expectedDimension);

        float[] embeddings = new float[referenceCount * embeddingDimension];
        for (int i = 0; i < embeddings.length; i++) {
            embeddings[i] = buffer.getFloat();
        }

        LabelRows labels = loadLabels(assets, labelsAsset, referenceCount);
        return new RecognitionIndex(
                embeddings,
                labels.checkpointIds,
                labels.imageFiles,
                referenceCount,
                embeddingDimension
        );
    }

    int referenceCount() {
        return referenceCount;
    }

    List<ScoredCheckpoint> rank(List<float[]> queryEmbeddings, int topReferenceLimit, float supportBonus) {
        int[] topIndexes = new int[Math.min(topReferenceLimit, referenceCount)];
        float[] topScores = new float[topIndexes.length];
        for (int i = 0; i < topIndexes.length; i++) {
            topIndexes[i] = -1;
            topScores[i] = -Float.MAX_VALUE;
        }
        for (int index = 0; index < referenceCount; index++) {
            insertTopReference(topIndexes, topScores, index, bestSimilarity(queryEmbeddings, index));
        }
        return aggregateScores(topIndexes, topScores, supportBonus);
    }

    List<RecognitionMatch> toMatches(
            List<ScoredCheckpoint> ranked,
            int limit,
            CheckpointRepository checkpointRepository,
            ConfidenceMapper confidenceMapper
    ) {
        List<RecognitionMatch> matches = new ArrayList<>();
        int count = Math.min(Math.max(0, limit), ranked.size());
        for (int i = 0; i < count; i++) {
            ScoredCheckpoint scored = ranked.get(i);
            RecognitionMatch match = new RecognitionMatch();
            match.checkpointId = scored.checkpointId;
            match.checkpointName = checkpointName(checkpointRepository, scored.checkpointId);
            match.confidencePercent = confidenceMapper.toConfidencePercent(scored.score);
            match.rank = i + 1;
            match.referenceImageUrl = "/assets/pano/outdoor/" + scored.imageFile;
            match.supportingViews = Math.min(scored.scores.size(), MAX_SUPPORTING_VIEWS);
            matches.add(match);
        }
        return matches;
    }

    private float bestSimilarity(List<float[]> queries, int referenceIndex) {
        float best = -Float.MAX_VALUE;
        for (float[] query : queries) {
            best = Math.max(best, dot(query, referenceIndex));
        }
        return best;
    }

    private float dot(float[] query, int referenceIndex) {
        int offset = referenceIndex * embeddingDimension;
        float sum = 0f;
        int count = Math.min(query.length, embeddingDimension);
        for (int i = 0; i < count; i++) {
            sum += query[i] * embeddings[offset + i];
        }
        return sum;
    }

    private List<ScoredCheckpoint> aggregateScores(
            int[] topIndexes,
            float[] topScores,
            float supportBonus
    ) {
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
            scored.score = scored.aggregateScore(supportBonus);
        }
        ranked.sort((left, right) -> Float.compare(right.score, left.score));
        return ranked;
    }

    private static void validateIndex(
            int rawLength,
            int referenceCount,
            int embeddingDimension,
            int expectedDimension
    ) throws IOException {
        if (referenceCount < 0) {
            throw new IOException("Recognition index has invalid reference count.");
        }
        if (embeddingDimension <= 0) {
            throw new IOException("Recognition index has invalid embedding dimension.");
        }
        if (expectedDimension > 0 && embeddingDimension != expectedDimension) {
            throw new IOException("Recognition index has unexpected dimension.");
        }
        long expectedBytes = 8L + (long) referenceCount * embeddingDimension * Float.BYTES;
        if (rawLength != expectedBytes) {
            throw new IOException("Recognition index is corrupt or incomplete.");
        }
    }

    private static LabelRows loadLabels(
            AssetManager assets,
            String labelsAsset,
            int expectedCount
    ) throws IOException {
        String[] checkpointIds = new String[expectedCount];
        String[] imageFiles = new String[expectedCount];
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open(labelsAsset),
                StandardCharsets.UTF_8
        ))) {
            reader.readLine();
            String line;
            int index = 0;
            while ((line = reader.readLine()) != null && index < expectedCount) {
                String[] parts = line.split(",", 2);
                checkpointIds[index] = parts.length > 0 ? parts[0] : "";
                imageFiles[index] = parts.length > 1 ? parts[1] : "";
                if (checkpointIds[index].isEmpty() || imageFiles[index].isEmpty()) {
                    throw new IOException("Recognition label row is incomplete.");
                }
                index++;
            }
            if (index != expectedCount) {
                throw new IOException("Recognition labels do not match the index.");
            }
        }
        return new LabelRows(checkpointIds, imageFiles);
    }

    private static void insertTopReference(int[] topIndexes, float[] topScores, int index, float score) {
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

    private static String checkpointName(
            CheckpointRepository checkpointRepository,
            String checkpointId
    ) {
        Checkpoint checkpoint = checkpointRepository.getCheckpointById(checkpointId);
        return checkpoint == null ? checkpointId : checkpoint.getCheckpointName();
    }

    interface ConfidenceMapper {
        double toConfidencePercent(float score);
    }

    static final class ScoredCheckpoint {
        private final String checkpointId;
        private final String imageFile;
        private final List<Float> scores = new ArrayList<>();
        private float score;

        private ScoredCheckpoint(String checkpointId, String imageFile) {
            this.checkpointId = checkpointId;
            this.imageFile = imageFile;
        }

        private float aggregateScore(float supportBonus) {
            scores.sort((left, right) -> Float.compare(right, left));
            int count = Math.min(scores.size(), MAX_SUPPORTING_VIEWS);
            float sum = 0f;
            for (int i = 0; i < count; i++) {
                sum += scores.get(i);
            }
            return sum / Math.max(1, count) + count * supportBonus;
        }
    }

    private static final class LabelRows {
        private final String[] checkpointIds;
        private final String[] imageFiles;

        private LabelRows(String[] checkpointIds, String[] imageFiles) {
            this.checkpointIds = checkpointIds;
            this.imageFiles = imageFiles;
        }
    }
}
