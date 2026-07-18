package com.example.mindmap.ui.tripdetail;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
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
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.ui.UiFactory;
import com.example.mindmap.ui.viewmodel.MoodMapViewModel;
import com.example.mindmap.util.AmapCoordinateUtils;
import com.example.mindmap.util.MapMarkerIconUtils;
import com.example.mindmap.util.TimeFormatUtils;
import com.example.mindmap.util.TrackMapUtils;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** Full-screen route map for a finished trip. */
public class TripMapFragment extends Fragment {
    private static final String ARG_TRIP_ID = "trip_id";

    private long tripId;
    private MoodMapViewModel viewModel;
    private MapView mapView;
    private AMap aMap;
    private TextView summaryText;
    private TripEntity trip;
    private List<TrackPointEntity> points = new ArrayList<>();
    private List<AnnotationEntity> annotations = new ArrayList<>();
    private boolean showAnnotations = true;
    private int drawGeneration;

    public static TripMapFragment newInstance(long tripId) {
        TripMapFragment fragment = new TripMapFragment();
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
        viewModel = ((MainActivity) requireActivity()).getSharedViewModel();

        LinearLayout page = new LinearLayout(requireContext());
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xffffffff);
        page.addView(createAppBar());

        FrameLayout mapContainer = new FrameLayout(requireContext());
        mapView = new MapView(requireContext());
        mapView.onCreate(savedInstanceState);
        aMap = mapView.getMap();
        configureMap();
        mapContainer.addView(mapView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mapContainer.addView(createSummaryOverlay(), overlayParams());
        page.addView(mapContainer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f));

        observe();
        return page;
    }

    private View createAppBar() {
        LinearLayout appBar = new LinearLayout(requireContext());
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(UiFactory.dp(requireContext(), 8), UiFactory.dp(requireContext(), 8),
                UiFactory.dp(requireContext(), 8), UiFactory.dp(requireContext(), 8));
        appBar.setBackgroundColor(0xffffffff);

        TextView back = UiFactory.text(requireContext(), "←", 30, Typeface.NORMAL);
        back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        appBar.addView(back, new LinearLayout.LayoutParams(UiFactory.dp(requireContext(), 44), UiFactory.dp(requireContext(), 48)));

        TextView title = UiFactory.cardTitle(requireContext(), "轨迹地图");
        appBar.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView menu = UiFactory.text(requireContext(), "⋮", 28, Typeface.BOLD);
        menu.setGravity(Gravity.CENTER);
        appBar.addView(menu, new LinearLayout.LayoutParams(UiFactory.dp(requireContext(), 44), UiFactory.dp(requireContext(), 48)));
        return appBar;
    }

    private View createSummaryOverlay() {
        CardView card = UiFactory.card(requireContext());
        card.setCardElevation(UiFactory.dp(requireContext(), 3));
        card.setUseCompatPadding(false);
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(UiFactory.dp(requireContext(), 10), UiFactory.dp(requireContext(), 7),
                UiFactory.dp(requireContext(), 10), UiFactory.dp(requireContext(), 7));
        summaryText = UiFactory.text(requireContext(), "定位记录 0个 · 手动标记 0处", 13, Typeface.NORMAL);
        body.addView(summaryText);
        body.addView(UiFactory.mapLegend(requireContext()));
        MaterialButton toggleAnnotationsButton = UiFactory.secondaryButton(requireContext(),
                showAnnotations ? "隐藏手动标记" : "显示手动标记");
        toggleAnnotationsButton.setOnClickListener(v -> {
            showAnnotations = !showAnnotations;
            toggleAnnotationsButton.setText(showAnnotations ? "隐藏手动标记" : "显示手动标记");
            scheduleDraw(false);
        });
        body.addView(toggleAnnotationsButton);
        card.addView(body);
        return card;
    }

    private FrameLayout.LayoutParams overlayParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        int margin = UiFactory.dp(requireContext(), 10);
        params.setMargins(margin, margin, margin, margin);
        return params;
    }

    private void configureMap() {
        aMap.getUiSettings().setZoomControlsEnabled(true);
        aMap.getUiSettings().setScaleControlsEnabled(true);
        aMap.getUiSettings().setZoomGesturesEnabled(true);
        aMap.getUiSettings().setScrollGesturesEnabled(true);
        aMap.getUiSettings().setRotateGesturesEnabled(true);
        aMap.getUiSettings().setTiltGesturesEnabled(true);
        aMap.setMapType(AMap.MAP_TYPE_NORMAL);
        aMap.setOnMapLoadedListener(() -> scheduleDraw(true));
    }

    private void observe() {
        viewModel.observeTrip(tripId).observe(getViewLifecycleOwner(), value -> {
            trip = value;
            updateSummary();
            scheduleDraw(false);
        });
        viewModel.observeTrackPoints(tripId).observe(getViewLifecycleOwner(), value -> {
            points = value == null ? new ArrayList<>() : value;
            updateSummary();
            scheduleDraw(true);
        });
        viewModel.observeAnnotations(tripId).observe(getViewLifecycleOwner(), value -> {
            annotations = value == null ? new ArrayList<>() : value;
            updateSummary();
            scheduleDraw(false);
        });
    }

    private void updateSummary() {
        if (summaryText == null) {
            return;
        }
        String distance = trip == null ? "0 m" : TimeFormatUtils.distance(trip.distanceMeters);
        String duration = trip == null ? "0秒" : TimeFormatUtils.duration(trip.durationMillis);
        summaryText.setText("定位记录 " + points.size() + "个 · 手动标记 " + annotations.size()
                + "处\n距离 " + distance + " · " + duration);
    }

    private void scheduleDraw(boolean fitBounds) {
        if (mapView == null) {
            return;
        }
        int generation = ++drawGeneration;
        mapView.post(() -> drawMap(fitBounds, generation));
        if (fitBounds) {
            mapView.postDelayed(() -> drawMap(true, generation), 250L);
            mapView.postDelayed(() -> drawMap(true, generation), 650L);
        }
    }

    private void drawMap(boolean fitBounds, int generation) {
        if (aMap == null) {
            return;
        }
        if (generation != drawGeneration) {
            return;
        }
        List<LatLng> route = TrackMapUtils.buildDisplayRoute(requireContext(), points);
        if (route.isEmpty() && (!showAnnotations || annotations.isEmpty())) {
            return;
        }
        aMap.clear();
        if (route.size() >= 2) {
            aMap.addPolyline(new PolylineOptions()
                    .addAll(route)
                    .width(11f)
                    .color(requireContext().getColor(R.color.park_green))
                    .zIndex(100f)
                    .geodesic(false));
            addEndpointMarker(route.get(0), "起点", BitmapDescriptorFactory.HUE_GREEN);
            addEndpointMarker(route.get(route.size() - 1), "终点", BitmapDescriptorFactory.HUE_RED);
        } else if (route.size() == 1) {
            addEndpointMarker(route.get(0), "定位记录", BitmapDescriptorFactory.HUE_GREEN);
        }

        if (showAnnotations) {
        for (int i = 0; i < annotations.size(); i++) {
            AnnotationEntity annotation = annotations.get(i);
            aMap.addMarker(new MarkerOptions()
                    .position(AmapCoordinateUtils.fromGps(requireContext(), annotation.latitude, annotation.longitude))
                    .title("第 " + (i + 1) + " 个手动标记")
                    .icon(MapMarkerIconUtils.numberedManualMark(requireContext(), i + 1)));
        }
        }

        if (fitBounds && route.size() > 1) {
            LatLngBounds.Builder builder = LatLngBounds.builder();
            for (LatLng latLng : route) {
                builder.include(latLng);
            }
            aMap.moveCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), UiFactory.dp(requireContext(), 72)));
        } else if (fitBounds && route.size() == 1) {
            aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(route.get(0), 17f));
        }
    }

    private void addEndpointMarker(LatLng position, String title, float hue) {
        aMap.addMarker(new MarkerOptions()
                .position(position)
                .title(title)
                .zIndex(120f)
                .icon(BitmapDescriptorFactory.defaultMarker(hue)));
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
            scheduleDraw(true);
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
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
}
