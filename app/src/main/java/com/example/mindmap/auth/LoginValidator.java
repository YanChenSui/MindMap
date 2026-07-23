package com.example.mindmap.auth;

public final class LoginValidator {
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_USERNAME_LENGTH = 50;

    private LoginValidator() {}

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim();
    }

    public static boolean isValidUsername(String username) {
        String normalized = normalizeUsername(username);
        return !normalized.isEmpty() && normalized.length() <= MAX_USERNAME_LENGTH;
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= MIN_PASSWORD_LENGTH;
    }
}
