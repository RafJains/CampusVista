package com.example.campusvista.recognition;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.example.campusvista.CampusVistaApp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public final class MobileClipRecognitionEngine {
    private static final String MODEL_ASSET = "ml/openclip_image_encoder.onnx";
    private static final String INDEX_ASSET = "ml/openclip_recognition_index.bin";
    private static final String LABELS_ASSET = "ml/openclip_recognition_index_labels.csv";
    private static final int IMAGE_SIZE = 224;
    private static final int TOP_REFERENCE_LIMIT = 220;
    private static final float SUPPORT_BONUS = 0.003f;
    private static final double CONFIDENCE_FLOOR = 0.78;
    private static final double CONFIDENCE_SPAN = 0.16;
    private static final float[] CLIP_MEAN = new float[]{0.48145466f, 0.4578275f, 0.40821073f};
    private static final float[] CLIP_STD = new float[]{0.26862954f, 0.26130258f, 0.27577711f};

    private static MobileClipRecognitionEngine instance;

    private final Context context;
    private OrtEnvironment environment;
    private OrtSession session;
    private String inputName;
    private RecognitionIndex index;
    private boolean loadAttempted;
    private boolean available;

    public static synchronized MobileClipRecognitionEngine getInstance(Context context) {
        if (instance == null) {
            instance = new MobileClipRecognitionEngine(context.getApplicationContext());
        }
        return instance;
    }

    private MobileClipRecognitionEngine(Context context) {
        this.context = context;
    }

    public synchronized boolean isAvailable() {
        ensureLoaded();
        return available;
    }

    public List<RecognitionMatch> recognize(byte[] imageBytes, int limit) throws IOException {
        ensureLoaded();
        if (!available) {
            return new ArrayList<>();
        }
        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        if (bitmap == null || VisualFeatureExtractor.isLowInformation(bitmap)) {
            return new ArrayList<>();
        }
        try {
            List<float[]> queryEmbeddings = embeddingsForQueryViews(bitmap);
            CampusVistaApp app = (CampusVistaApp) context;
            return index.toMatches(
                    index.rank(queryEmbeddings, TOP_REFERENCE_LIMIT, SUPPORT_BONUS),
                    Math.max(0, limit),
                    app.getCheckpointRepository(),
                    MobileClipRecognitionEngine::confidencePercent
            );
        } catch (OrtException exception) {
            throw new IOException("Mobile CLIP inference failed.", exception);
        }
    }

    private void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        loadAttempted = true;
        try {
            AssetManager assets = context.getAssets();
            if (!assetExists(assets, MODEL_ASSET)
                    || !assetExists(assets, INDEX_ASSET)
                    || !assetExists(assets, LABELS_ASSET)) {
                available = false;
                return;
            }
            environment = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();
            session = environment.createSession(copyAssetToFile(assets, MODEL_ASSET).getAbsolutePath(), options);
            inputName = session.getInputNames().iterator().next();
            index = RecognitionIndex.load(assets, INDEX_ASSET, LABELS_ASSET, 0);
            available = index.referenceCount() > 0;
        } catch (IOException | OrtException | RuntimeException exception) {
            available = false;
        }
    }

    private List<float[]> embeddingsForQueryViews(Bitmap source) throws OrtException {
        List<float[]> outputs = new ArrayList<>();
        for (Bitmap view : queryBitmaps(source)) {
            outputs.add(runModel(view));
        }
        return outputs;
    }

    private float[] runModel(Bitmap bitmap) throws OrtException {
        float[] input = clipInput(centerCrop(bitmap));
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input),
                new long[]{1, 3, IMAGE_SIZE, IMAGE_SIZE}
        );
             OrtSession.Result result = session.run(mapOf(inputName, tensor))) {
            Object value = result.get(0).getValue();
            float[] output = flattenOutput(value);
            normalize(output);
            return output;
        }
    }

    private static List<Bitmap> queryBitmaps(Bitmap source) {
        List<Bitmap> bitmaps = new ArrayList<>();
        bitmaps.add(source);
        int width = source.getWidth();
        int height = source.getHeight();
        addCrop(bitmaps, source, width, height, 0.78f, 0.78f, 0.5f, 0.5f);
        addCrop(bitmaps, source, width, height, 0.78f, 0.78f, 0.0f, 0.5f);
        addCrop(bitmaps, source, width, height, 0.78f, 0.78f, 1.0f, 0.5f);
        addCrop(bitmaps, source, width, height, 0.78f, 0.78f, 0.5f, 0.0f);
        addCrop(bitmaps, source, width, height, 0.78f, 0.78f, 0.5f, 1.0f);
        addCrop(bitmaps, source, width, height, 0.62f, 0.86f, 0.0f, 0.5f);
        addCrop(bitmaps, source, width, height, 0.62f, 0.86f, 0.5f, 0.5f);
        addCrop(bitmaps, source, width, height, 0.62f, 0.86f, 1.0f, 0.5f);
        return bitmaps;
    }

    private static void addCrop(
            List<Bitmap> bitmaps,
            Bitmap source,
            int width,
            int height,
            float widthRatio,
            float heightRatio,
            float xFraction,
            float yFraction
    ) {
        int cropWidth = Math.max(1, Math.round(width * widthRatio));
        int cropHeight = Math.max(1, Math.round(height * heightRatio));
        int x = Math.round(Math.max(0, width - cropWidth) * xFraction);
        int y = Math.round(Math.max(0, height - cropHeight) * yFraction);
        bitmaps.add(Bitmap.createBitmap(source, x, y, cropWidth, cropHeight));
    }

    private static Bitmap centerCrop(Bitmap source) {
        int width = source.getWidth();
        int height = source.getHeight();
        float scale = Math.max(IMAGE_SIZE / (float) width, IMAGE_SIZE / (float) height);
        int scaledWidth = Math.round(width * scale);
        int scaledHeight = Math.round(height * scale);
        Bitmap scaled = Bitmap.createScaledBitmap(source, scaledWidth, scaledHeight, true);
        int x = Math.max(0, (scaledWidth - IMAGE_SIZE) / 2);
        int y = Math.max(0, (scaledHeight - IMAGE_SIZE) / 2);
        return Bitmap.createBitmap(scaled, x, y, IMAGE_SIZE, IMAGE_SIZE);
    }

    private static float[] clipInput(Bitmap bitmap) {
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);
        float[] input = new float[3 * IMAGE_SIZE * IMAGE_SIZE];
        int plane = IMAGE_SIZE * IMAGE_SIZE;
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            input[i] = (((color >> 16) & 0xff) / 255f - CLIP_MEAN[0]) / CLIP_STD[0];
            input[plane + i] = (((color >> 8) & 0xff) / 255f - CLIP_MEAN[1]) / CLIP_STD[1];
            input[plane * 2 + i] = ((color & 0xff) / 255f - CLIP_MEAN[2]) / CLIP_STD[2];
        }
        return input;
    }

    private static double confidencePercent(float score) {
        double percent = (score - CONFIDENCE_FLOOR) / CONFIDENCE_SPAN * 99.0;
        return Math.round(Math.max(0.0, Math.min(99.0, percent)) * 10.0) / 10.0;
    }

    private static float[] flattenOutput(Object value) throws OrtException {
        if (value instanceof float[][]) {
            float[][] matrix = (float[][]) value;
            return matrix.length == 0 ? new float[0] : matrix[0];
        }
        if (value instanceof float[]) {
            return (float[]) value;
        }
        throw new OrtException("Unexpected mobile CLIP output shape.");
    }

    private static void normalize(float[] values) {
        double sumSquares = 0.0;
        for (float value : values) {
            sumSquares += value * value;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm <= 0.0) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            values[i] = (float) (values[i] / norm);
        }
    }

    private static boolean assetExists(AssetManager assets, String path) {
        try (InputStream ignored = assets.open(path)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private File copyAssetToFile(AssetManager assets, String assetPath) throws IOException {
        File output = new File(context.getCodeCacheDir(), assetPath.replace('/', '_'));
        try (InputStream input = assets.open(assetPath);
             FileOutputStream fileOutput = new FileOutputStream(output)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                fileOutput.write(buffer, 0, read);
            }
        }
        return output;
    }

    private static Map<String, OnnxTensor> mapOf(String key, OnnxTensor value) {
        Map<String, OnnxTensor> map = new HashMap<>();
        map.put(key, value);
        return map;
    }
}
