package com.example.mindmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.mindmap.export.CsvUtils;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.DistanceUtils;
import com.example.mindmap.util.MoodUtils;
import com.example.mindmap.util.MovingStateDetector;

import org.junit.Test;

public class UtilityUnitTest {
    @Test
    public void distanceCalculation_isReasonable() {
        double meters = DistanceUtils.haversineMeters(39.9087, 116.3975, 39.9097, 116.3975);
        assertTrue(meters > 100 && meters < 120);
    }

    @Test
    public void moodAverage_calculatesFourDimensions() {
        assertEquals(4.0f, MoodUtils.averageScore(5, 4, 3, 4), 0.001f);
    }

    @Test
    public void moodColor_mapsLowMidHigh() {
        assertTrue(MoodUtils.colorForAverage(4.2f) != MoodUtils.colorForAverage(2.2f));
    }

    @Test
    public void csvEscape_handlesCommaQuoteAndLineBreak() {
        assertEquals("\"a,b\"\"c\n\"", CsvUtils.escape("a,b\"c\n"));
    }

    @Test
    public void movingStateDetector_detectsMovingAndStaying() {
        assertEquals(AppConstants.MOVING, MovingStateDetector.detect(1.0f, 0f, 0f, 9.8f));
        assertEquals(AppConstants.STAYING, MovingStateDetector.detect(0.1f, 0f, 0f, 9.8f));
    }
}
