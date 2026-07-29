package com.example.mindmap.ui.activetrip;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.PolylineOptions;
import com.example.mindmap.MainActivity;
import com.example.mindmap.R;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.service.MediaRecorderManager;
import com.example.mindmap.service.asr.TranscriptionService;
import com.example.mindmap.service.asr.TranscriptionServiceFactory;
import com.example.mindmap.ui.UiFactory;
import com.example.mindmap.ui.annotation.AnnotationDialogFragment;
import com.example.mindmap.ui.video.VideoRecordActivity;
import com.example.mindmap.ui.viewmodel.MoodMapViewModel;
import com.example.mindmap.util.AmapCoordinateUtils;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.MapMarkerIconUtils;
import com.example.mindmap.util.TimeFormatUtils;
import com.example.mindmap.util.TrackMapUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Active trip page: live track display, media capture and mood annotation entry points. */
public class ActiveTripFragment extends Fragment {
    private static final String ARG_TRIP_ID = "trip_id";

    private MoodMapViewModel viewModel;
    private ActiveTripHost host;
    private long tripId;
    private TripEntity trip;
    private TrackPointEntity latestPoint;
    private TextView statusText;
    private TextView locationText;
    private TextView distanceText;
    private TextView countText;
    private LinearLayout stateContainer;
    private MapView mapView;
    private AMap aMap;
    private List<TrackPointEntity> currentPoints = new ArrayList<>();
    private List<AnnotationEntity> currentAnnotations = new ArrayList<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final MediaRecorderManager recorderManager = new MediaRecorderManager();
    private TranscriptionService transcriptionService;
    private boolean recordingAudio;
    private String lastAudioPath;
    private String lastSpeechText;
    private long lastAudioDurationMillis;
    private String pendingVideoUri;
    private String pendingVideoThumbnailUri;
    private long pendingVideoDurationMillis;
    private MaterialButton audioButton;
    private ActivityResultLauncher<Intent> videoLauncher;

    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            updateStatusText();
            updateEnvironmentState();
            timerHandler.postDelayed(this, 1000L);
        }
    };

    private final Runnable audioTimeoutRunnable = () -> stopAudioRecording(true, true);

    public static ActiveTripFragment newInstance(long tripId) {
        ActiveTripFragment fragment = new ActiveTripFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_TRIP_ID, tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tripId = requireArguments().getLong(ARG_TRIP_ID);
        transcriptionService = TranscriptionServiceFactory.create(requireContext());
        getParentFragmentManager().setFragmentResultListener(AnnotationDialogFragment.REQUEST_ANNOTATION_SAVED, this,
                (requestKey, result) -> clearPendingMedia());
        getParentFragmentManager().setFragmentResultListener(AnnotationDialogFragment.REQUEST_ANNOTATION_DISCARDED, this,
                (requestKey, result) -> clearPendingMedia());
        videoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
                String uri = result.getData().getStringExtra(VideoRecordActivity.EXTRA_VIDEO_URI);
                String thumbnailUri = result.getData().getStringExtra(VideoRecordActivity.EXTRA_VIDEO_THUMBNAIL_URI);
                long videoDuration = result.getData().getLongExtra(VideoRecordActivity.EXTRA_VIDEO_DURATION_MILLIS, 0L);
                pendingVideoUri = uri;
                pendingVideoThumbnailUri = thumbnailUri;
                pendingVideoDurationMillis = videoDuration;
                lastSpeechText = "";
                File videoFile = fileFromUri(uri);
                if (videoFile != null) {
                    Snackbar.make(requireView(), "视频已保存，正在转写视频声音：" + displayMediaUri(uri), Snackbar.LENGTH_LONG).show();
                    startMediaFileTranscription(videoFile, "视频声音");
                    return;
                }
                Snackbar.make(requireView(), "视频已保存：" + displayMediaUri(uri), Snackbar.LENGTH_LONG).show();
                showAnnotationDialog(uri, lastAudioPath, thumbnailUri, Math.max(videoDuration, lastAudioDurationMillis));
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        host = (ActiveTripHost) requireActivity();
        viewModel = ((MainActivity) requireActivity()).getSharedViewModel();

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xfff4faf4);
        root.setPadding(UiFactory.dp(requireContext(), UiFactory.PAGE_HORIZONTAL_PADDING_DP),
                UiFactory.dp(requireContext(), 16),
                UiFactory.dp(requireContext(), UiFactory.PAGE_HORIZONTAL_PADDING_DP),
                UiFactory.dp(requireContext(), 12));

        root.addView(createStatusCard());
        stateContainer = new LinearLayout(requireContext());
        stateContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(stateContainer);
        root.addView(createActionRow());

        CardView mapCard = UiFactory.card(requireContext());
        mapView = new MapView(requireContext());
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        configureAmap();
        mapCard.addView(mapView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(mapCard, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        MaterialButton finishButton = UiFactory.primaryButton(requireContext(), "结束行程");
        finishButton.setOnClickListener(v -> confirmFinish());
        root.addView(finishButton);

        observeData();
        updateEnvironmentState();
        return root;
    }

    private View createStatusCard() {
        CardView card = UiFactory.card(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        statusText = UiFactory.sectionTitle(requireContext(), "● 正在采集");
        statusText.setTextColor(requireContext().getColor(R.color.park_green));
        locationText = UiFactory.mutedText(requireContext(), "当前位置：等待 GPS");
        distanceText = UiFactory.mutedText(requireContext(), "已行走：0 m");
        countText = UiFactory.mutedText(requireContext(), "手动标记：0处");
        content.addView(statusText);
        content.addView(locationText);
        content.addView(distanceText);
        content.addView(countText);
        card.addView(content);
        return card;
    }

    private View createActionRow() {
        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, UiFactory.dp(requireContext(), 4), 0, UiFactory.dp(requireContext(), 4));
        MaterialButton videoButton = UiFactory.secondaryButton(requireContext(), "录视频");
        videoButton.setOnClickListener(v -> openVideoRecorder());
        MaterialButton noteButton = UiFactory.secondaryButton(requireContext(), "记感受");
        noteButton.setOnClickListener(v -> showAnnotationDialog(pendingVideoUri, lastAudioPath,
                pendingVideoThumbnailUri, Math.max(pendingVideoDurationMillis, lastAudioDurationMillis)));
        audioButton = UiFactory.secondaryButton(requireContext(), "录音");
        audioButton.setOnClickListener(v -> toggleAudio());
        addActionButton(actions, videoButton);
        addActionButton(actions, noteButton);
        addActionButton(actions, audioButton);
        return actions;
    }

    private void addActionButton(LinearLayout row, MaterialButton button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(UiFactory.dp(requireContext(), 4), 0, UiFactory.dp(requireContext(), 4), 0);
        row.addView(button, params);
    }

    private void configureAmap() {
        if (aMap == null) {
            return;
        }
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setMyLocationButtonEnabled(true);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.setMapType(AMap.MAP_TYPE_NORMAL);
        MyLocationStyle style = new MyLocationStyle();
        style.myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE_NO_CENTER);
        style.strokeColor(0x332e7d32);
        style.radiusFillColor(0x112e7d32);
        aMap.setMyLocationStyle(style);
        aMap.setMyLocationEnabled(true);
        aMap.moveCamera(CameraUpdateFactory.zoomTo(16f));
    }

    private void observeData() {
        viewModel.observeTrip(tripId).observe(getViewLifecycleOwner(), value -> {
            trip = value;
            updateStatusText();
        });
        viewModel.observeTrackPoints(tripId).observe(getViewLifecycleOwner(), this::renderTrack);
        viewModel.observeAnnotations(tripId).observe(getViewLifecycleOwner(), this::renderMapAnnotations);
    }

    private void updateEnvironmentState() {
        if (stateContainer == null) {
            return;
        }
        stateContainer.removeAllViews();
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stateContainer.addView(UiFactory.stateCard(requireContext(), "定位权限未授权", "请在系统设置中允许定位权限，否则无法记录轨迹"));
            return;
        }
        LocationManager locationManager = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        boolean gpsEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean networkEnabled = locationManager != null && locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!gpsEnabled && !networkEnabled) {
            CardView card = UiFactory.stateCard(requireContext(), "定位服务已关闭", "请打开系统定位开关后继续采集");
            card.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
            stateContainer.addView(card);
        } else if (currentPoints.isEmpty()) {
            stateContainer.addView(UiFactory.stateCard(requireContext(), "等待定位", "正在获取第一个有效 GPS 点"));
        }
        addVideoMosaicState();
    }

    private void addVideoMosaicState() {
        if (pendingVideoUri != null && !pendingVideoUri.isEmpty()) {
            CardView card = UiFactory.stateCard(requireContext(), "视频待保存", "请先在弹出的标注问卷中点击保存；保存后这里会显示打码入口。");
            card.setOnClickListener(v -> showAnnotationDialog(pendingVideoUri, lastAudioPath,
                    pendingVideoThumbnailUri, Math.max(pendingVideoDurationMillis, lastAudioDurationMillis)));
            stateContainer.addView(card);
            return;
        }
        AnnotationEntity target = latestVideoAnnotation();
        if (target == null) {
            return;
        }
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(UiFactory.cardTitle(requireContext(), "视频人脸打码"));
        body.addView(UiFactory.mutedText(requireContext(), "状态：" + safeMosaicStatus(target.videoMosaicStatus)));
        body.addView(UiFactory.mutedText(requireContext(), "视频：" + displayMediaUri(target.videoUri)));
        if (AppConstants.MOSAIC_STATUS_PROCESSING.equals(target.videoMosaicStatus)) {
            body.addView(createMosaicProgressBar());
            body.addView(UiFactory.mutedText(requireContext(), "正在处理视频，请保持应用打开"));
        }
        MaterialButton button = UiFactory.secondaryButton(requireContext(),
                AppConstants.MOSAIC_STATUS_PROCESSING.equals(target.videoMosaicStatus) ? "打码处理中" : "高斯模糊打码");
        button.setEnabled(!AppConstants.MOSAIC_STATUS_PROCESSING.equals(target.videoMosaicStatus));
        button.setOnClickListener(v -> confirmMosaicVideo(target));
        body.addView(button);
        card.addView(body);
        stateContainer.addView(card);
    }

    @Nullable
    private AnnotationEntity latestVideoAnnotation() {
        for (int i = currentAnnotations.size() - 1; i >= 0; i--) {
            AnnotationEntity annotation = currentAnnotations.get(i);
            if (annotation.videoUri != null && !annotation.videoUri.isEmpty()) {
                return annotation;
            }
        }
        return null;
    }

    private ProgressBar createMosaicProgressBar() {
        ProgressBar progressBar = new ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setIndeterminate(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, UiFactory.dp(requireContext(), 8), 0, UiFactory.dp(requireContext(), 4));
        progressBar.setLayoutParams(params);
        return progressBar;
    }

    private void renderTrack(List<TrackPointEntity> points) {
        currentPoints = points == null ? new ArrayList<>() : points;
        if (currentPoints.isEmpty()) {
            updateEnvironmentState();
            return;
        }
        latestPoint = currentPoints.get(currentPoints.size() - 1);
        locationText.setText("当前位置：" + TimeFormatUtils.coordinate(latestPoint.latitude) + ", "
                + TimeFormatUtils.coordinate(latestPoint.longitude) + "  状态：" + TimeFormatUtils.movingState(latestPoint.movingState));
        redrawMap();
        updateEnvironmentState();
    }

    private void redrawMap() {
        if (aMap == null) {
            return;
        }
        aMap.clear();
        List<LatLng> latLngs = TrackMapUtils.buildDisplayRoute(requireContext(), currentPoints);
        if (latLngs.size() >= 2) {
            aMap.addPolyline(new PolylineOptions()
                    .addAll(latLngs)
                    .width(8f)
                    .color(requireContext().getColor(R.color.park_green))
                    .geodesic(false));
        }
        if (!latLngs.isEmpty()) {
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.get(latLngs.size() - 1), 17f));
        }
        renderMarkersOnly(currentAnnotations);
    }

    private void renderMapAnnotations(List<AnnotationEntity> annotations) {
        currentAnnotations = annotations == null ? new ArrayList<>() : annotations;
        countText.setText("手动标记：" + currentAnnotations.size() + "处");
        redrawMap();
        updateEnvironmentState();
    }

    private void renderMarkersOnly(@Nullable List<AnnotationEntity> annotations) {
        if (annotations == null || aMap == null) {
            return;
        }
        for (int i = 0; i < annotations.size(); i++) {
            AnnotationEntity annotation = annotations.get(i);
            LatLng latLng = AmapCoordinateUtils.fromGps(requireContext(), annotation.latitude, annotation.longitude);
            aMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title("第 " + (i + 1) + " 个手动标记")
                    .snippet(buildAnnotationSnippet(annotation))
                    .icon(MapMarkerIconUtils.numberedManualMark(requireContext(), i + 1)));
        }
    }

    private String buildAnnotationSnippet(AnnotationEntity annotation) {
        String note = annotation.textNote == null ? "" : annotation.textNote;
        String speech = annotation.speechText == null ? "" : annotation.speechText;
        return note + (speech.isEmpty() ? "" : "\n转写：" + speech);
    }

    private float markerHue(float averageScore) {
        if (averageScore >= 4f) {
            return BitmapDescriptorFactory.HUE_GREEN;
        }
        if (averageScore >= 3f) {
            return BitmapDescriptorFactory.HUE_ORANGE;
        }
        return BitmapDescriptorFactory.HUE_RED;
    }

    private void updateStatusText() {
        if (trip == null || statusText == null || distanceText == null) {
            return;
        }
        long duration = System.currentTimeMillis() - trip.startTime;
        statusText.setText("● 正在采集  " + trip.name + "  " + TimeFormatUtils.duration(duration));
        distanceText.setText("已行走：" + TimeFormatUtils.distance(trip.distanceMeters));
    }

    private void openVideoRecorder() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(requireView(), "缺少相机或录音权限", Snackbar.LENGTH_SHORT).show();
            return;
        }
        videoLauncher.launch(new Intent(requireContext(), VideoRecordActivity.class));
    }

    private void toggleAudio() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Snackbar.make(requireView(), "缺少录音权限", Snackbar.LENGTH_SHORT).show();
            return;
        }
        try {
            if (!recordingAudio) {
                File file = recorderManager.start(requireContext(), viewModel.getRepository().getMediaDir("audio"));
                lastAudioPath = Uri.fromFile(file).toString();
                lastAudioDurationMillis = 0L;
                recordingAudio = true;
                audioButton.setText("停止");
                timerHandler.postDelayed(audioTimeoutRunnable, AppConstants.MAX_AUDIO_DURATION_MILLIS);
            } else {
                stopAudioRecording(false, true);
            }
        } catch (Throwable throwable) {
            recordingAudio = false;
            audioButton.setText("录音");
            timerHandler.removeCallbacks(audioTimeoutRunnable);
            Snackbar.make(requireView(), "录音失败，请检查权限和存储空间", Snackbar.LENGTH_LONG).show();
        }
    }

    private void stopAudioRecording(boolean reachedLimit, boolean launchSpeech) {
        try {
            MediaRecorderManager.RecordingResult result = recorderManager.stop();
            if (result.file != null) {
                lastAudioPath = Uri.fromFile(result.file).toString();
            }
            lastAudioDurationMillis = result.durationMillis;
            recordingAudio = false;
            audioButton.setText("录音");
            timerHandler.removeCallbacks(audioTimeoutRunnable);
            if (launchSpeech) {
                String message = reachedLimit ? "录音已到 60 秒并自动保存，正在本地转写" : "录音已保存，正在本地转写";
                Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG).show();
                startMediaFileTranscription(result.file, "音频文件");
            }
        } catch (Throwable throwable) {
            recordingAudio = false;
            audioButton.setText("录音");
            timerHandler.removeCallbacks(audioTimeoutRunnable);
            Snackbar.make(requireView(), "录音保存失败，请检查存储空间", Snackbar.LENGTH_LONG).show();
        }
    }

    private void startMediaFileTranscription(@Nullable File mediaFile, @NonNull String mediaLabel) {
        transcriptionService.transcribe(mediaFile, new TranscriptionService.Callback() {
            @Override
            public void onSuccess(String text) {
                lastSpeechText = text == null ? "" : text;
                if (!isAdded() || getView() == null) {
                    return;
                }
                if (mediaLabel != null) {
                    Snackbar.make(requireView(), mediaLabel + "转写完成，可保存到标注", Snackbar.LENGTH_LONG).show();
                    showAnnotationDialog(pendingVideoUri, lastAudioPath, pendingVideoThumbnailUri,
                            Math.max(pendingVideoDurationMillis, lastAudioDurationMillis));
                    return;
                }
                Snackbar.make(requireView(), "音频文件转写完成，可保存到标注", Snackbar.LENGTH_LONG).show();
                showAnnotationDialog(pendingVideoUri, lastAudioPath, pendingVideoThumbnailUri,
                        Math.max(pendingVideoDurationMillis, lastAudioDurationMillis));
            }

            @Override
            public void onError(Throwable throwable) {
                lastSpeechText = "";
                if (!isAdded() || getView() == null) {
                    return;
                }
                Snackbar.make(requireView(), "音频已保存，但自动转写失败：" + throwable.getMessage(), Snackbar.LENGTH_LONG).show();
                showAnnotationDialog(pendingVideoUri, lastAudioPath, pendingVideoThumbnailUri,
                        Math.max(pendingVideoDurationMillis, lastAudioDurationMillis));
            }
        });
    }

    @Nullable
    private File fileFromUri(@Nullable String uri) {
        if (uri == null || uri.isEmpty()) {
            return null;
        }
        Uri parsed = Uri.parse(uri);
        if ("file".equals(parsed.getScheme()) || parsed.getScheme() == null) {
            String path = parsed.getPath();
            return path == null ? null : new File(path);
        }
        return null;
    }

    private void clearPendingMedia() {
        lastAudioPath = null;
        lastSpeechText = null;
        lastAudioDurationMillis = 0L;
        pendingVideoUri = null;
        pendingVideoThumbnailUri = null;
        pendingVideoDurationMillis = 0L;
    }

    private void showAnnotationDialog(@Nullable String videoUri, @Nullable String audioUri, @Nullable String thumbnailUri, long mediaDurationMillis) {
        double lat = latestPoint == null ? 0d : latestPoint.latitude;
        double lon = latestPoint == null ? 0d : latestPoint.longitude;
        float pitch = latestPoint == null ? 0f : latestPoint.pitch;
        float roll = latestPoint == null ? 0f : latestPoint.roll;
        float yaw = latestPoint == null ? 0f : latestPoint.yaw;
        AnnotationDialogFragment.newInstance(tripId, lat, lon, videoUri, thumbnailUri, audioUri,
                        lastSpeechText, pitch, roll, yaw, mediaDurationMillis)
                .show(getParentFragmentManager(), "annotation");
    }

    private String displayMediaUri(@Nullable String uri) {
        if (uri == null || uri.isEmpty()) {
            return "无";
        }
        if (uri.startsWith("file://")) {
            return Uri.parse(uri).getPath();
        }
        return uri;
    }

    private String safeMosaicStatus(@Nullable String status) {
        return status == null || status.isEmpty() ? AppConstants.MOSAIC_STATUS_NONE : status;
    }

    private void confirmMosaicVideo(AnnotationEntity annotation) {
        new AlertDialog.Builder(requireContext())
                .setTitle("视频人脸打码")
                .setMessage("将使用 YOLOv9-T 检测 person，并对人体框顶部区域做高斯模糊。完成后会用打码视频替换当前视频路径，并保留原视频路径。")
                .setPositiveButton("开始打码", (dialog, which) -> viewModel.mosaicAnnotationVideo(annotation.id))
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmFinish() {
        new AlertDialog.Builder(requireContext())
                .setTitle("结束行程？")
                .setMessage("时长：" + (trip == null ? "0秒" : TimeFormatUtils.duration(System.currentTimeMillis() - trip.startTime))
                        + "\n距离：" + (trip == null ? "0 m" : TimeFormatUtils.distance(trip.distanceMeters))
                        + "\n手动标记：" + (trip == null ? 0 : trip.annotationCount) + "处")
                .setPositiveButton("确认结束", (dialog, which) -> {
                    host.stopTrackingService();
                    viewModel.finishTrip(tripId);
                    host.openHome();
                })
                .setNeutralButton("不保存行程", (dialog, which) -> {
                    host.stopTrackingService();
                    viewModel.deleteTrip(tripId);
                    host.openHome();
                })
                .setNegativeButton("继续记录", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
        timerHandler.post(timerRunnable);
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        timerHandler.removeCallbacks(timerRunnable);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (recordingAudio) {
            stopAudioRecording(false, false);
        }
        timerHandler.removeCallbacks(audioTimeoutRunnable);
        recorderManager.release();
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroyView();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    public interface ActiveTripHost {
        void startTrackingService(long tripId);

        void stopTrackingService();

        void openHome();
    }
}
