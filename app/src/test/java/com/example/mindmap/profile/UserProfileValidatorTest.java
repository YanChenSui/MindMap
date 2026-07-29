package com.example.mindmap.profile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UserProfileValidatorTest {
    @Test
    public void profileMustUseKnownOptions() {
        assertTrue(UserProfileValidator.isValidProfile(new UserProfile("participant01", "男", "18-24", "本科")));
        assertTrue(UserProfileValidator.isValidProfile(new UserProfile("participant02", "不愿透露", "55岁及以上", "其他")));
        assertFalse(UserProfileValidator.isValidProfile(new UserProfile("", "男", "18-24", "本科")));
        assertFalse(UserProfileValidator.isValidProfile(new UserProfile("participant03", "未知", "18-24", "本科")));
        assertFalse(UserProfileValidator.isValidProfile(new UserProfile("participant03", "男", "24", "本科")));
        assertFalse(UserProfileValidator.isValidProfile(null));
    }

    @Test
    public void missingOptionFallsBackToFirstIndex() {
        assertEquals(1, UserProfileValidator.indexOf(UserProfileSession.GENDER_OPTIONS, "女"));
        assertEquals(0, UserProfileValidator.indexOf(UserProfileSession.GENDER_OPTIONS, "未知"));
    }
}
