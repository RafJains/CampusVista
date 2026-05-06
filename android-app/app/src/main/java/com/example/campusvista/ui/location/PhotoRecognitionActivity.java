package com.example.campusvista.ui.location;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendClient.BackendCallback;
import com.example.campusvista.network.BackendDtos.RecognitionMatchDto;
import com.example.campusvista.network.BackendDtos.RecognitionResponseDto;
import com.example.campusvista.recognition.LocalRecognitionEngine;
import com.example.campusvista.recognition.PhotoInputPreprocessor;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.home.HomeMapActivity;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class PhotoRecognitionActivity extends Activity {
    private static final int REQUEST_CAMERA_PERMISSION = 41;
    private static final int REQUEST_TAKE_PHOTO = 42;
    private static final int REQUEST_PICK_PHOTO = 43;

    private ImageView photoPreview;
    private TextView statusText;
    private LinearLayout matchesList;
    private byte[] currentImageBytes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo_recognition);

        photoPreview = findViewById(R.id.recognitionPhotoPreview);
        statusText = findViewById(R.id.recognitionStatusText);
        matchesList = findViewById(R.id.recognitionMatchesList);

        findViewById(R.id.takePhotoButton).setOnClickListener(view -> openCamera());
        findViewById(R.id.chooseGalleryButton).setOnClickListener(view -> openGallery());
        findViewById(R.id.retryRecognitionButton).setOnClickListener(view -> analyzeCurrentPhoto());
        setStatus("Take a photo or choose one from gallery.");
    }

    private void openCamera() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(intent, REQUEST_TAKE_PHOTO);
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Choose campus photo"), REQUEST_PICK_PHOTO);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            openCamera();
            return;
        }
        Toast.makeText(this, "Camera permission is needed to take a photo.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) {
            return;
        }
        try {
            if (requestCode == REQUEST_TAKE_PHOTO) {
                Bundle extras = data.getExtras();
                Parcelable photo = extras == null ? null : extras.getParcelable("data");
                if (!(photo instanceof Bitmap)) {
                    throw new IOException("Camera did not return a photo.");
                }
                Bitmap bitmap = (Bitmap) photo;
                currentImageBytes = PhotoInputPreprocessor.fromBitmap(bitmap);
            } else if (requestCode == REQUEST_PICK_PHOTO) {
                Uri uri = data.getData();
                if (uri == null) {
                    throw new IOException("Gallery did not return a photo.");
                }
                currentImageBytes = PhotoInputPreprocessor.fromUri(getContentResolver(), uri);
            }
            showPreview();
            analyzeCurrentPhoto();
        } catch (IOException | RuntimeException exception) {
            setStatus("Could not read that image.");
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showPreview() {
        Bitmap bitmap = BitmapFactory.decodeByteArray(currentImageBytes, 0, currentImageBytes.length);
        photoPreview.setImageBitmap(bitmap);
        photoPreview.setVisibility(View.VISIBLE);
    }

    private void analyzeCurrentPhoto() {
        if (currentImageBytes == null || currentImageBytes.length == 0) {
            setStatus("Take a photo or choose one from gallery.");
            return;
        }
        matchesList.removeAllViews();
        setStatus("Checking location...");
        BackendClient.getInstance(this).recognize(
                currentImageBytes,
                "campus-photo.jpg",
                new BackendCallback<RecognitionResponseDto>() {
                    @Override
                    public void onSuccess(RecognitionResponseDto value) {
                        bindRecognitionResult(value);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        bindLocalRecognitionFallback();
                    }
                }
        );
    }

    private void bindLocalRecognitionFallback() {
        try {
            List<RecognitionMatchDto> matches = LocalRecognitionEngine.getInstance(this)
                    .recognize(currentImageBytes, 5);
            if (matches.isEmpty()) {
                setStatus("Recognition needs the backend. You can still set location manually.");
                matchesList.removeAllViews();
                showManualLocationAction();
                return;
            }
            RecognitionResponseDto response = new RecognitionResponseDto();
            response.recognized = isConfident(matches);
            response.matches = matches;
            response.message = response.recognized
                    ? "Location recognized locally."
                    : "Backend unavailable. Showing closest local matches.";
            response.modelVersion = "android-local-vpr-v1";
            bindRecognitionResult(response);
        } catch (IOException | RuntimeException exception) {
            setStatus("Recognition needs the backend. You can still set location manually.");
            matchesList.removeAllViews();
            showManualLocationAction();
        }
    }

    private void bindRecognitionResult(RecognitionResponseDto response) {
        List<RecognitionMatchDto> matches = response == null || response.matches == null
                ? Collections.emptyList()
                : response.matches;
        setStatus(response == null ? "No recognition response." : response.message);
        matchesList.removeAllViews();
        if (matches.isEmpty()) {
            matchesList.addView(ViewFactory.sectionLine(this, "No supported checkpoint matched this photo."));
            showManualLocationAction();
            return;
        }
        for (RecognitionMatchDto match : matches) {
            matchesList.addView(matchButton(match));
        }
    }

    private Button matchButton(RecognitionMatchDto match) {
        String label = match.rank + ". " + match.checkpointName
                + "\n" + match.checkpointId + " - " + Math.round(match.confidencePercent) + "% match";
        Button button = ViewFactory.listButton(this, label);
        button.setOnClickListener(view -> showMatchActions(match));
        return button;
    }

    private void showMatchActions(RecognitionMatchDto match) {
        String[] actions = {"Set as current location", "Route from here", "View on map"};
        new android.app.AlertDialog.Builder(this)
                .setTitle(match.checkpointName)
                .setItems(actions, (dialog, which) -> {
                    handleMatchAction(match, which);
                })
                .show();
    }

    private void handleMatchAction(RecognitionMatchDto match, int actionIndex) {
        setCurrentCheckpoint(match.checkpointId);
        if (actionIndex == 0) {
            finish();
            return;
        }
        Intent intent = new Intent(this, HomeMapActivity.class);
        startActivity(intent);
        finish();
    }

    private void showManualLocationAction() {
        Button button = ViewFactory.listButton(this, "Choose location manually");
        button.setOnClickListener(view -> {
            startActivity(new Intent(this, SetLocationActivity.class));
            finish();
        });
        matchesList.addView(button);
    }

    private void setCurrentCheckpoint(String checkpointId) {
        LocationStore.setCurrentCheckpointId(this, checkpointId);
        Checkpoint checkpoint = ((CampusVistaApp) getApplication())
                .getCheckpointRepository()
                .getCheckpointById(checkpointId);
        String name = checkpoint == null ? checkpointId : checkpoint.getCheckpointName();
        Toast.makeText(this, "Start set: " + name, Toast.LENGTH_SHORT).show();
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private static boolean isConfident(List<RecognitionMatchDto> matches) {
        if (matches.isEmpty()) {
            return false;
        }
        RecognitionMatchDto top = matches.get(0);
        double second = matches.size() > 1 ? matches.get(1).confidencePercent : 0.0;
        return top.confidencePercent >= 70.0
                && top.confidencePercent - second >= 6.0
                && top.supportingViews >= 2;
    }
}
