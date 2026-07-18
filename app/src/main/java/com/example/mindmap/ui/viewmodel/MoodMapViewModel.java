package com.example.mindmap.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.mindmap.MoodMapApplication;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.data.repository.MoodMapRepository;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.MoodUtils;
import com.example.mindmap.util.TimeFormatUtils;

import java.util.List;
import java.util.function.Consumer;

/**
 * 主 ViewModel。通过 Application 获取 Repository，Fragment 不直接操作 DAO。
 */
public class MoodMapViewModel extends AndroidViewModel {
    private final MoodMapRepository repository;
    private final MutableLiveData<Long> activeTripId = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();

    public MoodMapViewModel(@NonNull Application application) {
        super(application);
        repository = ((MoodMapApplication) application).getRepository();
        repository.getActiveTrip(trip -> {
            if (trip != null) {
                activeTripId.postValue(trip.id);
            }
        });
    }

    public MoodMapRepository getRepository() {
        return repository;
    }

    public LiveData<List<TripEntity>> observeTrips() {
        return repository.observeTrips();
    }

    public LiveData<TripEntity> observeTrip(long tripId) {
        return repository.observeTrip(tripId);
    }

    public LiveData<List<TrackPointEntity>> observeTrackPoints(long tripId) {
        return repository.observeTrackPoints(tripId);
    }

    public LiveData<List<AnnotationEntity>> observeAnnotations(long tripId) {
        return repository.observeAnnotations(tripId);
    }

    public LiveData<List<RosPredictionEntity>> observeRosPredictions(long annotationId) {
        return repository.observeRosPredictions(annotationId);
    }

    public LiveData<List<RosPredictionEntity>> observeRosPredictionsByTrip(long tripId) {
        return repository.observeRosPredictionsByTrip(tripId);
    }

    public LiveData<Long> getActiveTripId() {
        return activeTripId;
    }

    public LiveData<String> getMessage() {
        return message;
    }

    public void startTrip(String rawName, String destination, boolean autoMode) {
        String name = rawName == null || rawName.trim().isEmpty()
                ? "未命名行程 " + TimeFormatUtils.fileSafeDate(System.currentTimeMillis())
                : rawName.trim();
        repository.createTrip(name, destination == null ? "" : destination.trim(), autoMode ? AppConstants.RECORD_MODE_AUTO : AppConstants.RECORD_MODE_MANUAL,
                id -> activeTripId.postValue(id),
                throwable -> message.postValue("创建行程失败，请稍后重试"));
    }

    public void saveAnnotation(AnnotationEntity annotation, int pleasure, int calm, int relaxation, int focus) {
        try {
            annotation.pleasureScore = pleasure;
            annotation.calmScore = calm;
            annotation.relaxationScore = relaxation;
            annotation.focusScore = focus;
            annotation.averageScore = MoodUtils.averageScore(pleasure, calm, relaxation, focus);
            annotation.visualPreferenceScore = pleasure;
            annotation.thoughtClarityScore = pleasure;
            annotation.worryForgetScore = pleasure;
            annotation.restoredRelaxedScore = relaxation;
            annotation.rosCalmScore = calm;
            annotation.interestScore = pleasure;
            annotation.focusedAlertScore = focus;
            annotation.rosAverageScore = MoodUtils.rosAverageScore(
                    annotation.visualPreferenceScore,
                    annotation.thoughtClarityScore,
                    annotation.worryForgetScore,
                    annotation.restoredRelaxedScore,
                    annotation.rosCalmScore,
                    annotation.interestScore,
                    annotation.focusedAlertScore);
            repository.insertAnnotation(annotation,
                    id -> message.postValue("标注已保存"),
                    throwable -> message.postValue("保存标注失败"));
        } catch (IllegalArgumentException exception) {
            message.setValue(exception.getMessage());
        }
    }

    public void saveRosAnnotation(AnnotationEntity annotation, int visualPreference, int thoughtClarity,
                                  int worryForget, int restoredRelaxed, int calm,
                                  int interest, int focusedAlert) {
        saveRosAnnotation(annotation, visualPreference, thoughtClarity, worryForget, restoredRelaxed,
                calm, interest, focusedAlert, null);
    }

    public void saveRosAnnotation(AnnotationEntity annotation, int visualPreference, int thoughtClarity,
                                  int worryForget, int restoredRelaxed, int calm,
                                  int interest, int focusedAlert, Consumer<Long> savedCallback) {
        try {
            annotation.visualPreferenceScore = visualPreference;
            annotation.thoughtClarityScore = thoughtClarity;
            annotation.worryForgetScore = worryForget;
            annotation.restoredRelaxedScore = restoredRelaxed;
            annotation.rosCalmScore = calm;
            annotation.interestScore = interest;
            annotation.focusedAlertScore = focusedAlert;
            annotation.rosAverageScore = MoodUtils.rosAverageScore(visualPreference, thoughtClarity,
                    worryForget, restoredRelaxed, calm, interest, focusedAlert);

            annotation.pleasureScore = visualPreference;
            annotation.calmScore = calm;
            annotation.relaxationScore = restoredRelaxed;
            annotation.focusScore = focusedAlert;
            annotation.averageScore = annotation.rosAverageScore;

            repository.insertAnnotation(annotation,
                    id -> {
                        message.postValue("标注已保存");
                        if (savedCallback != null) {
                            savedCallback.accept(id);
                        }
                    },
                    throwable -> message.postValue("保存标注失败"));
        } catch (IllegalArgumentException exception) {
            message.setValue(exception.getMessage());
        }
    }

    public void finishTrip(long tripId) {
        activeTripId.setValue(null);
        repository.finishTrip(tripId,
                success -> {
                    message.postValue(success ? "行程已结束" : "未找到行程");
                },
                throwable -> message.postValue("结束行程失败"));
    }

    public void saveRosPrediction(RosPredictionEntity prediction) {
        repository.insertRosPrediction(prediction,
                id -> message.postValue("ROS 模型预测已保存"),
                throwable -> message.postValue("保存 ROS 模型预测失败"));
    }

    public void deleteTrip(long tripId) {
        Long activeId = activeTripId.getValue();
        if (activeId != null && activeId == tripId) {
            activeTripId.setValue(null);
        }
        repository.deleteTrip(tripId,
                success -> message.postValue("行程已删除"),
                throwable -> message.postValue("删除行程失败"));
    }

    public void renameTrip(long tripId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            message.setValue("行程名称不能为空");
            return;
        }
        repository.renameTrip(tripId, name,
                success -> message.postValue(success ? "行程已重命名" : "未找到行程"),
                throwable -> message.postValue("重命名失败"));
    }
}
