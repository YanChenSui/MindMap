package com.example.mindmap.service.asr;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TranscriptionResultTest {
    @Test
    public void convertsMediaOffsetsToWorldClockTimes() {
        TranscriptionResult result = new TranscriptionResult("开始讲话", 1_250L, 4_800L);

        assertTrue(result.hasSpeechTiming());
        assertEquals(Long.valueOf(1_700_000_001_250L), result.worldStartTimeMillis(1_700_000_000_000L));
        assertEquals(Long.valueOf(1_700_000_004_800L), result.worldEndTimeMillis(1_700_000_000_000L));
    }

    @Test
    public void invalidOffsetsRemainUnknown() {
        TranscriptionResult result = new TranscriptionResult("只有文字", 5_000L, 2_000L);

        assertFalse(result.hasSpeechTiming());
        assertNull(result.worldStartTimeMillis(1_700_000_000_000L));
        assertNull(result.worldEndTimeMillis(1_700_000_000_000L));
    }

    @Test
    public void missingMediaStartCannotProduceWorldClockTime() {
        TranscriptionResult result = new TranscriptionResult("开始讲话", 100L, 500L);

        assertTrue(result.hasSpeechTiming());
        assertNull(result.worldStartTimeMillis(0L));
        assertNull(result.worldEndTimeMillis(0L));
    }
}
