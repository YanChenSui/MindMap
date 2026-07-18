package com.example.mindmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.mindmap.data.local.AppDatabase;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.util.AppConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class RoomDaoInstrumentedTest {
    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class).build();
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void dao_insertQueryAndCascadeDelete() {
        TripEntity trip = new TripEntity("测试行程", "公园", AppConstants.RECORD_MODE_AUTO, 1000L, AppConstants.TRIP_STATUS_ACTIVE, 1000L);
        long tripId = database.tripDao().insert(trip);
        assertTrue(tripId > 0);

        TrackPointEntity point = new TrackPointEntity(tripId, 31.2, 121.4, 0, 5f, 1f, 0f, 1001L, 0f, 0f, 9.8f, 0f, 0f, 0f, AppConstants.MOVING);
        database.trackPointDao().insert(point);
        AnnotationEntity annotation = new AnnotationEntity(tripId, 31.2, 121.4, 1002L);
        database.annotationDao().insert(annotation);

        assertEquals(1, database.trackPointDao().getByTripSync(tripId).size());
        assertEquals(1, database.annotationDao().getByTripSync(tripId).size());
        database.tripDao().delete(database.tripDao().getTripSync(tripId));
        assertEquals(0, database.trackPointDao().getByTripSync(tripId).size());
        assertEquals(0, database.annotationDao().getByTripSync(tripId).size());
    }
}
