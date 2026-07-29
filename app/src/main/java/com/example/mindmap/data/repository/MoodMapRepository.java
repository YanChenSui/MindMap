package com.example.mindmap.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.example.mindmap.data.local.AppDatabase;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.profile.UserProfile;
import com.example.mindmap.profile.UserProfileSession;
import com.example.mindmap.service.mosaic.VideoMosaicProcessor;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.DistanceUtils;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 统一封装数据库读写和文件目录。UI 只观察 LiveData 或调用 Repository 方法。
 */
public class MoodMapRepository {
    private static final String TAG = "MoodMapRepository";
    private final Context appContext;
    private final AppDatabase database;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();

    public MoodMapRepository(Context context, AppDatabase database) {
        this.appContext = context.getApplicationContext();
        this.database = database;
    }

    public LiveData<List<TripEntity>> observeTrips() {
        return database.tripDao().observeAllTrips();
    }

    public LiveData<TripEntity> observeTrip(long tripId) {
        return database.tripDao().observeTrip(tripId);
    }

    public LiveData<List<TrackPointEntity>> observeTrackPoints(long tripId) {
        return database.trackPointDao().observeByTrip(tripId);
    }

    public LiveData<List<AnnotationEntity>> observeAnnotations(long tripId) {
        return database.annotationDao().observeByTrip(tripId);
    }

    public LiveData<List<RosPredictionEntity>> observeRosPredictions(long annotationId) {
        return database.rosPredictionDao().observeByAnnotation(annotationId);
    }

    public LiveData<List<RosPredictionEntity>> observeRosPredictionsByTrip(long tripId) {
        return database.rosPredictionDao().observeByTrip(tripId);
    }

    public void createTrip(String name, String destination, String recordMode, Consumer<Long> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                TripEntity active = database.tripDao().getActiveTripSync();
                if (active != null) {
                    callback.accept(active.id);
                    return;
                }
                long now = System.currentTimeMillis();
                TripEntity trip = new TripEntity(name, destination, recordMode, now, AppConstants.TRIP_STATUS_ACTIVE, now);
                UserProfile profile = UserProfileSession.currentProfile(appContext);
                trip.accountName = profile.accountName;
                trip.gender = profile.gender;
                trip.ageGroup = profile.ageGroup;
                trip.educationLevel = profile.educationLevel;
                long id = database.tripDao().insert(trip);
                callback.accept(id);
            } catch (Throwable throwable) {
                Log.e(TAG, "创建行程失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void getActiveTrip(Consumer<TripEntity> callback) {
        ioExecutor.execute(() -> callback.accept(database.tripDao().getActiveTripSync()));
    }

    public void insertTrackPoint(TrackPointEntity point) {
        ioExecutor.execute(() -> {
            try {
                TrackPointEntity last = database.trackPointDao().getLastPointSync(point.tripId);
                double distance = 0d;
                TripEntity trip = database.tripDao().getTripSync(point.tripId);
                if (trip == null || AppConstants.TRIP_STATUS_FINISHED.equals(trip.status)) {
                    return;
                }
                if (last != null) {
                    distance = DistanceUtils.haversineMeters(last.latitude, last.longitude, point.latitude, point.longitude);
                    long deltaTime = Math.max(1L, point.timestamp - last.timestamp);
                    double speed = distance / (deltaTime / 1000d);
                    if (point.timestamp <= last.timestamp || point.accuracy > AppConstants.MAX_ACCEPTED_ACCURACY_METERS || speed > AppConstants.MAX_REASONABLE_SPEED_MPS) {
                        return;
                    }
                }
                database.trackPointDao().insert(point);
                int count = database.annotationDao().countByTripSync(point.tripId);
                long duration = Math.max(0L, System.currentTimeMillis() - trip.startTime);
                database.tripDao().updateTripProgress(point.tripId, trip.distanceMeters + distance, count, duration);
            } catch (Throwable throwable) {
                Log.e(TAG, "写入轨迹点失败", throwable);
            }
        });
    }

    public void insertAnnotation(AnnotationEntity annotation, Consumer<Long> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                long id = database.annotationDao().insert(annotation);
                TripEntity trip = database.tripDao().getTripSync(annotation.tripId);
                if (trip != null) {
                    int count = database.annotationDao().countByTripSync(annotation.tripId);
                    database.tripDao().updateTripProgress(annotation.tripId, trip.distanceMeters, count, Math.max(0L, System.currentTimeMillis() - trip.startTime));
                }
                callback.accept(id);
            } catch (Throwable throwable) {
                Log.e(TAG, "写入标注失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void insertRosPrediction(RosPredictionEntity prediction, Consumer<Long> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                long id = database.rosPredictionDao().insert(prediction);
                callback.accept(id);
            } catch (Throwable throwable) {
                Log.e(TAG, "保存 ROS 模型预测失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void mosaicAnnotationVideo(long annotationId,
                                      Consumer<Integer> progressCallback,
                                      Consumer<AnnotationEntity> callback,
                                      Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            AnnotationEntity annotation = null;
            try {
                annotation = database.annotationDao().getByIdSync(annotationId);
                if (annotation == null) {
                    throw new IllegalArgumentException("Annotation not found: " + annotationId);
                }
                if (annotation.videoUri == null || annotation.videoUri.isEmpty()) {
                    throw new IllegalArgumentException("Annotation has no video");
                }
                annotation.videoMosaicStatus = AppConstants.MOSAIC_STATUS_PROCESSING;
                annotation.videoMosaicError = "";
                database.annotationDao().update(annotation);

                VideoMosaicProcessor processor = new VideoMosaicProcessor(appContext);
                File output = processor.process(annotation.videoUri, getMediaDir("mosaic_videos"), progressCallback::accept);
                String originalUri = annotation.originalVideoUri == null || annotation.originalVideoUri.isEmpty()
                        ? annotation.videoUri
                        : annotation.originalVideoUri;
                annotation.originalVideoUri = originalUri;
                annotation.blurredVideoUri = output.toURI().toString();
                annotation.videoUri = annotation.blurredVideoUri;
                annotation.videoMosaicStatus = AppConstants.MOSAIC_STATUS_SUCCESS;
                annotation.videoMosaicError = "";
                database.annotationDao().update(annotation);
                callback.accept(annotation);
            } catch (Throwable throwable) {
                Log.e(TAG, "视频打码失败", throwable);
                if (annotation != null) {
                    annotation.videoMosaicStatus = AppConstants.MOSAIC_STATUS_FAILED;
                    annotation.videoMosaicError = throwable.getMessage();
                    database.annotationDao().update(annotation);
                }
                errorCallback.accept(throwable);
            }
        });
    }

    public void getRosPredictions(long annotationId, Consumer<List<RosPredictionEntity>> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                callback.accept(database.rosPredictionDao().getByAnnotationSync(annotationId));
            } catch (Throwable throwable) {
                Log.e(TAG, "读取 ROS 模型预测失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void finishTrip(long tripId, Consumer<Boolean> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                TripEntity trip = database.tripDao().getTripSync(tripId);
                if (trip == null) {
                    callback.accept(false);
                    return;
                }
                long now = System.currentTimeMillis();
                trip.endTime = now;
                trip.durationMillis = Math.max(0L, now - trip.startTime);
                trip.annotationCount = database.annotationDao().countByTripSync(tripId);
                trip.status = AppConstants.TRIP_STATUS_FINISHED;
                database.tripDao().update(trip);
                callback.accept(true);
            } catch (Throwable throwable) {
                Log.e(TAG, "结束行程失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void renameTrip(long tripId, String newName, Consumer<Boolean> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                TripEntity trip = database.tripDao().getTripSync(tripId);
                if (trip == null) {
                    callback.accept(false);
                    return;
                }
                trip.name = newName;
                database.tripDao().update(trip);
                callback.accept(true);
            } catch (Throwable throwable) {
                Log.e(TAG, "重命名行程失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void deleteTrip(long tripId, Consumer<Boolean> callback, Consumer<Throwable> errorCallback) {
        ioExecutor.execute(() -> {
            try {
                TripEntity trip = database.tripDao().getTripSync(tripId);
                if (trip != null) {
                    database.tripDao().delete(trip);
                }
                callback.accept(true);
            } catch (Throwable throwable) {
                Log.e(TAG, "删除行程失败", throwable);
                errorCallback.accept(throwable);
            }
        });
    }

    public void loadTripBundle(long tripId, TripBundleCallback callback) {
        ioExecutor.execute(() -> {
            try {
                callback.onLoaded(
                        database.tripDao().getTripSync(tripId),
                        database.trackPointDao().getByTripSync(tripId),
                        database.annotationDao().getByTripSync(tripId),
                        database.rosPredictionDao().getByTripSync(tripId));
            } catch (Throwable throwable) {
                Log.e(TAG, "读取行程详情失败", throwable);
                callback.onError(throwable);
            }
        });
    }

    public File getExportDir() {
        File dir = new File(appContext.getExternalFilesDir(null), "exports");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "导出目录创建失败: " + dir);
        }
        return dir;
    }

    public File getMediaDir(String type) {
        File dir = new File(appContext.getExternalFilesDir(null), type);
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "媒体目录创建失败: " + dir);
        }
        return dir;
    }

    public interface TripBundleCallback {
        void onLoaded(TripEntity trip, List<TrackPointEntity> points, List<AnnotationEntity> annotations, List<RosPredictionEntity> predictions);
        void onError(Throwable throwable);
    }
}
