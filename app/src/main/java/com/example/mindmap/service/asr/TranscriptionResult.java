package com.example.mindmap.service.asr;

/** Speech transcription plus the first and last detected speech offsets in the media file. */
public final class TranscriptionResult {
    public static final long UNKNOWN_OFFSET_MILLIS = -1L;

    public final String text;
    public final long speechStartOffsetMillis;
    public final long speechEndOffsetMillis;

    public TranscriptionResult(String text, long speechStartOffsetMillis, long speechEndOffsetMillis) {
        this.text = text == null ? "" : text.trim();
        if (speechStartOffsetMillis >= 0L && speechEndOffsetMillis >= speechStartOffsetMillis) {
            this.speechStartOffsetMillis = speechStartOffsetMillis;
            this.speechEndOffsetMillis = speechEndOffsetMillis;
        } else {
            this.speechStartOffsetMillis = UNKNOWN_OFFSET_MILLIS;
            this.speechEndOffsetMillis = UNKNOWN_OFFSET_MILLIS;
        }
    }

    public static TranscriptionResult textOnly(String text) {
        return new TranscriptionResult(text, UNKNOWN_OFFSET_MILLIS, UNKNOWN_OFFSET_MILLIS);
    }

    public boolean hasSpeechTiming() {
        return speechStartOffsetMillis >= 0L && speechEndOffsetMillis >= speechStartOffsetMillis;
    }

    public Long worldStartTimeMillis(long mediaStartTimeMillis) {
        return hasSpeechTiming() && mediaStartTimeMillis > 0L
                ? mediaStartTimeMillis + speechStartOffsetMillis
                : null;
    }

    public Long worldEndTimeMillis(long mediaStartTimeMillis) {
        return hasSpeechTiming() && mediaStartTimeMillis > 0L
                ? mediaStartTimeMillis + speechEndOffsetMillis
                : null;
    }
}
