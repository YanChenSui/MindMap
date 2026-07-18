package com.example.mindmap.util;

public final class MoodUtils {
    private MoodUtils() {}

    public static float averageScore(int pleasure, int calm, int relaxation, int focus) {
        validateScore(pleasure);
        validateScore(calm);
        validateScore(relaxation);
        validateScore(focus);
        return (pleasure + calm + relaxation + focus) / 4f;
    }

    public static float rosAverageScore(int visualPreference, int thoughtClarity, int worryForget,
                                        int restoredRelaxed, int calm, int interest, int focusedAlert) {
        validateScore(visualPreference);
        validateScore(thoughtClarity);
        validateScore(worryForget);
        validateScore(restoredRelaxed);
        validateScore(calm);
        validateScore(interest);
        validateScore(focusedAlert);
        return (visualPreference + thoughtClarity + worryForget + restoredRelaxed
                + calm + interest + focusedAlert) / 7f;
    }

    public static int colorForAverage(float averageScore) {
        if (averageScore >= 4f) {
            return rgb(46, 125, 50);
        }
        if (averageScore >= 3f) {
            return rgb(245, 124, 0);
        }
        return rgb(198, 40, 40);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static void validateScore(int score) {
        if (score < 1 || score > 5) {
            throw new IllegalArgumentException("情绪评分必须在 1 到 5 之间");
        }
    }
}
