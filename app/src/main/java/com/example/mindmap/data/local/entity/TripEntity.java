package com.example.mindmap.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 一次公园步行情境采集行程。ACTIVE 表示仍在采集，FINISHED 表示用户主动结束。
 */
@Entity(tableName = "trips")
public class TripEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String name;
    public String destination;
    public String recordMode;
    public long startTime;
    public long endTime;
    public long durationMillis;
    public double distanceMeters;
    public int annotationCount;
    public String status;
    public long createdAt;

    public TripEntity(String name, String destination, String recordMode, long startTime, String status, long createdAt) {
        this.name = name;
        this.destination = destination;
        this.recordMode = recordMode;
        this.startTime = startTime;
        this.status = status;
        this.createdAt = createdAt;
    }
}
