package com.example.mindmap.service.asr;

import android.content.Context;

/** Selects cloud ASR when configured, otherwise keeps the offline fallback. */
public final class TranscriptionServiceFactory {
    private TranscriptionServiceFactory() {
    }

    public static TranscriptionService create(Context context) {
        DoubaoAsrTranscriptionService doubaoService = new DoubaoAsrTranscriptionService();
        if (doubaoService.isAvailable()) {
            return doubaoService;
        }
        return new VoskAudioTranscriptionService(context);
    }
}
