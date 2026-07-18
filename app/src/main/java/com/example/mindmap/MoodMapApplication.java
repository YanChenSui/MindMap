package com.example.mindmap;

import android.app.Application;

import com.example.mindmap.data.local.AppDatabase;
import com.example.mindmap.data.repository.MoodMapRepository;

import com.amap.api.maps.MapsInitializer;

/**
 * Application 负责创建进程级单例对象，避免 Activity 或 Fragment 直接持有 DAO。
 */
public class MoodMapApplication extends Application {
    private MoodMapRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        MapsInitializer.updatePrivacyShow(this, true, true);
        MapsInitializer.updatePrivacyAgree(this, true);
        AppDatabase database = AppDatabase.getInstance(this);
        repository = new MoodMapRepository(this, database);
    }

    public MoodMapRepository getRepository() {
        return repository;
    }
}
