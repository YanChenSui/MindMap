package com.example.mindmap.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.mindmap.data.local.entity.RosPredictionEntity;

import java.util.List;

@Dao
public interface RosPredictionDao {
    @Insert
    long insert(RosPredictionEntity prediction);

    @Query("SELECT * FROM ros_predictions WHERE annotationId = :annotationId ORDER BY createdAt DESC")
    LiveData<List<RosPredictionEntity>> observeByAnnotation(long annotationId);

    @Query("SELECT ros_predictions.* FROM ros_predictions INNER JOIN annotations ON ros_predictions.annotationId = annotations.id WHERE annotations.tripId = :tripId ORDER BY ros_predictions.createdAt DESC")
    LiveData<List<RosPredictionEntity>> observeByTrip(long tripId);

    @Query("SELECT ros_predictions.* FROM ros_predictions INNER JOIN annotations ON ros_predictions.annotationId = annotations.id WHERE annotations.tripId = :tripId ORDER BY ros_predictions.createdAt DESC")
    List<RosPredictionEntity> getByTripSync(long tripId);

    @Query("SELECT * FROM ros_predictions WHERE annotationId = :annotationId ORDER BY createdAt DESC")
    List<RosPredictionEntity> getByAnnotationSync(long annotationId);

    @Query("DELETE FROM ros_predictions WHERE annotationId = :annotationId")
    void deleteByAnnotation(long annotationId);
}
