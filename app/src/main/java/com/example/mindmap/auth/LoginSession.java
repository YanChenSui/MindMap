package com.example.mindmap.auth;

import android.content.Context;
import android.content.SharedPreferences;

public final class LoginSession {
    private static final String PREFERENCES_NAME = "login_session";
    private static final String KEY_LOGGED_IN = "logged_in";
    private static final String KEY_USERNAME = "username";

    private static boolean processSessionActive;
    private static String processUsername = "";

    private LoginSession() {}

    public static boolean isLoggedIn(Context context) {
        return processSessionActive || preferences(context).getBoolean(KEY_LOGGED_IN, false);
    }

    public static String username(Context context) {
        if (processSessionActive) {
            return processUsername;
        }
        return preferences(context).getString(KEY_USERNAME, "");
    }

    public static void login(Context context, String username, boolean rememberLogin) {
        processSessionActive = true;
        processUsername = username;
        SharedPreferences.Editor editor = preferences(context).edit();
        if (rememberLogin) {
            editor.putBoolean(KEY_LOGGED_IN, true).putString(KEY_USERNAME, username);
        } else {
            editor.remove(KEY_LOGGED_IN).remove(KEY_USERNAME);
        }
        editor.apply();
    }

    public static void logout(Context context) {
        processSessionActive = false;
        processUsername = "";
        preferences(context).edit().clear().apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext()
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }
}
