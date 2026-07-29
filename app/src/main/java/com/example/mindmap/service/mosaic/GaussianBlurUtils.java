package com.example.mindmap.service.mosaic;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;

public final class GaussianBlurUtils {
    private GaussianBlurUtils() {}

    public static void blurRegion(Bitmap bitmap, Rect region) {
        Rect bounds = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());
        if (!region.intersect(bounds) || region.width() < 4 || region.height() < 4) {
            return;
        }
        Bitmap cropped = Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height());
        Bitmap blurred = gaussianBlur(cropped, 8);
        new Canvas(bitmap).drawBitmap(blurred, region.left, region.top, null);
        cropped.recycle();
        blurred.recycle();
    }

    private static Bitmap gaussianBlur(Bitmap source, int radius) {
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        int[] temp = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        int[] kernel = buildKernel(radius);
        int kernelSum = 0;
        for (int value : kernel) {
            kernelSum += value;
        }
        convolveHorizontal(pixels, temp, width, height, kernel, radius, kernelSum);
        convolveVertical(temp, pixels, width, height, kernel, radius, kernelSum);
        Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        output.setPixels(pixels, 0, width, 0, 0, width, height);
        return output;
    }

    private static int[] buildKernel(int radius) {
        int size = radius * 2 + 1;
        int[] kernel = new int[size];
        float sigma = Math.max(1f, radius / 2f);
        for (int i = 0; i < size; i++) {
            int x = i - radius;
            kernel[i] = Math.max(1, Math.round((float) Math.exp(-(x * x) / (2f * sigma * sigma)) * 1024f));
        }
        return kernel;
    }

    private static void convolveHorizontal(int[] input, int[] output, int width, int height, int[] kernel, int radius, int kernelSum) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int sampleX = clamp(x + k, 0, width - 1);
                    int color = input[y * width + sampleX];
                    int weight = kernel[k + radius];
                    a += ((color >>> 24) & 0xff) * weight;
                    r += ((color >>> 16) & 0xff) * weight;
                    g += ((color >>> 8) & 0xff) * weight;
                    b += (color & 0xff) * weight;
                }
                output[y * width + x] = ((a / kernelSum) << 24)
                        | ((r / kernelSum) << 16)
                        | ((g / kernelSum) << 8)
                        | (b / kernelSum);
            }
        }
    }

    private static void convolveVertical(int[] input, int[] output, int width, int height, int[] kernel, int radius, int kernelSum) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int a = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int sampleY = clamp(y + k, 0, height - 1);
                    int color = input[sampleY * width + x];
                    int weight = kernel[k + radius];
                    a += ((color >>> 24) & 0xff) * weight;
                    r += ((color >>> 16) & 0xff) * weight;
                    g += ((color >>> 8) & 0xff) * weight;
                    b += (color & 0xff) * weight;
                }
                output[y * width + x] = ((a / kernelSum) << 24)
                        | ((r / kernelSum) << 16)
                        | ((g / kernelSum) << 8)
                        | (b / kernelSum);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
