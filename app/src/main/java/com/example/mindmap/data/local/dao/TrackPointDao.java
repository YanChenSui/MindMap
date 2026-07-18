package com.example.mindmap.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindmap.data.local.entity.TrackPointEntity;

import java.util.List;

@Dao
public interface TrackPointDao {
    @Insert
    long insert(TrackPointEntity point);

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    LiveData<List<TrackPointEntity>> observeByTrip(long tripId);

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp ASC")
    List<TrackPointEntity> getByTripSync(long tripId);

    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY timestamp DESC LIMIT 1")
    TrackPointEntity getLastPointSync(long tripId);
}
