package com.example.mindmap.profile;

import java.util.Arrays;

public final class UserProfileValidator {
    private UserProfileValidator() {}

    public static boolean isValidProfile(UserProfile profile) {
        return profile != null
                && isValidAccountName(profile.accountName)
                && contains(UserProfileSession.GENDER_OPTIONS, profile.gender)
                && contains(UserProfileSession.AGE_GROUP_OPTIONS, profile.ageGroup)
                && contains(UserProfileSession.EDUCATION_OPTIONS, profile.educationLevel);
    }

    public static boolean isValidAccountName(String accountName) {
        String normalized = accountName == null ? "" : accountName.trim();
        return !normalized.isEmpty() && normalized.length() <= 50;
    }

    public static int indexOf(String[] options, String value) {
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static boolean contains(String[] options, String value) {
        return value != null && Arrays.asList(options).contains(value);
    }
}
