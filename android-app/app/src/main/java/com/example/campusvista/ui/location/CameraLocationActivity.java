package com.example.campusvista.ui.location;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.campusvista.CampusVistaApp;
import com.example.campusvista.R;
import com.example.campusvista.data.model.Checkpoint;
import com.example.campusvista.data.model.RecognitionRef;
import com.example.campusvista.network.BackendCallback;
import com.example.campusvista.network.BackendClient;
import com.example.campusvista.network.BackendMapper;
import com.example.campusvista.network.dto.BackendCheckpointDto;
import com.example.campusvista.network.dto.BackendRecognitionCandidateDto;
import com.example.campusvista.network.dto.BackendRecognitionRequest;
import com.example.campusvista.network.dto.BackendRecognitionResponse;
import com.example.campusvista.recognition.ConfidenceChecker;
import com.example.campusvista.recognition.ImagePreprocessor;
import com.example.campusvista.recognition.RecognitionResult;
import com.example.campusvista.recognition.TFLiteRecognitionEngine;
import com.example.campusvista.ui.common.LocationStore;
import com.example.campusvista.ui.common.UiText;
import com.example.campusvista.ui.common.ViewFactory;
import com.example.campusvista.ui.home.HomeMapActivity;
import com.example.campusvista.ui.search.SearchActivity;

import java.util.List;
import java.util.Locale;

public final class CameraLocationActivity extends Activity {
    private static final int REQUEST_CAPTURE_IMAGE = 7001;

    private TextView statusView;
    private ImageView previewImage;
    private LinearLayout suggestionList;
    private TFLiteRecognitionEngine recognitionEngine;
    private ImagePreprocessor imagePreprocessor;
    private ConfidenceChecker confidenceChecker;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_location);

        statusView = findViewById(R.id.recognitionStatus);
        previewImage = findViewById(R.id.capturedImagePreview);
        suggestionList = findViewById(R.id.recognitionSuggestionList);
        recognitionEngine = new TFLiteRecognitionEngine(this);
        imagePreprocessor = new ImagePreprocessor();
        confidenceChecker = new ConfidenceChecker();

        findViewById(R.id.captureOutdoorImageButton).setOnClickListener(view -> openCamera());
        findViewById(R.id.fallbackSelectOnMapButton).setOnClickListener(view ->
                startActivity(new Intent(this, SetLocationActivity.class)));
        findViewById(R.id.fallbackSearchLocationButton).setOnClickListener(view ->
                startActivity(new Intent(this, SearchActivity.class)));

        bindInitialState();
    }

    private void bindInitialState() {
        suggestionList.removeAllViews();
        if (recognitionEngine.isModelAvailable()) {
            statusView.setText("Camera recognition is ready for outdoor checkpoints.");
        } else {
            statusView.setText(
                    "Outdoor recognition model is not installed yet.\n"
                            + "Use fallback options below or capture an image when the model is added."
            );
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(intent, REQUEST_CAPTURE_IMAGE);
        } catch (ActivityNotFoundException exception) {
            statusView.setText("No camera app is available. Choose a fallback option.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CAPTURE_IMAGE || resultCode != RESULT_OK || data == null) {
            return;
        }

        Object bitmapExtra = data.getExtras() == null ? null : data.getExtras().get("data");
        if (!(bitmapExtra instanceof Bitmap)) {
            statusView.setText("Could not read captured image. Try camera again or use fallback.");
            return;
        }

        Bitmap capturedBitmap = (Bitmap) bitmapExtra;
        previewImage.setImageBitmap(capturedBitmap);
        processCapturedImage(capturedBitmap);
    }

    private void processCapturedImage(Bitmap capturedBitmap) {
        suggestionList.removeAllViews();
        statusView.setText("Sending captured image to Python recognition API...");
        BackendClient.getInstance(this).recognize(
                new BackendRecognitionRequest(),
                new BackendCallback<BackendRecognitionResponse>() {
                    @Override
                    public void onSuccess(BackendRecognitionResponse value) {
                        handleBackendRecognition(value, capturedBitmap);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        processCapturedImageLocally(capturedBitmap);
                    }
                }
        );
    }

    private void handleBackendRecognition(
            BackendRecognitionResponse response,
            Bitmap capturedBitmap
    ) {
        if ("accepted".equals(response.status) && response.checkpointId != null) {
            acceptCheckpointId(response.checkpointId, "Detected by Python backend");
            return;
        }
        if ("low_confidence".equals(response.status)
                && response.candidates != null
                && !response.candidates.isEmpty()) {
            showBackendManualConfirmation(response);
            return;
        }

        processCapturedImageLocally(capturedBitmap);
        if (response.message != null && !response.message.trim().isEmpty()) {
            statusView.setText(response.message
                    + "\nUsing local fallback or manual options below.");
        }
    }

    private void processCapturedImageLocally(Bitmap capturedBitmap) {
        suggestionList.removeAllViews();
        Bitmap modelInput = imagePreprocessor.prepareForModel(capturedBitmap);
        RecognitionResult result = recognitionEngine.recognize(modelInput);

        ConfidenceChecker.ConfidenceDecision decision = confidenceChecker.evaluate(result);
        if (decision == ConfidenceChecker.ConfidenceDecision.AUTO_ACCEPT) {
            acceptLabelIndex(result.getLabelIndex(), result.getConfidence(), true);
            return;
        }
        if (decision == ConfidenceChecker.ConfidenceDecision.NEEDS_CONFIRMATION) {
            showManualConfirmation(result);
            return;
        }
        showFallback(result);
    }

    private void showBackendManualConfirmation(BackendRecognitionResponse response) {
        statusView.setText("Python backend returned a low-confidence match. Confirm one suggestion.");
        int count = Math.min(2, response.candidates.size());
        for (int i = 0; i < count; i++) {
            BackendRecognitionCandidateDto candidate = response.candidates.get(i);
            double confidence = candidate.confidence == null ? 0.0 : candidate.confidence;
            suggestionList.addView(ViewFactory.listButton(
                    this,
                    UiText.cleanType(candidate.labelName)
                            + "  -  "
                            + String.format(Locale.US, "%.0f%%", confidence * 100.0)
            ));
            suggestionList.getChildAt(suggestionList.getChildCount() - 1)
                    .setOnClickListener(view -> acceptCheckpointId(
                            candidate.checkpointId,
                            "Confirmed Python backend match"
                    ));
        }
    }

    private void showManualConfirmation(RecognitionResult result) {
        statusView.setText("Low-confidence outdoor match. Confirm one suggestion.");
        List<RecognitionResult.Candidate> candidates = result.getCandidates();
        int count = Math.min(2, candidates.size());
        for (int i = 0; i < count; i++) {
            RecognitionResult.Candidate candidate = candidates.get(i);
            suggestionList.addView(ViewFactory.listButton(
                    this,
                    UiText.cleanType(candidate.getLabelName())
                            + "  -  "
                            + String.format(Locale.US, "%.0f%%", candidate.getConfidence() * 100.0)
            ));
            suggestionList.getChildAt(suggestionList.getChildCount() - 1)
                    .setOnClickListener(view -> acceptLabelIndex(
                            candidate.getLabelIndex(),
                            candidate.getConfidence(),
                            false
                    ));
        }

        if (count == 0) {
            showFallback(result);
        }
    }

    private void showFallback(RecognitionResult result) {
        String message = result == null ? null : result.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = "Outdoor location could not be recognized confidently.";
        }
        statusView.setText(message + "\nSelect on map, search outdoor location, or try camera again.");
    }

    private void acceptLabelIndex(int labelIndex, double confidence, boolean automatic) {
        CampusVistaApp app = (CampusVistaApp) getApplication();
        RecognitionRef ref = app.getRecognitionRepository().getRecognitionRefByLabelIndex(labelIndex);
        if (ref == null) {
            statusView.setText("Recognized label has no checkpoint mapping. Use fallback options.");
            return;
        }

        Checkpoint checkpoint = app.getCheckpointRepository().getCheckpointById(ref.getCheckpointId());
        if (checkpoint == null) {
            statusView.setText("Recognition checkpoint is missing. Use fallback options.");
            return;
        }

        LocationStore.setCurrentCheckpointId(this, checkpoint.getCheckpointId());
        String mode = automatic ? "Detected" : "Confirmed";
        Toast.makeText(
                this,
                mode + " " + checkpoint.getCheckpointName(),
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(this, HomeMapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    private void acceptCheckpointId(String checkpointId, String mode) {
        BackendClient.getInstance(this).getCheckpoint(
                checkpointId,
                new BackendCallback<BackendCheckpointDto>() {
                    @Override
                    public void onSuccess(BackendCheckpointDto value) {
                        Checkpoint checkpoint = BackendMapper.toCheckpoint(value);
                        if (checkpoint == null) {
                            acceptCheckpointIdLocal(checkpointId, mode);
                            return;
                        }
                        acceptCheckpoint(checkpoint, mode);
                    }

                    @Override
                    public void onFallback(Throwable throwable) {
                        acceptCheckpointIdLocal(checkpointId, mode);
                    }
                }
        );
    }

    private void acceptCheckpointIdLocal(String checkpointId, String mode) {
        Checkpoint checkpoint = ((CampusVistaApp) getApplication())
                .getCheckpointRepository()
                .getCheckpointById(checkpointId);
        if (checkpoint == null) {
            statusView.setText("Recognition checkpoint is missing. Use fallback options.");
            return;
        }
        acceptCheckpoint(checkpoint, mode);
    }

    private void acceptCheckpoint(Checkpoint checkpoint, String mode) {
        LocationStore.setCurrentCheckpointId(this, checkpoint.getCheckpointId());
        Toast.makeText(
                this,
                mode + ": " + checkpoint.getCheckpointName(),
                Toast.LENGTH_SHORT
        ).show();

        Intent intent = new Intent(this, HomeMapActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}
