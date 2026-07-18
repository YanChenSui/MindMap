package com.example.mindmap.ui.home;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;

import com.example.mindmap.MainActivity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.ui.UiFactory;
import com.example.mindmap.ui.viewmodel.MoodMapViewModel;
import com.example.mindmap.util.TimeFormatUtils;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** 首页展示应用入口、行程创建和历史行程列表。 */
public class HomeFragment extends Fragment {
    private MoodMapViewModel viewModel;
    private LinearLayout tripList;
    private NavigationHost host;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        host = (NavigationHost) requireActivity();
        viewModel = ((MainActivity) requireActivity()).getSharedViewModel();

        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setBackgroundColor(0xfff4faf4);
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiFactory.dp(requireContext(), UiFactory.PAGE_HORIZONTAL_PADDING_DP),
                UiFactory.dp(requireContext(), 24),
                UiFactory.dp(requireContext(), UiFactory.PAGE_HORIZONTAL_PADDING_DP),
                UiFactory.dp(requireContext(), 24));
        scrollView.addView(root);

        root.addView(createHeroCard());
        root.addView(UiFactory.sectionTitle(requireContext(), "我的行程"));
        tripList = new LinearLayout(requireContext());
        tripList.setOrientation(LinearLayout.VERTICAL);
        root.addView(tripList);
        observeTrips();
        return scrollView;
    }

    private View createHeroCard() {
        CardView card = UiFactory.card(requireContext());
        LinearLayout body = new LinearLayout(requireContext());
        body.setOrientation(LinearLayout.VERTICAL);
        body.addView(UiFactory.title(requireContext(), "心境地图"));
        body.addView(UiFactory.body(requireContext(), "用轨迹、视频、语音和情绪评分记录一次真实的公园景观体验。"));
        MaterialButton startButton = UiFactory.primaryButton(requireContext(), "开始漫步");
        startButton.setOnClickListener(v -> showCreateTripDialog());
        body.addView(startButton);
        card.addView(body);
        return card;
    }

    private void observeTrips() {
        tripList.addView(UiFactory.stateCard(requireContext(), "正在加载", "正在读取本地行程记录"));
        viewModel.observeTrips().observe(getViewLifecycleOwner(), this::renderTrips);
        viewModel.getActiveTripId().observe(getViewLifecycleOwner(), tripId -> {
            if (tripId != null && tripId > 0) {
                host.startTrackingService(tripId);
                host.openActiveTrip(tripId);
            }
        });
    }

    private void renderTrips(List<TripEntity> trips) {
        tripList.removeAllViews();
        if (trips == null || trips.isEmpty()) {
            tripList.addView(UiFactory.stateCard(requireContext(), "暂无行程", "点击“开始漫步”创建第一次公园体验记录。"));
            return;
        }
        for (TripEntity trip : trips) {
            tripList.addView(createTripCard(trip));
        }
    }

    private View createTripCard(TripEntity trip) {
        CardView card = UiFactory.card(requireContext());
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(UiFactory.cardTitle(requireContext(), trip.name));
        content.addView(UiFactory.mutedText(requireContext(), TimeFormatUtils.readableDate(trip.startTime)));
        content.addView(UiFactory.body(requireContext(),
                "时长 " + TimeFormatUtils.duration(trip.durationMillis)
                        + "   距离 " + TimeFormatUtils.distance(trip.distanceMeters)
                        + "   手动标记 " + trip.annotationCount + " 处"));
        content.addView(UiFactory.mutedText(requireContext(), TimeFormatUtils.tripStatus(trip.status) + " · " + TimeFormatUtils.recordMode(trip.recordMode)));
        card.addView(content);
        card.setOnClickListener(v -> {
            if ("ACTIVE".equals(trip.status)) {
                host.openActiveTrip(trip.id);
            } else {
                host.openTripDetail(trip.id);
            }
        });
        return card;
    }

    private void showCreateTripDialog() {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(UiFactory.dp(requireContext(), 20), 0, UiFactory.dp(requireContext(), 20), 0);
        EditText name = new EditText(requireContext());
        name.setHint("行程名称（可空）");
        EditText destination = new EditText(requireContext());
        destination.setHint("目的地（可选）");
        CheckBox auto = new CheckBox(requireContext());
        auto.setText("自动连续记录");
        auto.setChecked(true);
        form.addView(name);
        form.addView(destination);
        form.addView(auto);
        new AlertDialog.Builder(requireContext())
                .setTitle("开始行程")
                .setView(form)
                .setPositiveButton("开始行程", (dialog, which) -> viewModel.startTrip(name.getText().toString(), destination.getText().toString(), auto.isChecked()))
                .setNegativeButton("取消", null)
                .show();
    }

    public interface NavigationHost {
        void openActiveTrip(long tripId);
        void openTripDetail(long tripId);
        void startTrackingService(long tripId);
    }
}
