package com.example.campusvista.recognition;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public final class PhotoInputPreprocessor {
    private static final int MAX_SIDE_PX = 1280;
    private static final int JPEG_QUALITY = 88;

    private PhotoInputPreprocessor() {
    }

    public static byte[] fromBitmap(Bitmap bitmap) {
        return compress(resize(bitmap));
    }

    public static byte[] fromUri(ContentResolver resolver, Uri uri) throws IOException {
        byte[] rawBytes = readAll(resolver, uri);
        Bitmap bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.length);
        if (bitmap == null) {
            throw new IOException("Selected image could not be decoded.");
        }
        bitmap = rotateFromExif(rawBytes, bitmap);
        return compress(resize(bitmap));
    }

    private static byte[] readAll(ContentResolver resolver, Uri uri) throws IOException {
        try (InputStream stream = resolver.openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (stream == null) {
                throw new IOException("Selected image could not be opened.");
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static Bitmap rotateFromExif(byte[] rawBytes, Bitmap bitmap) {
        if (Build.VERSION.SDK_INT < 24) {
            return bitmap;
        }
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(rawBytes));
            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            int degrees = 0;
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                degrees = 90;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                degrees = 180;
            } else if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                degrees = 270;
            }
            if (degrees == 0) {
                return bitmap;
            }
            Matrix matrix = new Matrix();
            matrix.postRotate(degrees);
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (IOException exception) {
            return bitmap;
        }
    }

    private static Bitmap resize(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int longest = Math.max(width, height);
        if (longest <= MAX_SIDE_PX) {
            return bitmap;
        }
        float scale = MAX_SIDE_PX / (float) longest;
        int targetWidth = Math.max(1, Math.round(width * scale));
        int targetHeight = Math.max(1, Math.round(height * scale));
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);
    }

    private static byte[] compress(Bitmap bitmap) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output);
        return output.toByteArray();
    }
}
