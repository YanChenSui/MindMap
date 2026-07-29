package com.example.mindmap.service.mosaic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

public final class Yolov9PersonDetector implements AutoCloseable {
    private static final String MODEL_ASSET = "models/yolov9-t-converted.onnx";
    private static final int INPUT_SIZE = 640;
    private static final int PERSON_CLASS_INDEX = 0;
    private static final float CONFIDENCE_THRESHOLD = 0.30f;
    private static final float NMS_IOU_THRESHOLD = 0.45f;
    private static final int MAX_DETECTIONS = 20;

    private final OrtEnvironment environment;
    private final OrtSession session;
    private final String inputName;

    public Yolov9PersonDetector(Context context) throws Exception {
        environment = OrtEnvironment.getEnvironment();
        File modelFile = copyModelToCache(context);
        session = environment.createSession(modelFile.getAbsolutePath(), new OrtSession.SessionOptions());
        inputName = session.getInputNames().iterator().next();
    }

    public List<DetectionBox> detect(Bitmap frame) throws Exception {
        LetterboxResult input = letterbox(frame);
        try (OnnxTensor tensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(input.tensor),
                new long[]{1, 3, INPUT_SIZE, INPUT_SIZE});
             OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            Object value = result.get(0).getValue();
            if (!(value instanceof float[][][])) {
                return Collections.emptyList();
            }
            return parseDetections((float[][][]) value, frame.getWidth(), frame.getHeight(), input);
        }
    }

    private List<DetectionBox> parseDetections(float[][][] output, int frameWidth, int frameHeight, LetterboxResult input) {
        float[][] values = output[0];
        List<DetectionBox> candidates = new ArrayList<>();
        int boxes = values[0].length;
        for (int i = 0; i < boxes; i++) {
            float confidence = values[4 + PERSON_CLASS_INDEX][i];
            if (confidence < CONFIDENCE_THRESHOLD) {
                continue;
            }
            float cx = values[0][i];
            float cy = values[1][i];
            float width = values[2][i];
            float height = values[3][i];
            float left = (cx - width / 2f - input.padX) / input.scale;
            float top = (cy - height / 2f - input.padY) / input.scale;
            float right = (cx + width / 2f - input.padX) / input.scale;
            float bottom = (cy + height / 2f - input.padY) / input.scale;
            candidates.add(new DetectionBox(
                    clamp(left, 0, frameWidth - 1),
                    clamp(top, 0, frameHeight - 1),
                    clamp(right, 0, frameWidth - 1),
                    clamp(bottom, 0, frameHeight - 1),
                    confidence
            ));
        }
        candidates.sort(Comparator.comparingDouble((DetectionBox box) -> box.confidence).reversed());
        return nonMaxSuppression(candidates);
    }

    private List<DetectionBox> nonMaxSuppression(List<DetectionBox> candidates) {
        List<DetectionBox> selected = new ArrayList<>();
        for (DetectionBox candidate : candidates) {
            boolean keep = true;
            for (DetectionBox box : selected) {
                if (iou(candidate, box) > NMS_IOU_THRESHOLD) {
                    keep = false;
                    break;
                }
            }
            if (keep) {
                selected.add(candidate);
                if (selected.size() >= MAX_DETECTIONS) {
                    break;
                }
            }
        }
        return selected;
    }

    private float iou(DetectionBox a, DetectionBox b) {
        float left = Math.max(a.left, b.left);
        float top = Math.max(a.top, b.top);
        float right = Math.min(a.right, b.right);
        float bottom = Math.min(a.bottom, b.bottom);
        float intersection = Math.max(0f, right - left) * Math.max(0f, bottom - top);
        float union = a.area() + b.area() - intersection;
        return union <= 0f ? 0f : intersection / union;
    }

    private LetterboxResult letterbox(Bitmap frame) {
        int width = frame.getWidth();
        int height = frame.getHeight();
        float scale = Math.min(INPUT_SIZE / (float) width, INPUT_SIZE / (float) height);
        int resizedWidth = Math.round(width * scale);
        int resizedHeight = Math.round(height * scale);
        float padX = (INPUT_SIZE - resizedWidth) / 2f;
        float padY = (INPUT_SIZE - resizedHeight) / 2f;

        Bitmap inputBitmap = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(inputBitmap);
        canvas.drawColor(Color.rgb(114, 114, 114));
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(frame, null, new android.graphics.RectF(padX, padY, padX + resizedWidth, padY + resizedHeight), paint);

        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        inputBitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        inputBitmap.recycle();
        float[] tensor = new float[3 * INPUT_SIZE * INPUT_SIZE];
        int plane = INPUT_SIZE * INPUT_SIZE;
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            tensor[i] = ((color >> 16) & 0xff) / 255f;
            tensor[plane + i] = ((color >> 8) & 0xff) / 255f;
            tensor[plane * 2 + i] = (color & 0xff) / 255f;
        }
        return new LetterboxResult(tensor, scale, padX, padY);
    }

    private File copyModelToCache(Context context) throws Exception {
        File modelFile = new File(context.getCacheDir(), "yolov9-t-converted.onnx");
        if (modelFile.exists() && modelFile.length() > 0) {
            return modelFile;
        }
        try (InputStream input = context.getAssets().open(MODEL_ASSET);
             FileOutputStream output = new FileOutputStream(modelFile)) {
            byte[] buffer = new byte[1024 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        return modelFile;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public void close() throws Exception {
        session.close();
    }

    private static final class LetterboxResult {
        final float[] tensor;
        final float scale;
        final float padX;
        final float padY;

        LetterboxResult(float[] tensor, float scale, float padX, float padY) {
            this.tensor = tensor;
            this.scale = scale;
            this.padX = padX;
            this.padY = padY;
        }
    }
}
