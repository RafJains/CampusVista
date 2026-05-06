package com.example.campusvista.recognition;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;

final class VisualFeatureExtractor {
    static final int IMAGE_SIZE = 224;
    static final int EMBEDDING_DIMENSION = 734;
    private static final int LOW_INFORMATION_SIZE = 96;
    private static final int TEXTURE_GRID_SIZE = 16;
    private static final int SPATIAL_GRID_SIZE = 12;

    private VisualFeatureExtractor() {
    }

    static boolean isLowInformation(Bitmap bitmap) {
        Bitmap scaled = Bitmap.createScaledBitmap(bitmap, LOW_INFORMATION_SIZE, LOW_INFORMATION_SIZE, true);
        int[] pixels = new int[LOW_INFORMATION_SIZE * LOW_INFORMATION_SIZE];
        scaled.getPixels(
                pixels,
                0,
                LOW_INFORMATION_SIZE,
                0,
                0,
                LOW_INFORMATION_SIZE,
                LOW_INFORMATION_SIZE
        );
        double sum = 0.0;
        double sumSquares = 0.0;
        for (int color : pixels) {
            double gray = (Color.red(color) * 0.299
                    + Color.green(color) * 0.587
                    + Color.blue(color) * 0.114) / 255.0;
            sum += gray;
            sumSquares += gray * gray;
        }
        int count = pixels.length;
        double mean = sum / count;
        double variance = Math.max(0.0, (sumSquares / count) - (mean * mean));
        return Math.sqrt(variance) < 0.04;
    }

    static List<float[]> queryEmbeddings(Bitmap source) {
        List<Bitmap> bitmaps = queryBitmaps(source);
        List<float[]> embeddings = new ArrayList<>(bitmaps.size());
        for (Bitmap bitmap : bitmaps) {
            embeddings.add(extractEmbedding(bitmap));
        }
        return embeddings;
    }

    private static List<Bitmap> queryBitmaps(Bitmap source) {
        List<Bitmap> bitmaps = new ArrayList<>();
        bitmaps.add(source);
        int width = source.getWidth();
        int height = source.getHeight();
        int cropWidth = Math.max(1, Math.round(width * 0.78f));
        int cropHeight = Math.max(1, Math.round(height * 0.78f));
        int[] xPositions = new int[]{
                0,
                Math.max(0, (width - cropWidth) / 2),
                Math.max(0, width - cropWidth)
        };
        int[] yPositions = new int[]{
                0,
                Math.max(0, (height - cropHeight) / 2),
                Math.max(0, height - cropHeight)
        };
        for (int i = 0; i < xPositions.length; i++) {
            bitmaps.add(Bitmap.createBitmap(
                    source,
                    xPositions[i],
                    yPositions[i],
                    cropWidth,
                    cropHeight
            ));
        }
        return bitmaps;
    }

    private static float[] extractEmbedding(Bitmap source) {
        Bitmap bitmap = centerCrop(source);
        int[] pixels = new int[IMAGE_SIZE * IMAGE_SIZE];
        bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE);
        float[] feature = new float[EMBEDDING_DIMENSION];
        int offset = appendColorTextureAndEdgeFeatures(pixels, feature);
        offset = appendSpatialGrid(pixels, feature, offset);
        normalize(feature);
        return feature;
    }

    private static int appendColorTextureAndEdgeFeatures(int[] pixels, float[] feature) {
        int offset = 0;
        float[] hsv = new float[3];
        float[] colorHist = new float[24];
        double[] channelSums = new double[3];
        double[] channelSquareSums = new double[3];
        float[][] gray = new float[IMAGE_SIZE][IMAGE_SIZE];

        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int color = pixels[y * IMAGE_SIZE + x];
                int red = Color.red(color);
                int green = Color.green(color);
                int blue = Color.blue(color);
                Color.RGBToHSV(red, green, blue, hsv);
                colorHist[Math.min(7, (int) (hsv[0] / 360f * 8f))] += 1f;
                colorHist[8 + Math.min(7, (int) (hsv[1] * 8f))] += 1f;
                colorHist[16 + Math.min(7, (int) (hsv[2] * 8f))] += 1f;
                float redFloat = red / 255f;
                float greenFloat = green / 255f;
                float blueFloat = blue / 255f;
                channelSums[0] += redFloat;
                channelSums[1] += greenFloat;
                channelSums[2] += blueFloat;
                channelSquareSums[0] += redFloat * redFloat;
                channelSquareSums[1] += greenFloat * greenFloat;
                channelSquareSums[2] += blueFloat * blueFloat;
                gray[y][x] = (float) (redFloat * 0.299 + greenFloat * 0.587 + blueFloat * 0.114);
            }
        }

        float histSum = IMAGE_SIZE * IMAGE_SIZE * 3f;
        for (float value : colorHist) {
            feature[offset++] = value / histSum;
        }
        offset = appendTexture(gray, feature, offset);
        offset = appendEdges(gray, feature, offset);
        return appendChannelStats(channelSums, channelSquareSums, feature, offset);
    }

    private static int appendTexture(float[][] gray, float[] feature, int offset) {
        float textureMean = 0f;
        float[] texture = new float[256];
        int textureIndex = 0;
        for (int row = 0; row < TEXTURE_GRID_SIZE; row++) {
            for (int col = 0; col < TEXTURE_GRID_SIZE; col++) {
                float value = cellGrayMean(gray, row, col, TEXTURE_GRID_SIZE);
                texture[textureIndex++] = value;
                textureMean += value;
            }
        }
        textureMean /= texture.length;
        for (float value : texture) {
            feature[offset++] = value - textureMean;
        }
        return offset;
    }

    private static int appendEdges(float[][] gray, float[] feature, int offset) {
        float[] edgeHist = new float[16];
        for (int y = 1; y < IMAGE_SIZE - 1; y++) {
            for (int x = 1; x < IMAGE_SIZE - 1; x++) {
                float gradientX = gray[y][x + 1] - gray[y][x - 1];
                float gradientY = gray[y + 1][x] - gray[y - 1][x];
                double magnitude = Math.sqrt(gradientX * gradientX + gradientY * gradientY);
                double angle = (Math.atan2(gradientY, gradientX) + Math.PI) / (2.0 * Math.PI);
                edgeHist[Math.min(15, (int) (angle * 16.0))] += (float) magnitude;
            }
        }
        float edgeSum = 0f;
        for (float value : edgeHist) {
            edgeSum += value;
        }
        for (float value : edgeHist) {
            feature[offset++] = edgeSum <= 0f ? 0f : value / edgeSum;
        }
        return offset;
    }

    private static int appendChannelStats(
            double[] channelSums,
            double[] channelSquareSums,
            float[] feature,
            int offset
    ) {
        double pixelCount = IMAGE_SIZE * IMAGE_SIZE;
        for (double sum : channelSums) {
            feature[offset++] = (float) (sum / pixelCount);
        }
        for (int i = 0; i < 3; i++) {
            double mean = channelSums[i] / pixelCount;
            double variance = Math.max(0.0, channelSquareSums[i] / pixelCount - mean * mean);
            feature[offset++] = (float) Math.sqrt(variance);
        }
        return offset;
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

    private static float cellGrayMean(float[][] gray, int row, int col, int gridSize) {
        int y0 = IMAGE_SIZE * row / gridSize;
        int y1 = IMAGE_SIZE * (row + 1) / gridSize;
        int x0 = IMAGE_SIZE * col / gridSize;
        int x1 = IMAGE_SIZE * (col + 1) / gridSize;
        float sum = 0f;
        int count = 0;
        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                sum += gray[y][x];
                count++;
            }
        }
        return count == 0 ? 0f : sum / count;
    }

    private static int appendSpatialGrid(int[] pixels, float[] feature, int offset) {
        for (int row = 0; row < SPATIAL_GRID_SIZE; row++) {
            int y0 = IMAGE_SIZE * row / SPATIAL_GRID_SIZE;
            int y1 = IMAGE_SIZE * (row + 1) / SPATIAL_GRID_SIZE;
            for (int col = 0; col < SPATIAL_GRID_SIZE; col++) {
                int x0 = IMAGE_SIZE * col / SPATIAL_GRID_SIZE;
                int x1 = IMAGE_SIZE * (col + 1) / SPATIAL_GRID_SIZE;
                double red = 0.0;
                double green = 0.0;
                double blue = 0.0;
                int count = 0;
                for (int y = y0; y < y1; y++) {
                    for (int x = x0; x < x1; x++) {
                        int color = pixels[y * IMAGE_SIZE + x];
                        red += Color.red(color) / 255.0;
                        green += Color.green(color) / 255.0;
                        blue += Color.blue(color) / 255.0;
                        count++;
                    }
                }
                feature[offset++] = (float) (red / count);
                feature[offset++] = (float) (green / count);
                feature[offset++] = (float) (blue / count);
            }
        }
        return offset;
    }

    private static void normalize(float[] feature) {
        double sumSquares = 0.0;
        for (float value : feature) {
            sumSquares += value * value;
        }
        double norm = Math.sqrt(sumSquares);
        if (norm <= 0.0) {
            return;
        }
        for (int i = 0; i < feature.length; i++) {
            feature[i] = (float) (feature[i] / norm);
        }
    }
}
