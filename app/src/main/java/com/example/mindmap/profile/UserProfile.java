package com.example.mindmap.profile;

public final class UserProfile {
    public final String accountName;
    public final String gender;
    public final String ageGroup;
    public final String educationLevel;

    public UserProfile(String accountName, String gender, String ageGroup, String educationLevel) {
        this.accountName = accountName;
        this.gender = gender;
        this.ageGroup = ageGroup;
        this.educationLevel = educationLevel;
    }
}
