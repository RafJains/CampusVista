package com.example.campusvista.pano;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.widget.ImageView;

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
            showUnderWorkSignage(imageView);
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

        if (imagePath != null
                && !imagePath.equals(thumbnailPath)
                && loadAssetBitmap(imageView, thumbnailPath)) {
            return true;
        }

        showUnderWorkSignage(imageView);
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
            attachPanoramaGestures(imageView, bitmap.getWidth(), bitmap.getHeight());
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void showUnderWorkSignage(ImageView imageView) {
        imageView.setOnTouchListener(null);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
        imageView.setImageBitmap(createUnderWorkBitmap());
        imageView.setContentDescription("Under work panorama placeholder");
    }

    private Bitmap createUnderWorkBitmap() {
        int width = 1200;
        int height = 800;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        canvas.drawColor(Color.rgb(247, 249, 250));

        paint.setColor(Color.rgb(18, 48, 62));
        RectF sign = new RectF(170, 190, 1030, 610);
        canvas.drawRoundRect(sign, 34, 34, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(10);
        paint.setColor(Color.rgb(231, 183, 90));
        canvas.drawRoundRect(new RectF(205, 225, 995, 575), 24, 24, paint);
        paint.setStyle(Paint.Style.FILL);

        paint.setColor(Color.rgb(231, 183, 90));
        canvas.drawRect(315, 610, 365, 760, paint);
        canvas.drawRect(835, 610, 885, 760, paint);

        drawCenteredText(canvas, paint, "UNDER WORK", 600, 365, 82, Color.WHITE, true);
        drawCenteredText(
                canvas,
                paint,
                "Panorama view is being prepared",
                600,
                460,
                38,
                Color.rgb(210, 228, 220),
                false
        );
        return bitmap;
    }

    private static void drawCenteredText(
            Canvas canvas,
            Paint paint,
            String text,
            int centerX,
            int baselineY,
            int textSize,
            int color,
            boolean bold
    ) {
        paint.setColor(color);
        paint.setTextSize(textSize);
        paint.setFakeBoldText(bold);
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        canvas.drawText(text, centerX - bounds.width() / 2f, baselineY, paint);
        paint.setFakeBoldText(false);
    }

    private void attachPanoramaGestures(ImageView imageView, int bitmapWidth, int bitmapHeight) {
        PanoGestureState state = new PanoGestureState(context, bitmapWidth, bitmapHeight);
        imageView.setAdjustViewBounds(false);
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        imageView.setOnTouchListener((view, event) -> {
            if (!(view instanceof ImageView)) {
                return false;
            }
            return state.onTouch((ImageView) view, event);
        });
        imageView.post(() -> state.fit(imageView));
    }

    private static final class PanoGestureState {
        private static final float MAX_ZOOM_MULTIPLIER = 4f;
        private static final float VERTICAL_LOOK_PADDING = 1.08f;

        private final Matrix matrix = new Matrix();
        private final ScaleGestureDetector scaleDetector;
        private final GestureDetector gestureDetector;
        private final int bitmapWidth;
        private final int bitmapHeight;
        private ImageView boundImageView;
        private float minScale;
        private float maxScale;
        private float scale;
        private float translateX;
        private float translateY;
        private float lastX;
        private float lastY;
        private float lastFocusX;
        private float lastFocusY;

        PanoGestureState(Context context, int bitmapWidth, int bitmapHeight) {
            this.bitmapWidth = bitmapWidth;
            this.bitmapHeight = bitmapHeight;
            scaleDetector = new ScaleGestureDetector(
                    context,
                    new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                        @Override
                        public boolean onScaleBegin(ScaleGestureDetector detector) {
                            lastFocusX = detector.getFocusX();
                            lastFocusY = detector.getFocusY();
                            return true;
                        }

                        @Override
                        public boolean onScale(ScaleGestureDetector detector) {
                            if (boundImageView == null) {
                                return false;
                            }
                            float focusX = detector.getFocusX();
                            float focusY = detector.getFocusY();
                            panBy(focusX - lastFocusX, focusY - lastFocusY);
                            lastFocusX = focusX;
                            lastFocusY = focusY;
                            zoomBy(detector.getScaleFactor(), focusX, focusY);
                            return true;
                        }
                    }
            );
            gestureDetector = new GestureDetector(
                    context,
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDown(MotionEvent event) {
                            return true;
                        }

                        @Override
                        public boolean onDoubleTap(MotionEvent event) {
                            if (boundImageView != null) {
                                fit(boundImageView);
                            }
                            return true;
                        }
                    }
            );
        }

        void fit(ImageView imageView) {
            boundImageView = imageView;
            int width = imageView.getWidth();
            int height = imageView.getHeight();
            if (width <= 0 || height <= 0 || bitmapWidth <= 0 || bitmapHeight <= 0) {
                return;
            }

            minScale = Math.max(
                    (float) width / bitmapWidth,
                    (height * VERTICAL_LOOK_PADDING) / bitmapHeight
            );
            maxScale = minScale * MAX_ZOOM_MULTIPLIER;
            scale = minScale;
            translateX = (width - bitmapWidth * scale) / 2f;
            translateY = (height - bitmapHeight * scale) / 2f;
            clampToViewport(imageView);
            apply(imageView);
        }

        boolean onTouch(ImageView imageView, MotionEvent event) {
            boundImageView = imageView;
            if (scale <= 0f) {
                fit(imageView);
            }

            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = event.getX();
                    lastY = event.getY();
                    requestParentInterception(imageView, true);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!scaleDetector.isInProgress() && event.getPointerCount() == 1) {
                        float dx = event.getX() - lastX;
                        float dy = event.getY() - lastY;
                        lastX = event.getX();
                        lastY = event.getY();
                        panBy(dx, dy);
                    }
                    return true;
                case MotionEvent.ACTION_POINTER_DOWN:
                    lastFocusX = scaleDetector.getFocusX();
                    lastFocusY = scaleDetector.getFocusY();
                    requestParentInterception(imageView, true);
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    updateLastPointAfterPointerUp(event);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    requestParentInterception(imageView, false);
                    return true;
                default:
                    return true;
            }
        }

        private void zoomBy(float scaleFactor, float focusX, float focusY) {
            if (boundImageView == null || scale <= 0f) {
                return;
            }
            float targetScale = scale * scaleFactor;
            float nextScale = Math.max(minScale, Math.min(maxScale, targetScale));
            float imageFocusX = (focusX - translateX) / scale;
            float imageFocusY = (focusY - translateY) / scale;
            translateX = focusX - imageFocusX * nextScale;
            translateY = focusY - imageFocusY * nextScale;
            scale = nextScale;
            clampToViewport(boundImageView);
            apply(boundImageView);
        }

        private void panBy(float dx, float dy) {
            if (boundImageView == null) {
                return;
            }
            translateX += dx;
            translateY += dy;
            clampToViewport(boundImageView);
            apply(boundImageView);
        }

        private void updateLastPointAfterPointerUp(MotionEvent event) {
            int liftedIndex = event.getActionIndex();
            int nextIndex = liftedIndex == 0 ? 1 : 0;
            if (nextIndex < event.getPointerCount()) {
                lastX = event.getX(nextIndex);
                lastY = event.getY(nextIndex);
            }
        }

        private void clampToViewport(ImageView imageView) {
            int width = imageView.getWidth();
            int height = imageView.getHeight();
            float scaledWidth = bitmapWidth * scale;
            float scaledHeight = bitmapHeight * scale;

            if (scaledWidth <= width) {
                translateX = (width - scaledWidth) / 2f;
            } else {
                translateX = Math.max(width - scaledWidth, Math.min(0f, translateX));
            }

            if (scaledHeight <= height) {
                translateY = (height - scaledHeight) / 2f;
            } else {
                translateY = Math.max(height - scaledHeight, Math.min(0f, translateY));
            }
        }

        private void apply(ImageView imageView) {
            matrix.reset();
            matrix.postScale(scale, scale);
            matrix.postTranslate(translateX, translateY);
            imageView.setImageMatrix(matrix);
        }

        private void requestParentInterception(ImageView imageView, boolean disallowIntercept) {
            if (imageView.getParent() != null) {
                imageView.getParent().requestDisallowInterceptTouchEvent(disallowIntercept);
            }
        }
    }
}
