package com.example.mindmap.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.example.mindmap.data.local.entity.AnnotationEntity;

import java.util.List;

@Dao
public interface AnnotationDao {
    @Insert
    long insert(AnnotationEntity annotation);

    @Update
    void update(AnnotationEntity annotation);

    @Query("SELECT * FROM annotations WHERE tripId = :tripId ORDER BY timestamp ASC")
    LiveData<List<AnnotationEntity>> observeByTrip(long tripId);

    @Query("SELECT * FROM annotations WHERE tripId = :tripId ORDER BY timestamp ASC")
    List<AnnotationEntity> getByTripSync(long tripId);

    @Query("SELECT COUNT(*) FROM annotations WHERE tripId = :tripId")
    int countByTripSync(long tripId);
}
