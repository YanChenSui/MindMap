package com.example.mindmap.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * GPS 轨迹点，同时保存最近一次加速度和手机朝向，便于后续分析移动状态。
 */
@Entity(
        tableName = "track_points",
        foreignKeys = @ForeignKey(
                entity = TripEntity.class,
                parentColumns = "id",
                childColumns = "tripId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("tripId"), @Index("timestamp"), @Index({"tripId", "timestamp"})}
)
public class TrackPointEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long tripId;
    public double latitude;
    public double longitude;
    public double altitude;
    public float accuracy;
    public float speed;
    public float bearing;
    public long timestamp;
    public float accelerationX;
    public float accelerationY;
    public float accelerationZ;
    public float pitch;
    public float roll;
    public float yaw;
    public String movingState;

    public TrackPointEntity(long tripId, double latitude, double longitude, double altitude, float accuracy,
                            float speed, float bearing, long timestamp, float accelerationX, float accelerationY,
                            float accelerationZ, float pitch, float roll, float yaw, String movingState) {
        this.tripId = tripId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
        this.accuracy = accuracy;
        this.speed = speed;
        this.bearing = bearing;
        this.timestamp = timestamp;
        this.accelerationX = accelerationX;
        this.accelerationY = accelerationY;
        this.accelerationZ = accelerationZ;
        this.pitch = pitch;
        this.roll = roll;
        this.yaw = yaw;
        this.movingState = movingState;
    }
}
