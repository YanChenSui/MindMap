package com.example.mindmap.service.mosaic;

public final class DetectionBox {
    public final float left;
    public final float top;
    public final float right;
    public final float bottom;
    public final float confidence;

    public DetectionBox(float left, float top, float right, float bottom, float confidence) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.confidence = confidence;
    }

    public float area() {
        return Math.max(0f, right - left) * Math.max(0f, bottom - top);
    }
}
