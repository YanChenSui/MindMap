package com.example.mindmap.service.mosaic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;

import com.example.mindmap.util.TimeFormatUtils;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;

public final class VideoMosaicProcessor {
    private static final String MIME_AVC = "video/avc";
    private static final int DEFAULT_FPS = 15;
    private static final int I_FRAME_INTERVAL_SECONDS = 2;

    private final Context context;

    public VideoMosaicProcessor(Context context) {
        this.context = context.getApplicationContext();
    }

    public File process(String videoUri, File outputDir, ProgressCallback callback) throws Exception {
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("无法创建打码视频目录");
        }
        File output = new File(outputDir, "mosaic_" + TimeFormatUtils.fileSafeDate(System.currentTimeMillis()) + ".mp4");
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(context, Uri.parse(videoUri));
        int width = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH), 0);
        int height = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT), 0);
        long durationUs = parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION), 0L) * 1000L;
        int fps = parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE), DEFAULT_FPS);
        if (width <= 0 || height <= 0 || durationUs <= 0) {
            retriever.release();
            throw new IllegalArgumentException("无法读取视频尺寸或时长");
        }
        fps = Math.max(8, Math.min(24, fps));
        int frameCount = Math.max(1, (int) Math.ceil(durationUs / 1_000_000d * fps));
        int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

        MediaFormat format = MediaFormat.createVideoFormat(MIME_AVC, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
        format.setInteger(MediaFormat.KEY_BIT_RATE, Math.max(1_500_000, width * height * 3));
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL_SECONDS);

        MediaCodec encoder = MediaCodec.createEncoderByType(MIME_AVC);
        MediaMuxer muxer = null;
        try (Yolov9PersonDetector detector = new Yolov9PersonDetector(context)) {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            muxer = new MediaMuxer(output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            encodeFrames(retriever, detector, encoder, muxer, width, height, durationUs, frameCount, colorFormat, callback);
        } finally {
            try {
                encoder.stop();
            } catch (Throwable ignored) {
            }
            encoder.release();
            if (muxer != null) {
                try {
                    muxer.stop();
                } catch (Throwable ignored) {
                }
                muxer.release();
            }
            retriever.release();
        }
        if (!output.exists() || output.length() == 0) {
            throw new IllegalStateException("打码视频生成失败");
        }
        return output;
    }

    private void encodeFrames(MediaMetadataRetriever retriever,
                              Yolov9PersonDetector detector,
                              MediaCodec encoder,
                              MediaMuxer muxer,
                              int width,
                              int height,
                              long durationUs,
                              int frameCount,
                              int colorFormat,
                              ProgressCallback callback) throws Exception {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean muxerStarted = false;
        int videoTrackIndex = -1;
        for (int frameIndex = 0; frameIndex < frameCount; frameIndex++) {
            long presentationTimeUs = Math.min(durationUs - 1, frameIndex * durationUs / frameCount);
            Bitmap frame = retriever.getFrameAtTime(presentationTimeUs, MediaMetadataRetriever.OPTION_CLOSEST);
            if (frame == null) {
                continue;
            }
            Bitmap mutable = Bitmap.createScaledBitmap(frame, width, height, true)
                    .copy(Bitmap.Config.ARGB_8888, true);
            if (mutable != frame) {
                frame.recycle();
            }
            blurPersonHeads(mutable, detector.detect(mutable));
            queueFrame(encoder, mutable, presentationTimeUs, colorFormat);
            mutable.recycle();

            DrainResult drainResult = drainEncoder(encoder, muxer, info, muxerStarted, videoTrackIndex, false);
            muxerStarted = drainResult.muxerStarted;
            videoTrackIndex = drainResult.trackIndex;
            if (callback != null) {
                callback.onProgress(Math.min(99, Math.round((frameIndex + 1) * 100f / frameCount)));
            }
        }
        int inputIndex = encoder.dequeueInputBuffer(10_000);
        if (inputIndex >= 0) {
            encoder.queueInputBuffer(inputIndex, 0, 0, durationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        drainEncoder(encoder, muxer, info, muxerStarted, videoTrackIndex, true);
        if (callback != null) {
            callback.onProgress(100);
        }
    }

    private void blurPersonHeads(Bitmap frame, List<DetectionBox> boxes) {
        for (DetectionBox box : boxes) {
            int personWidth = Math.round(box.right - box.left);
            int personHeight = Math.round(box.bottom - box.top);
            if (personWidth < 24 || personHeight < 48) {
                continue;
            }
            int headHeight = Math.round(personHeight * 0.28f);
            int horizontalPad = Math.round(personWidth * 0.08f);
            int verticalPad = Math.round(personHeight * 0.03f);
            Rect head = new Rect(
                    Math.max(0, Math.round(box.left) - horizontalPad),
                    Math.max(0, Math.round(box.top) - verticalPad),
                    Math.min(frame.getWidth(), Math.round(box.right) + horizontalPad),
                    Math.min(frame.getHeight(), Math.round(box.top) + headHeight)
            );
            GaussianBlurUtils.blurRegion(frame, head);
        }
    }

    private void queueFrame(MediaCodec encoder, Bitmap bitmap, long presentationTimeUs, int colorFormat) {
        int inputIndex = encoder.dequeueInputBuffer(10_000);
        if (inputIndex < 0) {
            return;
        }
        ByteBuffer buffer = encoder.getInputBuffer(inputIndex);
        if (buffer == null) {
            return;
        }
        buffer.clear();
        byte[] yuv = bitmapToYuv420(bitmap, colorFormat);
        buffer.put(yuv);
        encoder.queueInputBuffer(inputIndex, 0, yuv.length, presentationTimeUs, 0);
    }

    private DrainResult drainEncoder(MediaCodec encoder,
                                     MediaMuxer muxer,
                                     MediaCodec.BufferInfo info,
                                     boolean muxerStarted,
                                     int trackIndex,
                                     boolean waitForEos) {
        while (true) {
            int outputIndex = encoder.dequeueOutputBuffer(info, waitForEos ? 10_000 : 0);
            if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                return new DrainResult(muxerStarted, trackIndex);
            }
            if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                trackIndex = muxer.addTrack(encoder.getOutputFormat());
                muxer.start();
                muxerStarted = true;
                continue;
            }
            if (outputIndex < 0) {
                continue;
            }
            ByteBuffer encodedData = encoder.getOutputBuffer(outputIndex);
            if (encodedData != null && info.size > 0 && muxerStarted) {
                encodedData.position(info.offset);
                encodedData.limit(info.offset + info.size);
                muxer.writeSampleData(trackIndex, encodedData, info);
            }
            boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            encoder.releaseOutputBuffer(outputIndex, false);
            if (eos) {
                return new DrainResult(muxerStarted, trackIndex);
            }
        }
    }

    private byte[] bitmapToYuv420(Bitmap bitmap, int colorFormat) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int[] argb = new int[width * height];
        bitmap.getPixels(argb, 0, width, 0, 0, width, height);
        byte[] yuv = new byte[width * height * 3 / 2];
        int yIndex = 0;
        int uvIndex = width * height;
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                int color = argb[j * width + i];
                int r = (color >> 16) & 0xff;
                int g = (color >> 8) & 0xff;
                int b = color & 0xff;
                int y = clamp(((66 * r + 129 * g + 25 * b + 128) >> 8) + 16);
                int u = clamp(((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128);
                int v = clamp(((112 * r - 94 * g - 18 * b + 128) >> 8) + 128);
                yuv[yIndex++] = (byte) y;
                if (j % 2 == 0 && i % 2 == 0) {
                    if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                        int quarter = width * height / 4;
                        int chromaIndex = (j / 2) * (width / 2) + (i / 2);
                        yuv[width * height + chromaIndex] = (byte) u;
                        yuv[width * height + quarter + chromaIndex] = (byte) v;
                    } else {
                        yuv[uvIndex++] = (byte) u;
                        yuv[uvIndex++] = (byte) v;
                    }
                }
            }
        }
        return yuv;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private int parseInt(String value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Math.round(Float.parseFloat(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private long parseLong(String value, long fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public interface ProgressCallback {
        void onProgress(int percent);
    }

    private static final class DrainResult {
        final boolean muxerStarted;
        final int trackIndex;

        DrainResult(boolean muxerStarted, int trackIndex) {
            this.muxerStarted = muxerStarted;
            this.trackIndex = trackIndex;
        }
    }
}
