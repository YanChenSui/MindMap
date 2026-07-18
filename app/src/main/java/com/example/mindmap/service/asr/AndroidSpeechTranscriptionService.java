package com.example.mindmap.service.asr;

import android.content.Context;
import android.speech.SpeechRecognizer;

import java.io.File;

/** 安卓内置实时听写服务的兼容性检查器。
 * Compatibility checker for Android's built-in live dictation service. */
public class AndroidSpeechTranscriptionService implements TranscriptionService {
    private final Context appContext;

    public AndroidSpeechTranscriptionService(Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public boolean isAvailable() {
        return SpeechRecognizer.isRecognitionAvailable(appContext);
    }

    @Override
    public String getCompatibilityNote() {
        return isAvailable()
                ? "系统语音服务只适合实时听写，不用于已保存音频文件转写。"
                : "当前设备没有可用的系统语音识别服务。";
    }

    @Override
    public void transcribe(File audioFile, Callback callback) {
        callback.onError(new UnsupportedOperationException("系统 SpeechRecognizer 不支持直接转写已保存音频文件"));
    }
}
