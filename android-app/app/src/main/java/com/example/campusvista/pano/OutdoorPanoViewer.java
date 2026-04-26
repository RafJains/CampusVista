package com.example.campusvista.pano;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;

import com.example.campusvista.R;
import com.example.campusvista.data.model.OutdoorPano;
import com.example.campusvista.data.repository.PanoRepository;

import java.io.IOException;
import java.io.InputStream;

public final class OutdoorPanoViewer {
    private final Context context;
    private final PanoRepository panoRepository;

    public OutdoorPanoViewer(Context context, PanoRepository panoRepository) {
        this.context = context.getApplicationContext();
        this.panoRepository = panoRepository;
    }

    public boolean loadPano(ImageView imageView, OutdoorPano pano, boolean preferThumbnail) {
        if (pano == null) {
            imageView.setImageResource(R.drawable.ic_pano_placeholder);
            return false;
        }

        String imagePath = panoRepository.getImageAssetPath(pano);
        String thumbnailPath = panoRepository.getThumbnailAssetPath(pano);

        if (preferThumbnail && loadAssetBitmap(imageView, thumbnailPath)) {
            return true;
        }

        if (loadAssetBitmap(imageView, imagePath)) {
            return true;
        }

        if (!imagePath.equals(thumbnailPath) && loadAssetBitmap(imageView, thumbnailPath)) {
            return true;
        }

        imageView.setImageResource(R.drawable.ic_pano_placeholder);
        return false;
    }

    private boolean loadAssetBitmap(ImageView imageView, String assetPath) {
        if (assetPath == null) {
            return false;
        }

        try (InputStream inputStream = context.getAssets().open(assetPath)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap == null) {
                return false;
            }
            imageView.setImageBitmap(bitmap);
            return true;
        } catch (IOException exception) {
            return false;
        }
    }
}
