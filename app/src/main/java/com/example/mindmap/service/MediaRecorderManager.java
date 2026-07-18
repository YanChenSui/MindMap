package com.example.mindmap.service;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import com.example.mindmap.util.TimeFormatUtils;

import java.io.File;
import java.io.IOException;

/**
 * 封装音频录制，调用方负责权限申请和生命周期内释放。
 */
public class MediaRecorderManager {
    private static final String TAG = "MediaRecorderManager";
    private MediaRecorder recorder;
    private File currentFile;
    private long startTime;

    public File start(Context context, File audioDir) throws IOException {
        if (!audioDir.exists() && !audioDir.mkdirs()) {
            throw new IOException("无法创建音频目录");
        }
        currentFile = new File(audioDir, "audio_" + TimeFormatUtils.fileSafeDate(System.currentTimeMillis()) + ".m4a");
        recorder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? new MediaRecorder(context) : new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(currentFile.getAbsolutePath());
        recorder.prepare();
        recorder.start();
        startTime = System.currentTimeMillis();
        return currentFile;
    }

    public RecordingResult stop() {
        long duration = Math.max(0L, System.currentTimeMillis() - startTime);
        try {
            if (recorder != null) {
                recorder.stop();
            }
        } catch (RuntimeException stopException) {
            Log.e(TAG, "停止录音失败", stopException);
        } finally {
            release();
        }
        return new RecordingResult(currentFile, duration);
    }

    public void release() {
        if (recorder != null) {
            try {
                recorder.release();
            } catch (Throwable throwable) {
                Log.e(TAG, "释放录音器失败", throwable);
            }
            recorder = null;
        }
    }

    public static class RecordingResult {
        public final File file;
        public final long durationMillis;

        public RecordingResult(File file, long durationMillis) {
            this.file = file;
            this.durationMillis = durationMillis;
        }
    }
}
