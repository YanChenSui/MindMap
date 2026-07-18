package com.example.mindmap.util;

import android.content.Context;

import com.amap.api.maps.model.LatLng;
import com.example.mindmap.data.local.entity.TrackPointEntity;

import java.util.ArrayList;
import java.util.List;

/** Builds a cleaner display route from raw GPS points without changing stored data. */
public final class TrackMapUtils {
    private static final float DISPLAY_MAX_ACCURACY_METERS = 30f;
    private static final double MIN_DISPLAY_STEP_METERS = 2.0d;
    private static final double STILL_JITTER_METERS = 8.0d;
    private static final double MAX_WALKING_DISPLAY_SPEED_MPS = 4.5d;
    private static final double MAX_SHORT_JUMP_METERS = 35.0d;
    private static final long SHORT_JUMP_WINDOW_MS = 8_000L;
    private static final double TURN_KEEP_ANGLE_DEGREES = 42.0d;
    private static final double SIMPLIFY_TOLERANCE_METERS = 1.4d;

    private TrackMapUtils() {
    }

    public static List<LatLng> buildDisplayRoute(Context context, List<TrackPointEntity> rawPoints) {
        List<RoutePoint> filtered = filterPoints(rawPoints, DISPLAY_MAX_ACCURACY_METERS, MAX_WALKING_DISPLAY_SPEED_MPS);
        if (filtered.size() < 2) {
            filtered = filterPoints(rawPoints, 60f, 8.0d);
        }
        if (filtered.size() < 2) {
            filtered = fallbackRoute(rawPoints);
        }
        List<RoutePoint> smoothed = smoothPreservingTurns(filtered);
        List<RoutePoint> simplified = simplify(smoothed, SIMPLIFY_TOLERANCE_METERS);
        return toAmapLatLngs(context, simplified);
    }

    private static List<RoutePoint> filterPoints(List<TrackPointEntity> rawPoints, float maxAccuracyMeters, double maxSpeedMps) {
        List<RoutePoint> result = new ArrayList<>();
        if (rawPoints == null || rawPoints.isEmpty()) {
            return result;
        }

        TrackPointEntity lastAccepted = null;
        for (TrackPointEntity point : rawPoints) {
            if (!isBasicUsable(point, maxAccuracyMeters, maxSpeedMps)) {
                continue;
            }
            if (lastAccepted != null && shouldRejectJump(lastAccepted, point, maxSpeedMps)) {
                continue;
            }
            if (lastAccepted != null) {
                double distance = DistanceUtils.haversineMeters(
                        lastAccepted.latitude, lastAccepted.longitude, point.latitude, point.longitude);
                if (distance < MIN_DISPLAY_STEP_METERS) {
                    continue;
                }
                if (distance < STILL_JITTER_METERS && AppConstants.STAYING.equals(point.movingState)) {
                    continue;
                }
            }
            result.add(new RoutePoint(point.latitude, point.longitude, point.timestamp));
            lastAccepted = point;
        }

        if (result.size() < 2) {
            return fallbackRoute(rawPoints);
        }
        return result;
    }

    private static boolean isBasicUsable(TrackPointEntity point, float maxAccuracyMeters, double maxSpeedMps) {
        if (point == null) {
            return false;
        }
        if (point.latitude == 0d && point.longitude == 0d) {
            return false;
        }
        if (point.accuracy > maxAccuracyMeters) {
            return false;
        }
        return point.speed <= 0f || point.speed <= maxSpeedMps;
    }

    private static boolean shouldRejectJump(TrackPointEntity previous, TrackPointEntity current, double maxSpeedMps) {
        long deltaMillis = Math.max(1L, current.timestamp - previous.timestamp);
        double distance = DistanceUtils.haversineMeters(
                previous.latitude, previous.longitude, current.latitude, current.longitude);
        double impliedSpeed = distance / (deltaMillis / 1000d);
        if (impliedSpeed > maxSpeedMps) {
            return true;
        }
        return deltaMillis <= SHORT_JUMP_WINDOW_MS && distance > MAX_SHORT_JUMP_METERS;
    }

    private static List<RoutePoint> fallbackRoute(List<TrackPointEntity> rawPoints) {
        List<RoutePoint> fallback = new ArrayList<>();
        for (TrackPointEntity point : rawPoints) {
            if (point != null && !(point.latitude == 0d && point.longitude == 0d)) {
                fallback.add(new RoutePoint(point.latitude, point.longitude, point.timestamp));
            }
        }
        return fallback;
    }

    private static List<RoutePoint> smoothPreservingTurns(List<RoutePoint> points) {
        if (points.size() <= 2) {
            return points;
        }
        List<RoutePoint> result = new ArrayList<>();
        result.add(points.get(0));
        for (int i = 1; i < points.size() - 1; i++) {
            RoutePoint previous = points.get(i - 1);
            RoutePoint current = points.get(i);
            RoutePoint next = points.get(i + 1);
            double turnAngle = angleBetween(previous, current, next);
            if (turnAngle >= TURN_KEEP_ANGLE_DEGREES) {
                result.add(current);
            } else {
                result.add(new RoutePoint(
                        previous.latitude * 0.25d + current.latitude * 0.5d + next.latitude * 0.25d,
                        previous.longitude * 0.25d + current.longitude * 0.5d + next.longitude * 0.25d,
                        current.timestamp));
            }
        }
        result.add(points.get(points.size() - 1));
        return result;
    }

    private static double angleBetween(RoutePoint a, RoutePoint b, RoutePoint c) {
        double ax = metersX(a.longitude - b.longitude, b.latitude);
        double ay = metersY(a.latitude - b.latitude);
        double cx = metersX(c.longitude - b.longitude, b.latitude);
        double cy = metersY(c.latitude - b.latitude);
        double dot = ax * cx + ay * cy;
        double mag = Math.sqrt(ax * ax + ay * ay) * Math.sqrt(cx * cx + cy * cy);
        if (mag <= 0d) {
            return 0d;
        }
        double cos = Math.max(-1d, Math.min(1d, dot / mag));
        return 180d - Math.toDegrees(Math.acos(cos));
    }

    private static List<RoutePoint> simplify(List<RoutePoint> points, double toleranceMeters) {
        if (points.size() <= 2) {
            return points;
        }
        boolean[] keep = new boolean[points.size()];
        keep[0] = true;
        keep[points.size() - 1] = true;
        simplifyRange(points, 0, points.size() - 1, toleranceMeters, keep);

        List<RoutePoint> result = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            if (keep[i]) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    private static void simplifyRange(List<RoutePoint> points, int start, int end, double toleranceMeters, boolean[] keep) {
        if (end <= start + 1) {
            return;
        }
        double maxDistance = -1d;
        int maxIndex = -1;
        for (int i = start + 1; i < end; i++) {
            double distance = perpendicularDistanceMeters(points.get(i), points.get(start), points.get(end));
            if (distance > maxDistance) {
                maxDistance = distance;
                maxIndex = i;
            }
        }
        if (maxDistance > toleranceMeters && maxIndex >= 0) {
            keep[maxIndex] = true;
            simplifyRange(points, start, maxIndex, toleranceMeters, keep);
            simplifyRange(points, maxIndex, end, toleranceMeters, keep);
        }
    }

    private static double perpendicularDistanceMeters(RoutePoint point, RoutePoint start, RoutePoint end) {
        double originLat = start.latitude;
        double px = metersX(point.longitude - start.longitude, originLat);
        double py = metersY(point.latitude - start.latitude);
        double ex = metersX(end.longitude - start.longitude, originLat);
        double ey = metersY(end.latitude - start.latitude);
        double lengthSquared = ex * ex + ey * ey;
        if (lengthSquared <= 0d) {
            return Math.sqrt(px * px + py * py);
        }
        double t = Math.max(0d, Math.min(1d, (px * ex + py * ey) / lengthSquared));
        double dx = px - t * ex;
        double dy = py - t * ey;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static List<LatLng> toAmapLatLngs(Context context, List<RoutePoint> routePoints) {
        List<LatLng> result = new ArrayList<>();
        for (RoutePoint point : routePoints) {
            result.add(AmapCoordinateUtils.fromGps(context, point.latitude, point.longitude));
        }
        return result;
    }

    private static double metersX(double longitudeDelta, double atLatitude) {
        return longitudeDelta * 111_320d * Math.cos(Math.toRadians(atLatitude));
    }

    private static double metersY(double latitudeDelta) {
        return latitudeDelta * 110_540d;
    }

    private static final class RoutePoint {
        final double latitude;
        final double longitude;
        final long timestamp;

        RoutePoint(double latitude, double longitude, long timestamp) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.timestamp = timestamp;
        }
    }
}
