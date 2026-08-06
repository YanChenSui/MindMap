package com.example.mindmap.ui.video;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.FileOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mindmap.MoodMapApplication;
import com.example.mindmap.R;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.TimeFormatUtils;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CameraX 视频录制页面。
 *
 * <p>真实设备优先录制带音频视频；当模拟器或设备没有可用麦克风/权限时，
 * 自动降级为无声视频，避免点击“开始录制”后没有明显反馈。</p>
 */
public class VideoRecordActivity extends AppCompatActivity {
    public static final String EXTRA_VIDEO_URI = "video_uri";
    public static final String EXTRA_VIDEO_THUMBNAIL_URI = "video_thumbnail_uri";
    public static final String EXTRA_VIDEO_DURATION_MILLIS = "video_duration_millis";
    public static final String EXTRA_VIDEO_RECORDED_AT_MILLIS = "video_recorded_at_millis";
    private static final String TAG = "VideoRecordActivity";

    private PreviewView previewView;
    private TextView statusText;
    private MaterialButton recordButton;
    private VideoCapture<Recorder> videoCapture;
    private Recording recording;
    private File currentFile;
    private long recordingStartTime;
    private boolean cameraReady;
    private boolean stoppingRecording;

    private final ExecutorService cameraExecutor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStopRunnable = this::stopRecording;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_record);
        previewView = findViewById(R.id.preview_view);
        statusText = findViewById(R.id.record_status);
        recordButton = findViewById(R.id.record_button);
        recordButton.setEnabled(false);
        recordButton.setText("相机准备中");
        recordButton.setOnClickListener(v -> {
            if (stoppingRecording) {
                return;
            }
            if (recording == null) {
                startRecording();
            } else {
                stopRecording();
            }
        });
        startCamera();
    }

    /**
     * 初始化 CameraX。质量选择使用回退策略，避免模拟器不支持 HD 时无法开始录制。
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                QualitySelector qualitySelector = QualitySelector.fromOrderedList(
                        Arrays.asList(Quality.HD, Quality.SD, Quality.LOWEST),
                        FallbackStrategy.lowerQualityOrHigherThan(Quality.LOWEST));
                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(qualitySelector)
                        .build();
                videoCapture = VideoCapture.withOutput(recorder);

                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, videoCapture);
                cameraReady = true;
                statusText.setText("准备录制");
                recordButton.setEnabled(true);
                recordButton.setText("开始录制");
            } catch (Throwable throwable) {
                Log.e(TAG, "相机初始化失败", throwable);
                statusText.setText("相机初始化失败");
                Toast.makeText(this, "相机初始化失败，请检查相机权限或切换设备", Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * 创建录制文件并启动录制。录音权限不可用时自动录制无声视频。
     */
    private void startRecording() {
        if (!cameraReady || videoCapture == null) {
            Toast.makeText(this, "相机尚未准备好", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            stoppingRecording = false;
            File dir = ((MoodMapApplication) getApplication()).getRepository().getMediaDir("video");
            currentFile = new File(dir, "video_" + TimeFormatUtils.fileSafeDate(System.currentTimeMillis()) + ".mp4");
            FileOutputOptions options = new FileOutputOptions.Builder(currentFile).build();
            PendingRecording pendingRecording = videoCapture.getOutput().prepareRecording(this, options);
            boolean audioGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            if (audioGranted) {
                pendingRecording = pendingRecording.withAudioEnabled();
            }
            recording = pendingRecording.start(ContextCompat.getMainExecutor(this), this::handleVideoEvent);
            recordingStartTime = System.currentTimeMillis();
            statusText.setText(audioGranted ? "正在录制，最长 60 秒" : "正在录制无声视频，最长 60 秒");
            recordButton.setText("停止录制");
            recordButton.setEnabled(true);
            handler.postDelayed(autoStopRunnable, AppConstants.MAX_VIDEO_DURATION_MILLIS);
        } catch (SecurityException securityException) {
            Log.e(TAG, "录制权限不足", securityException);
            resetRecordButton("缺少相机或录音权限");
            Toast.makeText(this, "缺少相机或录音权限", Toast.LENGTH_LONG).show();
        } catch (Throwable throwable) {
            Log.e(TAG, "开始录制失败", throwable);
            resetRecordButton("录制启动失败");
            Toast.makeText(this, "视频录制失败，请检查权限和存储空间", Toast.LENGTH_LONG).show();
        }
    }

    private void stopRecording() {
        handler.removeCallbacks(autoStopRunnable);
        if (recording != null && !stoppingRecording) {
            stoppingRecording = true;
            String path = currentFile == null ? "" : currentFile.getAbsolutePath();
            statusText.setText(path.isEmpty() ? "正在保存视频" : "正在保存视频\n" + path);
            recordButton.setEnabled(false);
            recordButton.setText("保存中");
            recording.stop();
        }
    }

    private void handleVideoEvent(@NonNull VideoRecordEvent event) {
        if (event instanceof VideoRecordEvent.Start) {
            if (!stoppingRecording) {
                statusText.setText("正在录制，点击可提前停止");
            }
        } else if (event instanceof VideoRecordEvent.Finalize) {
            handleFinalize((VideoRecordEvent.Finalize) event);
        }
    }

    private void handleFinalize(VideoRecordEvent.Finalize finalizeEvent) {
        handler.removeCallbacks(autoStopRunnable);
        recording = null;
        if (finalizeEvent.hasError()) {
            Log.e(TAG, "视频录制失败: " + finalizeEvent.getError());
            resetRecordButton("视频保存失败");
            Toast.makeText(this, "视频保存失败，请重试", Toast.LENGTH_LONG).show();
            setResult(RESULT_CANCELED);
            return;
        }
        Intent data = new Intent();
        File thumbnail = createThumbnail(currentFile);
        data.putExtra(EXTRA_VIDEO_URI, Uri.fromFile(currentFile).toString());
        if (thumbnail != null) {
            data.putExtra(EXTRA_VIDEO_THUMBNAIL_URI, Uri.fromFile(thumbnail).toString());
        }
        data.putExtra(EXTRA_VIDEO_DURATION_MILLIS, Math.max(0L, System.currentTimeMillis() - recordingStartTime));
        data.putExtra(EXTRA_VIDEO_RECORDED_AT_MILLIS, recordingStartTime);
        setResult(RESULT_OK, data);
        String path = currentFile.getAbsolutePath();
        statusText.setText("视频已保存\n" + path);
        Toast.makeText(this, "视频已保存：" + path, Toast.LENGTH_LONG).show();
        finishAfterTransition();
    }

    private void resetRecordButton(String message) {
        recording = null;
        stoppingRecording = false;
        handler.removeCallbacks(autoStopRunnable);
        statusText.setText(message);
        recordButton.setText("重新录制");
        recordButton.setEnabled(cameraReady);
    }

    /**
     * 为详情页生成可持久化的视频缩略图，失败时保留视频本体，不中断录制流程。
     */
    private File createThumbnail(File videoFile) {
        if (videoFile == null || !videoFile.exists()) {
            return null;
        }
        try {
            Bitmap bitmap = ThumbnailUtils.createVideoThumbnail(videoFile.getAbsolutePath(), MediaStore.Video.Thumbnails.MINI_KIND);
            if (bitmap == null) {
                return null;
            }
            String baseName = videoFile.getName().replaceFirst("\\.mp4$", "");
            File thumbnailFile = new File(videoFile.getParentFile(), baseName + "_thumb.jpg");
            try (FileOutputStream outputStream = new FileOutputStream(thumbnailFile)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, outputStream);
            }
            bitmap.recycle();
            return thumbnailFile;
        } catch (IOException exception) {
            Log.e(TAG, "视频缩略图写入失败", exception);
        } catch (Throwable throwable) {
            Log.e(TAG, "视频缩略图生成失败", throwable);
        }
        return null;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(autoStopRunnable);
        if (recording != null) {
            recording.stop();
            recording = null;
        }
        cameraExecutor.shutdown();
        super.onDestroy();
    }
}
