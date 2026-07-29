package com.example.mindmap.profile;

import android.content.Context;
import android.content.SharedPreferences;

public final class UserProfileSession {
    public static final String[] GENDER_OPTIONS = {"\u7537", "\u5973", "\u4e0d\u613f\u900f\u9732"};
    public static final String[] AGE_GROUP_OPTIONS = {"18\u5c81\u4ee5\u4e0b", "18-24", "25-34", "35-44", "45-54", "55\u5c81\u53ca\u4ee5\u4e0a"};
    public static final String[] EDUCATION_OPTIONS = {"\u521d\u4e2d\u53ca\u4ee5\u4e0b", "\u9ad8\u4e2d\u6216\u4e2d\u4e13", "\u5927\u4e13", "\u672c\u79d1", "\u7855\u58eb", "\u535a\u58eb", "\u5176\u4ed6"};

    private static final String PREFERENCES_NAME = "user_profile";
    private static final String KEY_COMPLETED = "profile_completed";
    private static final String KEY_ACCOUNT_NAME = "account_name";
    private static final String KEY_GENDER = "gender";
    private static final String KEY_AGE_GROUP = "age_group";
    private static final String KEY_EDUCATION_LEVEL = "education_level";

    private UserProfileSession() {}

    public static boolean hasProfile(Context context) {
        SharedPreferences preferences = preferences(context);
        return preferences.getBoolean(KEY_COMPLETED, false)
                && UserProfileValidator.isValidProfile(currentProfile(context));
    }

    public static UserProfile currentProfile(Context context) {
        SharedPreferences preferences = preferences(context);
        return new UserProfile(
                preferences.getString(KEY_ACCOUNT_NAME, ""),
                preferences.getString(KEY_GENDER, ""),
                preferences.getString(KEY_AGE_GROUP, ""),
                preferences.getString(KEY_EDUCATION_LEVEL, "")
        );
    }

    public static void saveProfile(Context context, UserProfile profile) {
        if (!UserProfileValidator.isValidProfile(profile)) {
            throw new IllegalArgumentException("\u8bf7\u5b8c\u6574\u586b\u5199\u8d26\u6237\u540d\u79f0\u548c\u57fa\u672c\u4fe1\u606f");
        }
        preferences(context).edit()
                .putBoolean(KEY_COMPLETED, true)
                .putString(KEY_ACCOUNT_NAME, profile.accountName.trim())
                .putString(KEY_GENDER, profile.gender)
                .putString(KEY_AGE_GROUP, profile.ageGroup)
                .putString(KEY_EDUCATION_LEVEL, profile.educationLevel)
                .apply();
    }

    public static void logout(Context context) {
        preferences(context).edit().clear().apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
