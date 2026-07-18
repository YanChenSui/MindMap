package com.example.mindmap.service;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * 采集最近一次加速度与姿态数据。调用方只在 GPS 点到达时读取快照并写入数据库。
 */
public class SensorCollector implements SensorEventListener {
    private static final String TAG = "SensorCollector";
    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final Sensor rotationVector;
    private volatile SensorSnapshot latestSnapshot = new SensorSnapshot();

    public SensorCollector(Context context) {
        sensorManager = (SensorManager) context.getApplicationContext().getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotationVector = sensorManager == null ? null : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void start() {
        if (sensorManager == null) {
            Log.w(TAG, "设备不支持 SensorManager");
            return;
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
        if (rotationVector != null) {
            sensorManager.registerListener(this, rotationVector, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.w(TAG, "设备不支持 Rotation Vector，姿态角将保留默认值");
        }
    }

    public void stop() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    public SensorSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        SensorSnapshot snapshot = latestSnapshot.copy();
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            snapshot.accelerationX = event.values[0];
            snapshot.accelerationY = event.values[1];
            snapshot.accelerationZ = event.values[2];
        } else if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            float[] orientation = new float[3];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            snapshot.yaw = (float) Math.toDegrees(orientation[0]);
            snapshot.pitch = (float) Math.toDegrees(orientation[1]);
            snapshot.roll = (float) Math.toDegrees(orientation[2]);
        }
        snapshot.timestamp = System.currentTimeMillis();
        latestSnapshot = snapshot;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 精度变化不需要即时写库，GPS 点到达时读取最新快照即可。
    }

    public static class SensorSnapshot {
        public float accelerationX;
        public float accelerationY;
        public float accelerationZ;
        public float pitch;
        public float roll;
        public float yaw;
        public long timestamp;

        SensorSnapshot copy() {
            SensorSnapshot copy = new SensorSnapshot();
            copy.accelerationX = accelerationX;
            copy.accelerationY = accelerationY;
            copy.accelerationZ = accelerationZ;
            copy.pitch = pitch;
            copy.roll = roll;
            copy.yaw = yaw;
            copy.timestamp = timestamp;
            return copy;
        }
    }
}
