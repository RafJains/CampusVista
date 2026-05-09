package com.example.campusvista.recognition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.res.AssetManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class RecognitionAssetsInstrumentedTest {
    @Test
    public void packagedLocalRecognitionReturnsExpectedReferenceMatch() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        assumeTrue(assetExists(context.getAssets(), "pano/outdoor/OUT_CP001.jpg"));
        assumeTrue(assetExists(context.getAssets(), "ml/recognition_index.bin"));
        assumeTrue(assetExists(context.getAssets(), "ml/recognition_index_labels.csv"));
        byte[] imageBytes = readAsset(context.getAssets(), "pano/outdoor/OUT_CP001.jpg");

        List<RecognitionMatch> matches = LocalRecognitionEngine.getInstance(context)
                .recognize(imageBytes, 3);

        assertFalse(matches.isEmpty());
        assertEquals("OUT_CP001", matches.get(0).checkpointId);
    }

    private static boolean assetExists(AssetManager assets, String path) {
        try (InputStream ignored = assets.open(path)) {
            return true;
        } catch (IOException exception) {
            return false;
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
}
