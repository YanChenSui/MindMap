package com.example.mindmap;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.example.mindmap.service.LocationTrackingService;
import com.example.mindmap.ui.activetrip.ActiveTripFragment;
import com.example.mindmap.ui.home.HomeFragment;
import com.example.mindmap.ui.tripdetail.TripDetailFragment;
import com.example.mindmap.ui.tripdetail.TripMapFragment;
import com.example.mindmap.ui.viewmodel.MoodMapViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;

/**
 * 单 Activity 宿主，负责权限申请、前台服务启动停止和 Fragment 导航。
 */
public class MainActivity extends AppCompatActivity implements HomeFragment.NavigationHost, ActiveTripFragment.ActiveTripHost, TripDetailFragment.DetailHost {
    private static final int REQUEST_CORE_PERMISSIONS = 10;
    private MoodMapViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        viewModel = new ViewModelProvider(this).get(MoodMapViewModel.class);
        viewModel.getMessage().observe(this, message -> {
            if (message != null) {
                Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_SHORT).show();
            }
        });
        if (savedInstanceState == null) {
            openHome();
        }
        requestCorePermissionsIfNeeded();
    }

    public MoodMapViewModel getSharedViewModel() {
        return viewModel;
    }

    @Override
    public void openHome() {
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        replace(new HomeFragment(), false);
    }

    @Override
    public void openActiveTrip(long tripId) {
        replace(ActiveTripFragment.newInstance(tripId), true);
    }

    @Override
    public void openTripDetail(long tripId) {
        replace(TripDetailFragment.newInstance(tripId), true);
    }

    @Override
    public void openTripMap(long tripId) {
        replace(TripMapFragment.newInstance(tripId), true);
    }

    @Override
    public void startTrackingService(long tripId) {
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_START);
        intent.putExtra(LocationTrackingService.EXTRA_TRIP_ID, tripId);
        ContextCompat.startForegroundService(this, intent);
    }

    @Override
    public void stopTrackingService() {
        Intent intent = new Intent(this, LocationTrackingService.class);
        intent.setAction(LocationTrackingService.ACTION_STOP);
        startService(intent);
    }

    private void replace(Fragment fragment, boolean addToBackStack) {
        androidx.fragment.app.FragmentTransaction transaction = getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment);
        if (addToBackStack) {
            transaction.addToBackStack(null);
        }
        transaction.commit();
    }

    private void requestCorePermissionsIfNeeded() {
        List<String> permissions = new ArrayList<>();
        addIfMissing(permissions, Manifest.permission.ACCESS_FINE_LOCATION);
        addIfMissing(permissions, Manifest.permission.ACCESS_COARSE_LOCATION);
        addIfMissing(permissions, Manifest.permission.CAMERA);
        addIfMissing(permissions, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= 33) {
            addIfMissing(permissions, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!permissions.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("权限说明")
                    .setMessage(getString(R.string.permission_required))
                    .setPositiveButton("继续授权", (dialog, which) -> ActivityCompat.requestPermissions(this, permissions.toArray(new String[0]), REQUEST_CORE_PERMISSIONS))
                    .setNegativeButton("稍后", null)
                    .show();
        }
    }

    private void addIfMissing(List<String> permissions, String permission) {
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(permission);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CORE_PERMISSIONS) {
            boolean denied = false;
            for (int result : grantResults) {
                denied |= result != PackageManager.PERMISSION_GRANTED;
            }
            if (denied) {
                Snackbar.make(findViewById(R.id.main), "部分权限被拒绝，定位、录音或视频功能可能不可用", Snackbar.LENGTH_LONG)
                        .setAction("设置", v -> {
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        }).show();
            } else {
                Toast.makeText(this, "权限已授权", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
