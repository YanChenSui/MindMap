package com.example.mindmap.service.asr;

import java.io.File;

/** Audio-file speech-to-text abstraction. */
public interface TranscriptionService {
    boolean isAvailable();

    String getCompatibilityNote();

    void transcribe(File audioFile, Callback callback);

    interface Callback {
        void onSuccess(String text);

        void onError(Throwable throwable);
    }
}
