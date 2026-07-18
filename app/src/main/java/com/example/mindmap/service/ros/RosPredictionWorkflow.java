package com.example.mindmap.service.ros;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.data.repository.MoodMapRepository;
import com.example.mindmap.service.asr.TranscriptionService;
import com.example.mindmap.service.asr.TranscriptionServiceFactory;

import java.io.File;

/** Runs ASR if needed, then saves an LLM ROS prediction linked to the saved annotation. */
public class RosPredictionWorkflow {
    private static final String TAG = "RosPredictionWorkflow";

    private final Context appContext;
    private final MoodMapRepository repository;
    private final TranscriptionService transcriptionService;
    private final DoubaoRosPredictionService predictionService;

    public RosPredictionWorkflow(Context context, MoodMapRepository repository) {
        this.appContext = context.getApplicationContext();
        this.repository = repository;
        this.transcriptionService = TranscriptionServiceFactory.create(appContext);
        this.predictionService = new DoubaoRosPredictionService();
    }

    public void start(AnnotationEntity annotation) {
        if (annotation == null || annotation.id <= 0L) {
            return;
        }
        String transcript = safe(annotation.speechText);
        if (!transcript.isEmpty()) {
            predictAndSave(annotation.id, transcript);
            return;
        }
        File mediaFile = firstExistingFile(annotation.videoUri, annotation.audioUri);
        if (mediaFile == null) {
            savePrediction(predictionService.failedPrediction(annotation.id, "", new IllegalStateException("No transcript or media file.")));
            return;
        }
        transcriptionService.transcribe(mediaFile, new TranscriptionService.Callback() {
            @Override
            public void onSuccess(String text) {
                predictAndSave(annotation.id, safe(text));
            }

            @Override
            public void onError(Throwable throwable) {
                savePrediction(predictionService.failedPrediction(annotation.id, "", throwable));
            }
        });
    }

    private void predictAndSave(long annotationId, String transcript) {
        predictionService.predict(annotationId, transcript, this::savePrediction);
    }

    private void savePrediction(RosPredictionEntity prediction) {
        repository.insertRosPrediction(prediction,
                id -> Log.d(TAG, "Saved ROS prediction: " + id),
                throwable -> Log.e(TAG, "Failed to save ROS prediction", throwable));
    }

    @Nullable
    private File firstExistingFile(@Nullable String firstUri, @Nullable String secondUri) {
        File first = fileFromUri(firstUri);
        if (first != null && first.exists() && first.length() > 0L) {
            return first;
        }
        File second = fileFromUri(secondUri);
        if (second != null && second.exists() && second.length() > 0L) {
            return second;
        }
        return null;
    }

    @Nullable
    private File fileFromUri(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        Uri uri = Uri.parse(value);
        if ("file".equalsIgnoreCase(uri.getScheme()) || uri.getScheme() == null) {
            String path = uri.getPath();
            return path == null ? null : new File(path);
        }
        return null;
    }

    private String safe(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}
