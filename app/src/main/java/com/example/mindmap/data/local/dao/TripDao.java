package com.example.mindmap.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mindmap.data.local.entity.TripEntity;

import java.util.List;

@Dao
public interface TripDao {
    @Insert
    long insert(TripEntity trip);

    @Update
    void update(TripEntity trip);

    @Delete
    void delete(TripEntity trip);

    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    LiveData<List<TripEntity>> observeAllTrips();

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    LiveData<TripEntity> observeTrip(long tripId);

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    TripEntity getTripSync(long tripId);

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' ORDER BY createdAt DESC LIMIT 1")
    TripEntity getActiveTripSync();

    @Query("UPDATE trips SET distanceMeters = :distanceMeters, annotationCount = :annotationCount, durationMillis = :durationMillis WHERE id = :tripId")
    void updateTripProgress(long tripId, double distanceMeters, int annotationCount, long durationMillis);
}
