package com.example.mindmap.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.mindmap.MainActivity;
import com.example.mindmap.MoodMapApplication;
import com.example.mindmap.R;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.repository.MoodMapRepository;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.MovingStateDetector;

/**
 * 前台定位服务。行程开始后持续接收 GPS/Network 位置，在后台也能写入 Room。
 */
public class LocationTrackingService extends Service implements LocationListener {
    public static final String ACTION_START = "com.example.mindmap.action.START_TRACKING";
    public static final String ACTION_STOP = "com.example.mindmap.action.STOP_TRACKING";
    public static final String EXTRA_TRIP_ID = "trip_id";
    private static final String TAG = "LocationTrackingService";
    private static final String CHANNEL_ID = "mood_map_tracking";
    private static final int NOTIFICATION_ID = 1001;

    private LocationManager locationManager;
    private SensorCollector sensorCollector;
    private MoodMapRepository repository;
    private long tripId = -1L;
    private boolean listening;

    @Override
    public void onCreate() {
        super.onCreate();
        repository = ((MoodMapApplication) getApplication()).getRepository();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        sensorCollector = new SensorCollector(getApplicationContext());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopTracking();
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && intent.hasExtra(EXTRA_TRIP_ID)) {
            tripId = intent.getLongExtra(EXTRA_TRIP_ID, -1L);
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        startTracking();
        return START_STICKY;
    }

    private void startTracking() {
        if (listening || tripId <= 0L) {
            return;
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "缺少定位权限，无法启动前台定位服务");
            stopSelf();
            return;
        }
        try {
            sensorCollector.start();
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, AppConstants.LOCATION_INTERVAL_MILLIS, 0f, this);
            }
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, AppConstants.LOCATION_INTERVAL_MILLIS, 0f, this);
            listening = true;
        } catch (Throwable throwable) {
            Log.e(TAG, "启动定位监听失败", throwable);
            stopSelf();
        }
    }

    private void stopTracking() {
        try {
            if (locationManager != null) {
                locationManager.removeUpdates(this);
            }
        } catch (Throwable throwable) {
            Log.e(TAG, "停止定位监听失败", throwable);
        } finally {
            listening = false;
            if (sensorCollector != null) {
                sensorCollector.stop();
            }
            stopForeground(STOP_FOREGROUND_REMOVE);
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (location == null || tripId <= 0L || location.getAccuracy() > AppConstants.MAX_ACCEPTED_ACCURACY_METERS) {
            return;
        }
        SensorCollector.SensorSnapshot snapshot = sensorCollector.getLatestSnapshot();
        String movingState = MovingStateDetector.detect(location.hasSpeed() ? location.getSpeed() : -1f,
                snapshot.accelerationX, snapshot.accelerationY, snapshot.accelerationZ);
        TrackPointEntity point = new TrackPointEntity(
                tripId,
                location.getLatitude(),
                location.getLongitude(),
                location.hasAltitude() ? location.getAltitude() : 0d,
                location.getAccuracy(),
                location.hasSpeed() ? location.getSpeed() : 0f,
                location.hasBearing() ? location.getBearing() : 0f,
                location.getTime() > 0L ? location.getTime() : System.currentTimeMillis(),
                snapshot.accelerationX,
                snapshot.accelerationY,
                snapshot.accelerationZ,
                snapshot.pitch,
                snapshot.roll,
                snapshot.yaw,
                movingState
        );
        repository.insertTrackPoint(point);
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.w(TAG, "定位 Provider 不可用: " + provider);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        // 兼容旧 API，状态异常通过 Log 记录，不影响服务存活。
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopTracking();
        super.onDestroy();
    }

    private Notification buildNotification() {
        Intent launchIntent = new Intent(this, MainActivity.class);
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, launchIntent, PendingIntent.FLAG_IMMUTABLE);
        Intent stopIntent = new Intent(this, LocationTrackingService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.notification_tracking_title))
                .setContentText(getString(R.string.notification_tracking_text))
                .setOngoing(true)
                .setContentIntent(contentIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop_trip), stopPendingIntent)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel_tracking), NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
