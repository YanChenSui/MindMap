package com.example.mindmap.util;

import android.content.Context;
import android.util.Log;

import com.amap.api.maps.CoordinateConverter;
import com.amap.api.maps.model.LatLng;

/**
 * 高德地图使用 GCJ-02 坐标。数据库保留原始 GPS/WGS84，展示到高德地图前做显示坐标转换。
 */
public final class AmapCoordinateUtils {
    private static final String TAG = "AmapCoordinateUtils";

    private AmapCoordinateUtils() {}

    public static LatLng fromGps(Context context, double latitude, double longitude) {
        LatLng gps = new LatLng(latitude, longitude);
        try {
            CoordinateConverter converter = new CoordinateConverter(context.getApplicationContext());
            converter.from(CoordinateConverter.CoordType.GPS);
            converter.coord(gps);
            return converter.convert();
        } catch (Throwable throwable) {
            Log.w(TAG, "高德坐标转换失败，使用原始坐标显示", throwable);
            return gps;
        }
    }
}
