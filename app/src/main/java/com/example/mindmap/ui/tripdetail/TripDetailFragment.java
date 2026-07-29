package com.example.mindmap.ui.tripdetail;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.LatLngBounds;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.PolylineOptions;
import com.example.mindmap.MainActivity;
import com.example.mindmap.R;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.export.ExportManager;
import com.example.mindmap.export.MoodStats;
import com.example.mindmap.ui.UiFactory;
import com.example.mindmap.ui.tripdetail.section.RosAnnotationSection;
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

/** 行程详情页：顶部导航、概览指标、轨迹概览、情绪统计和标注时间线。 */
public class TripDetailFragment extends Fragment {
    private static final String ARG_TRIP_ID = "trip_id";

    private long tripId;
    private MoodMapViewModel viewModel;
    private DetailHost host;
    private LinearLayout content;
    private MapView mapView;
    private AMap aMap;
    private MoodBarChartView chartView;
    private TripEntity trip;
    private List<TrackPointEntity> points = new ArrayList<>();
    private List<AnnotationEntity> annotations = new ArrayList<>();
    private List<RosPredictionEntity> rosPredictions = new ArrayList<>();
    private MediaPlayer mediaPlayer;
    private boolean previewCameraFitted;
    private boolean showPreviewAnnotations = true;
    private int previewDrawGeneration;

    public static TripDetailFragment newInstance(long tripId) {
        TripDetailFragment fragment = new TripDetailFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_TRIP_ID, tripId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tripId = requireArguments().getLong(ARG_TRIP_ID);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        host = (DetailHost) requireActivity();
        viewModel = ((MainActivity) requireActivity()).getSharedViewModel();

        LinearLayout page = new LinearLayout(requireContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xfff4faf4);
        page.addView(createAppBar());

        ScrollView scroll = new ScrollView(requireContext());
        content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(UiFactory.dp(requireContext(), UiFactory.PAGE_HORIZONTAL_PADDING_DP),
                UiFactory.dp(requireContext(), 8),
                UiFactory.dp(requireContext(), UiFactory.PAGE_HORIZONTAL_PADDING_DP),
                UiFactory.dp(requireContext(), 24));
        scroll.addView(content);
        page.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        chartView = new MoodBarChartView(requireContext());

        showLoadingState();
        observe();
        return page;
    }

    private View createAppBar() {
        LinearLayout appBar = new LinearLayout(requireContext());
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(UiFactory.dp(requireContext(), 8), UiFactory.dp(requireContext(), 8), UiFactory.dp(requireContext(), 8), UiFactory.dp(requireContext(), 8));
        appBar.setBackgroundColor(0xffffffff);

        TextView back = UiFactory.text(requireContext(), "‹", 32, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        appBar.addView(back, new LinearLayout.LayoutParams(UiFactory.dp(requireContext(), 42), UiFactory.dp(requireContext(), 48)));

        TextView title = UiFactory.sectionTitle(requireContext(), "行程详情");
        appBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView menu = UiFactory.text(requireContext(), "⋮", 28, Typeface.BOLD);
        menu.setGravity(Gravity.CENTER);
        menu.setOnClickListener(this::showOverflowMenu);
        appBar.addView(menu, new LinearLayout.LayoutParams(UiFactory.dp(requireContext(), 42), UiFactory.dp(requireContext(), 48)));
        return appBar;
    }

    private void showOverflowMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.getMenu().add("重命名");
        popupMenu.getMenu().add("删除行程");
        popupMenu.setOnMenuItemClickListener(this::handleMenuClick);
        popupMenu.show();
    }

    private boolean handleMenuClick(MenuItem item) {
        String title = String.valueOf(item.getTitle());
        if ("重命名".equals(title)) {
            showRenameDialog();
        } else if ("删除行程".equals(title)) {
            confirmDelete();
        }
        return true;
    }

    private void configureAmap() {
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.getUiSettings().setZoomGesturesEnabled(true);
        aMap.getUiSettings().setScrollGesturesEnabled(true);
        aMap.getUiSettings().setRotateGesturesEnabled(true);
        aMap.getUiSettings().setTiltGesturesEnabled(true);
        aMap.setMapType(AMap.MAP_TYPE_NORMAL);
        aMap.moveCamera(CameraUpdateFactory.zoomTo(15f));
        aMap.setOnMapLoadedListener(() -> schedulePreviewRedraw(true));
        aMap.setOnMapTouchListener(event -> {
            if (mapView != null) {
                ViewParentDisallowIntercept(mapView, event);
            }
        });
    }

    private void observe() {
        viewModel.observeTrip(tripId).observe(getViewLifecycleOwner(), value -> {
            trip = value;
            render();
            schedulePreviewRedraw(true);
        });
        viewModel.observeTrackPoints(tripId).observe(getViewLifecycleOwner(), value -> {
            points = value == null ? new ArrayList<>() : value;
            previewCameraFitted = false;
            render();
            schedulePreviewRedraw(true);
        });
        viewModel.observeAnnotations(tripId).observe(getViewLifecycleOwner(), value -> {
            annotations = value == null ? new ArrayList<>() : value;
            chartView.setAnnotations(annotations);
            previewCameraFitted = false;
            render();
            schedulePreviewRedraw(true);
        });
        viewModel.observeRosPredictionsByTrip(tripId).observe(getViewLifecycleOwner(), value -> {
            rosPredictions = value == null ? new ArrayList<>() : value;
            render();
        });
    }

    private void showLoadingState() {
        content.removeAllViews();
        content.addView(UiFactory.stateCard(requireContext(), "正在加载", "正在读取行程详情"));
    }

    private void render() {
        if (content == null || trip == null) {
            return;
        }
        content.removeAllViews();
        content.addView(createOverviewCard());
        content.addView(createMapCard());
        content.addView(createExportCard());
        content.addView(createStatsCard());
        content.addView(UiFactory.sectionTitle(requireContext(), "手动标记时间线"));
        if (annotations.isEmpty()) {
            content.addView(UiFactory.stateCard(requireContext(), "暂无手动标记", "本次行程还没有保存情绪标记"));
        } else {
            for (AnnotationEntity annotation : annotations) {
                content.addView(createAnnotationCard(annotation));
            }
        }
    }

    private View createOverviewCard() {
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(UiFactory.title(requireContext(), trip.name), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView status = UiFactory.cardTitle(requireContext(), TimeFormatUtils.tripStatus(trip.status));
        status.setTextColor(requireContext().getColor(R.color.park_green));
        titleRow.addView(status);
        body.addView(titleRow);

        body.addView(UiFactory.body(requireContext(), TimeFormatUtils.dateOnly(trip.startTime)));
        body.addView(UiFactory.mutedText(requireContext(), TimeFormatUtils.timeRange(trip.startTime, trip.endTime)));
        body.addView(createMetricRow());
        body.addView(UiFactory.mutedText(requireContext(), "记录模式：" + TimeFormatUtils.recordMode(trip.recordMode)));
        card.addView(body);
        return card;
    }

    private View createMetricRow() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        addMetric(row, TimeFormatUtils.duration(trip.durationMillis), "时长");
        addMetric(row, TimeFormatUtils.distance(trip.distanceMeters), "距离");
        addMetric(row, TimeFormatUtils.averageSpeedKmh(trip.distanceMeters, trip.durationMillis), "平均速度");
        return row;
    }

    private void addMetric(LinearLayout row, String value, String label) {
        LinearLayout block = new LinearLayout(requireContext());
        block.setOrientation(LinearLayout.VERTICAL);
        block.setGravity(Gravity.CENTER);
        block.addView(UiFactory.cardTitle(requireContext(), value));
        block.addView(UiFactory.mutedText(requireContext(), label));
        row.addView(block, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }

    private View createMapCard() {
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(UiFactory.cardTitle(requireContext(), "轨迹概览"));
        if (points.isEmpty()) {
            body.addView(UiFactory.stateCard(requireContext(), "暂无轨迹", "未采集到有效定位点，地图区域已折叠为空状态"));
        } else {
            recreatePreviewMapView();
            detachFromParent(mapView);
            allowMapGesturesInsideScroll(mapView);
            body.addView(mapView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 240)));
            schedulePreviewRedraw(false);
            body.addView(UiFactory.mutedText(requireContext(), "定位记录：" + points.size() + "个    手动标记：" + trip.annotationCount + "处    行程距离：" + TimeFormatUtils.distance(trip.distanceMeters)));
            body.addView(UiFactory.mapLegend(requireContext()));
            MaterialButton toggleAnnotationsButton = UiFactory.secondaryButton(requireContext(),
                    showPreviewAnnotations ? "隐藏手动标记" : "显示手动标记");
            toggleAnnotationsButton.setOnClickListener(v -> {
                showPreviewAnnotations = !showPreviewAnnotations;
                toggleAnnotationsButton.setText(showPreviewAnnotations ? "隐藏手动标记" : "显示手动标记");
                schedulePreviewRedraw(false);
            });
            body.addView(toggleAnnotationsButton);
            MaterialButton fullMapButton = UiFactory.secondaryButton(requireContext(), "查看完整地图 >");
            fullMapButton.setOnClickListener(v -> Snackbar.make(requireView(), "当前 MVP 在详情页展示完整轨迹概览", Snackbar.LENGTH_SHORT).show());
            fullMapButton.setOnClickListener(v -> host.openTripMap(tripId));
            body.addView(fullMapButton);
        }
        card.addView(body);
        return card;
    }

    private void recreatePreviewMapView() {
        if (mapView != null && aMap != null) {
            return;
        }
        mapView = new MapView(requireContext());
        mapView.onCreate(null);
        mapView.onResume();
        aMap = mapView.getMap();
        configureAmap();
    }

    private void destroyPreviewMapView() {
        if (mapView == null) {
            return;
        }
        try {
            detachFromParent(mapView);
            mapView.onPause();
            mapView.onDestroy();
        } catch (Throwable ignored) {
            // Best-effort cleanup before creating the next preview map instance.
        } finally {
            mapView = null;
            aMap = null;
            previewCameraFitted = false;
            previewDrawGeneration++;
        }
    }

    private View createStatsCard() {
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        MoodStats stats = MoodStats.from(annotations);
        body.addView(UiFactory.cardTitle(requireContext(), "情绪统计"));
        if (annotations.isEmpty()) {
            body.addView(UiFactory.stateCard(requireContext(), "暂无情绪数据", "保存标注后会展示四个维度的平均分"));
        } else {
            if (stats.count == 1) {
                AnnotationEntity annotation = annotations.get(0);
                body.addView(UiFactory.mutedText(requireContext(), "本次评价"));
                for (int i = 0; i < MoodStats.ROS_LABELS.length; i++) {
                    body.addView(UiFactory.mutedText(requireContext(), MoodStats.ROS_LABELS[i]
                            + "    " + String.format(java.util.Locale.CHINA, "%.1f", (float) rosScore(annotation, i))));
                }
            } else {
                body.addView(UiFactory.mutedText(requireContext(), "样本数：" + stats.count));
                for (int i = 0; i < MoodStats.ROS_LABELS.length; i++) {
                    body.addView(UiFactory.mutedText(requireContext(), MoodStats.ROS_LABELS[i]
                            + "    平均 " + stats.averages[i]
                            + "    范围 " + stats.minScores[i] + "-" + stats.maxScores[i]));
                }
            }
            detachFromParent(chartView);
            body.addView(chartView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 320)));
        }
        card.addView(body);
        return card;
    }

    private int rosScore(AnnotationEntity annotation, int index) {
        switch (index) {
            case 0:
                return annotation.visualPreferenceScore;
            case 1:
                return annotation.thoughtClarityScore;
            case 2:
                return annotation.worryForgetScore;
            case 3:
                return annotation.restoredRelaxedScore;
            case 4:
                return annotation.rosCalmScore;
            case 5:
                return annotation.interestScore;
            case 6:
                return annotation.focusedAlertScore;
            default:
                return 0;
        }
    }

    private View createExportCard() {
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(UiFactory.cardTitle(requireContext(), "导出数据"));
        body.addView(UiFactory.mutedText(requireContext(), "选择格式后直接生成并分享文件"));
        addExportButton(body, "导出 JSON", "JSON");
        addExportButton(body, "导出 CSV", "CSV");
        addExportButton(body, "导出 HTML", "HTML");
        card.addView(body);
        return card;
    }

    private void addExportButton(LinearLayout body, String text, String label) {
        MaterialButton button = UiFactory.secondaryButton(requireContext(), text);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(UiFactory.dp(requireContext(), 44));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setOnClickListener(v -> export(label));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, UiFactory.dp(requireContext(), 8), 0, 0);
        body.addView(button, params);
    }

    private View createAnnotationCard(AnnotationEntity annotation) {
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(UiFactory.cardTitle(requireContext(), TimeFormatUtils.readableDate(annotation.timestamp) + "  ROS均分 " + annotation.rosAverageScore));
        body.addView(UiFactory.mutedText(requireContext(), "位置：" + TimeFormatUtils.coordinate(annotation.latitude) + ", " + TimeFormatUtils.coordinate(annotation.longitude)));
        RosAnnotationSection.addTo(requireContext(), body, annotation, predictionsFor(annotation.id));
        View thumbnail = createThumbnailView(annotation.videoThumbnailUri);
        if (thumbnail != null) {
            body.addView(thumbnail);
        }
        body.addView(UiFactory.mutedText(requireContext(), "视频地址：" + displayMediaUri(annotation.videoUri)));
        body.addView(UiFactory.mutedText(requireContext(), "原视频备份：" + displayMediaUri(annotation.originalVideoUri)));
        body.addView(UiFactory.mutedText(requireContext(), "打码视频：" + displayMediaUri(annotation.blurredVideoUri)));
        body.addView(UiFactory.mutedText(requireContext(), "打码状态：" + safe(annotation.videoMosaicStatus)));
        if (AppConstants.MOSAIC_STATUS_PROCESSING.equals(annotation.videoMosaicStatus)) {
            body.addView(createMosaicProgressBar());
            body.addView(UiFactory.mutedText(requireContext(), "正在处理视频，请保持应用打开"));
        }
        body.addView(UiFactory.mutedText(requireContext(), "缩略图：" + displayMediaUri(annotation.videoThumbnailUri)));
        body.addView(UiFactory.mutedText(requireContext(), "媒体时长：" + TimeFormatUtils.duration(annotation.durationMillis)));
        body.addView(UiFactory.mutedText(requireContext(), "拍摄角度：Pitch " + annotation.cameraPitch
                + "° / Roll " + annotation.cameraRoll + "° / Yaw " + annotation.cameraYaw + "°"));
        body.addView(UiFactory.mutedText(requireContext(), "文字：" + safe(annotation.textNote)));
        body.addView(UiFactory.mutedText(requireContext(), "转写：" + safe(annotation.speechText)));
        LinearLayout buttons = new LinearLayout(requireContext());
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        if (annotation.videoUri != null && !annotation.videoUri.isEmpty()) {
            MaterialButton playVideo = UiFactory.secondaryButton(requireContext(), "播放视频");
            playVideo.setOnClickListener(v -> openMedia(annotation.videoUri, "video/*"));
            buttons.addView(playVideo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            MaterialButton mosaicVideo = UiFactory.secondaryButton(requireContext(), "高斯模糊打码");
            mosaicVideo.setEnabled(!AppConstants.MOSAIC_STATUS_PROCESSING.equals(annotation.videoMosaicStatus));
            mosaicVideo.setOnClickListener(v -> confirmMosaicVideo(annotation));
            buttons.addView(mosaicVideo, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (annotation.audioUri != null && !annotation.audioUri.isEmpty()) {
            MaterialButton playAudio = UiFactory.secondaryButton(requireContext(), "播放音频");
            playAudio.setOnClickListener(v -> playAudio(annotation.audioUri));
            buttons.addView(playAudio, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        if (buttons.getChildCount() > 0) {
            body.addView(buttons);
        }
        card.addView(body);
        return card;
    }

    private List<RosPredictionEntity> predictionsFor(long annotationId) {
        List<RosPredictionEntity> matches = new ArrayList<>();
        for (RosPredictionEntity prediction : rosPredictions) {
            if (prediction.annotationId == annotationId) {
                matches.add(prediction);
            }
        }
        return matches;
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

    @Nullable
    private View createThumbnailView(@Nullable String thumbnailUri) {
        if (thumbnailUri == null || thumbnailUri.isEmpty()) {
            return null;
        }
        try {
            ImageView imageView = new ImageView(requireContext());
            imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            imageView.setImageURI(toReadableMediaUri(thumbnailUri));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiFactory.dp(requireContext(), 150));
            params.setMargins(0, UiFactory.dp(requireContext(), 8), 0, UiFactory.dp(requireContext(), 8));
            imageView.setLayoutParams(params);
            return imageView;
        } catch (Throwable throwable) {
            return null;
        }
    }

    private void detachFromParent(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) {
            ((ViewGroup) view.getParent()).removeView(view);
        }
    }

    private void allowMapGesturesInsideScroll(MapView targetMapView) {
        installMapTouchGuard(targetMapView);
        targetMapView.post(() -> installMapTouchGuard(targetMapView));
    }

    private void installMapTouchGuard(View view) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnTouchListener((touchedView, event) -> {
            ViewParentDisallowIntercept(touchedView, event);
            return false;
        });
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                installMapTouchGuard(group.getChildAt(i));
            }
        }
    }

    private void ViewParentDisallowIntercept(View view, MotionEvent event) {
        boolean keepTouchForMap = event.getActionMasked() != MotionEvent.ACTION_UP
                && event.getActionMasked() != MotionEvent.ACTION_CANCEL;
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(keepTouchForMap);
            parent = parent.getParent();
        }
    }

    private void redrawMap(int generation) {
        if (aMap == null) {
            return;
        }
        if (generation != previewDrawGeneration) {
            return;
        }
        List<LatLng> latLngs = TrackMapUtils.buildDisplayRoute(requireContext(), points);
        if (latLngs.isEmpty() && (!showPreviewAnnotations || annotations.isEmpty())) {
            return;
        }
        aMap.clear();
        if (latLngs.size() >= 2) {
            aMap.addPolyline(new PolylineOptions()
                    .addAll(latLngs)
                    .width(8f)
                    .color(requireContext().getColor(R.color.park_green))
                    .geodesic(false));
            aMap.addMarker(new MarkerOptions()
                    .position(latLngs.get(0))
                    .title("起点")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            aMap.addMarker(new MarkerOptions()
                    .position(latLngs.get(latLngs.size() - 1))
                    .title("终点")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
        }
        if (showPreviewAnnotations) {
        for (int i = 0; i < annotations.size(); i++) {
            AnnotationEntity annotation = annotations.get(i);
            aMap.addMarker(new MarkerOptions()
                    .position(AmapCoordinateUtils.fromGps(requireContext(), annotation.latitude, annotation.longitude))
                    .title("第 " + (i + 1) + " 个手动标记")
                    .snippet(safe(annotation.textNote))
                    .icon(MapMarkerIconUtils.numberedManualMark(requireContext(), i + 1)));
        }
        }
        fitPreviewRoute(false);
    }

    private void schedulePreviewRedraw(boolean fitBounds) {
        if (mapView == null || aMap == null || points.isEmpty()) {
            return;
        }
        int generation = ++previewDrawGeneration;
        if (fitBounds) {
            previewCameraFitted = false;
        }
        mapView.post(() -> runPreviewDraw(generation, fitBounds));
        mapView.postDelayed(() -> runPreviewDraw(generation, fitBounds), 250L);
        mapView.postDelayed(() -> runPreviewDraw(generation, fitBounds), 650L);
    }

    private void runPreviewDraw(int generation, boolean fitBounds) {
        if (generation != previewDrawGeneration) {
            return;
        }
        redrawMap(generation);
        if (fitBounds) {
            fitPreviewRoute(true);
        }
    }

    private void fitPreviewRoute(boolean force) {
        if (aMap == null || mapView == null || points.isEmpty()) {
            return;
        }
        if (previewCameraFitted && !force) {
            return;
        }
        List<LatLng> latLngs = TrackMapUtils.buildDisplayRoute(requireContext(), points);
        if (latLngs.isEmpty()) {
            return;
        }
        Runnable fitAction = () -> {
            List<LatLng> boundsPoints = new ArrayList<>(latLngs);
            for (AnnotationEntity annotation : annotations) {
                boundsPoints.add(AmapCoordinateUtils.fromGps(requireContext(), annotation.latitude, annotation.longitude));
            }
            if (boundsPoints.size() == 1) {
                aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(boundsPoints.get(0), 18f));
                previewCameraFitted = true;
                return;
            }
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (LatLng latLng : expandTinyBounds(boundsPoints)) {
                builder.include(latLng);
            }
            aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(
                    builder.build(),
                    mapView.getWidth(),
                    mapView.getHeight(),
                    UiFactory.dp(requireContext(), 32)));
            previewCameraFitted = true;
        };
        if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
            fitAction.run();
        } else {
            mapView.post(fitAction);
        }
    }

    private List<LatLng> expandTinyBounds(List<LatLng> boundsPoints) {
        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;
        for (LatLng latLng : boundsPoints) {
            minLat = Math.min(minLat, latLng.latitude);
            maxLat = Math.max(maxLat, latLng.latitude);
            minLon = Math.min(minLon, latLng.longitude);
            maxLon = Math.max(maxLon, latLng.longitude);
        }
        double centerLat = (minLat + maxLat) / 2d;
        double centerLon = (minLon + maxLon) / 2d;
        double latMeters = Math.max(1d, (maxLat - minLat) * 110_540d);
        double lonMeters = Math.max(1d, (maxLon - minLon) * 111_320d * Math.cos(Math.toRadians(centerLat)));
        double minSpanMeters = 70d;
        if (latMeters >= minSpanMeters && lonMeters >= minSpanMeters) {
            return boundsPoints;
        }
        double halfLatDelta = Math.max(maxLat - minLat, minSpanMeters / 110_540d) / 2d;
        double lonMetersPerDegree = Math.max(1d, 111_320d * Math.cos(Math.toRadians(centerLat)));
        double halfLonDelta = Math.max(maxLon - minLon, minSpanMeters / lonMetersPerDegree) / 2d;
        List<LatLng> expanded = new ArrayList<>(boundsPoints);
        expanded.add(new LatLng(centerLat - halfLatDelta, centerLon - halfLonDelta));
        expanded.add(new LatLng(centerLat + halfLatDelta, centerLon + halfLonDelta));
        return expanded;
    }

    private void showFullMapDialog() {
        if (points.isEmpty()) {
            Snackbar.make(requireView(), "暂无定位记录", Snackbar.LENGTH_SHORT).show();
            return;
        }
        MapView fullMapView = new MapView(requireContext());
        fullMapView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                UiFactory.dp(requireContext(), 520)));
        fullMapView.onCreate(null);
        fullMapView.onResume();
        AMap fullMap = fullMapView.getMap();
        fullMap.getUiSettings().setZoomControlsEnabled(true);
        fullMap.getUiSettings().setScaleControlsEnabled(true);
        fullMap.getUiSettings().setZoomGesturesEnabled(true);
        fullMap.getUiSettings().setScrollGesturesEnabled(true);
        fullMap.getUiSettings().setRotateGesturesEnabled(true);
        fullMap.getUiSettings().setTiltGesturesEnabled(true);
        fullMap.setMapType(AMap.MAP_TYPE_NORMAL);
        fullMap.setOnMapLoadedListener(() -> drawFullMap(fullMap, true));
        allowMapGesturesInsideScroll(fullMapView);

        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle("完整地图")
                .setView(fullMapView)
                .setPositiveButton("关闭", null)
                .create();
        dialog.setOnShowListener(d -> {
            fullMapView.post(() -> drawFullMap(fullMap, true));
            fullMapView.postDelayed(() -> drawFullMap(fullMap, true), 200L);
            fullMapView.postDelayed(() -> drawFullMap(fullMap, true), 600L);
        });
        dialog.setOnDismissListener(d -> {
            fullMapView.onPause();
            fullMapView.onDestroy();
        });
        dialog.show();
    }

    private void drawFullMap(AMap targetMap, boolean fitBounds) {
        targetMap.clear();
        List<LatLng> latLngs = TrackMapUtils.buildDisplayRoute(requireContext(), points);
        if (latLngs.size() >= 2) {
            targetMap.addPolyline(new PolylineOptions()
                    .addAll(latLngs)
                    .width(14f)
                    .color(0xff1b7f3a)
                    .zIndex(100f)
                    .geodesic(false)
                    .visible(true));
        }
        for (int i = 0; i < annotations.size(); i++) {
            AnnotationEntity annotation = annotations.get(i);
            targetMap.addMarker(new MarkerOptions()
                    .position(AmapCoordinateUtils.fromGps(requireContext(), annotation.latitude, annotation.longitude))
                    .title("第 " + (i + 1) + " 个手动标记")
                    .snippet(safe(annotation.textNote))
                    .icon(MapMarkerIconUtils.numberedManualMark(requireContext(), i + 1)));
        }
        if (latLngs.size() > 1 && fitBounds) {
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (LatLng latLng : latLngs) {
                builder.include(latLng);
            }
            targetMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), UiFactory.dp(requireContext(), 48)));
        } else if (!latLngs.isEmpty()) {
            targetMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.get(0), 17f));
        }
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

    private void showRenameDialog() {
        EditText input = new EditText(requireContext());
        input.setText(trip == null ? "" : trip.name);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(requireContext())
                .setTitle("重命名行程")
                .setView(input)
                .setPositiveButton("保存", (dialog, which) -> viewModel.renameTrip(tripId, input.getText().toString()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void export(String label) {
        viewModel.getRepository().loadTripBundle(tripId, new com.example.mindmap.data.repository.MoodMapRepository.TripBundleCallback() {
            @Override
            public void onLoaded(TripEntity loadedTrip, List<TrackPointEntity> loadedPoints, List<AnnotationEntity> loadedAnnotations, List<RosPredictionEntity> loadedPredictions) {
                try {
                    ExportManager manager = new ExportManager();
                    File dir = viewModel.getRepository().getExportDir();
                    File file;
                    String mime;
                    if ("JSON".equals(label)) {
                        file = manager.exportJson(dir, loadedTrip, loadedPoints, loadedAnnotations, loadedPredictions);
                        mime = "application/json";
                    } else if ("CSV".equals(label)) {
                        file = manager.exportCsv(dir, loadedTrip, loadedAnnotations);
                        mime = "text/csv";
                    } else {
                        file = manager.exportHtml(dir, loadedTrip, loadedAnnotations);
                        mime = "text/html";
                    }
                    Intent share = manager.buildShareIntent(requireContext(), file, mime);
                    requireActivity().runOnUiThread(() -> startActivity(share));
                } catch (Throwable throwable) {
                    requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), "导出失败，请检查存储空间", Snackbar.LENGTH_LONG).show());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                requireActivity().runOnUiThread(() -> Snackbar.make(requireView(), "导出失败", Snackbar.LENGTH_LONG).show());
            }
        });
    }

    private void openMedia(String uri, String type) {
        try {
            Uri mediaUri = toReadableMediaUri(uri);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(mediaUri, type);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(intent);
        } catch (ActivityNotFoundException throwable) {
            Snackbar.make(requireView(), "没有可用的媒体播放器", Snackbar.LENGTH_LONG).show();
        } catch (Throwable throwable) {
            Snackbar.make(requireView(), "无法打开媒体文件", Snackbar.LENGTH_LONG).show();
        }
    }

    private void confirmMosaicVideo(AnnotationEntity annotation) {
        new AlertDialog.Builder(requireContext())
                .setTitle("视频人脸打码")
                .setMessage("将使用 YOLOv9-T 检测 person，并对人体框顶部区域做高斯模糊。完成后会用打码视频替换当前视频路径，并保留原视频路径。")
                .setPositiveButton("开始打码", (dialog, which) -> viewModel.mosaicAnnotationVideo(annotation.id))
                .setNegativeButton("取消", null)
                .show();
    }

    private void playAudio(String uri) {
        try {
            releasePlayer();
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(requireContext(), toReadableMediaUri(uri));
            Snackbar.make(requireView(), "正在准备音频", Snackbar.LENGTH_SHORT).show();
            if (mediaPlayer == null) {
                Snackbar.make(requireView(), "音频文件不存在或无法播放", Snackbar.LENGTH_LONG).show();
                return;
            }
            mediaPlayer.setOnCompletionListener(mp -> releasePlayer());
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                releasePlayer();
                Snackbar.make(requireView(), "音频播放失败", Snackbar.LENGTH_LONG).show();
                return true;
            });
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                Snackbar.make(requireView(), "正在播放音频", Snackbar.LENGTH_SHORT).show();
            });
            mediaPlayer.prepareAsync();
        } catch (Throwable throwable) {
            releasePlayer();
            Snackbar.make(requireView(), "音频播放失败", Snackbar.LENGTH_LONG).show();
        }
    }

    private Uri toReadableMediaUri(String value) {
        Uri uri = Uri.parse(value);
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            return uri;
        }
        File file;
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            file = new File(uri.getPath());
        } else {
            file = new File(value);
        }
        if (!file.exists()) {
            throw new IllegalArgumentException("Media file does not exist: " + file);
        }
        return FileProvider.getUriForFile(requireContext(), requireContext().getPackageName() + ".fileprovider", file);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(requireContext())
                .setTitle("删除行程？")
                .setMessage("数据库记录会被删除，应用专属目录中的媒体文件请按需手动清理。")
                .setPositiveButton("删除", (dialog, which) -> {
                    viewModel.deleteTrip(tripId);
                    host.openHome();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String safe(String value) {
        return value == null || value.isEmpty() ? "无" : value;
    }

    private String displayMediaUri(String uri) {
        if (uri == null || uri.isEmpty()) {
            return "无";
        }
        return uri.startsWith("file://") ? uri.substring("file://".length()) : uri;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView == null && trip != null && content != null) {
            render();
        } else if (mapView != null) {
            mapView.onResume();
            schedulePreviewRedraw(true);
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        releasePlayer();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        releasePlayer();
        destroyPreviewMapView();
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

    private void releasePlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    public interface DetailHost {
        void openHome();
        void openTripMap(long tripId);
    }
}
