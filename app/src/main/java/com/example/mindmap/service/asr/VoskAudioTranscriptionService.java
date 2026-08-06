package com.example.mindmap.service.asr;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Transcribes saved audio files with an offline Vosk Chinese model bundled in assets/model-cn. */
public class VoskAudioTranscriptionService implements TranscriptionService {
    private static final String MODEL_ASSET_DIR = "model-cn";
    private static final String MODEL_TARGET_DIR = "model-cn";
    private static final long TIMEOUT_US = 10_000L;

    private static Model cachedModel;

    private final Context appContext;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public VoskAudioTranscriptionService(Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public boolean isAvailable() {
        try {
            String[] assets = appContext.getAssets().list(MODEL_ASSET_DIR);
            return assets != null && assets.length > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public String getCompatibilityNote() {
        return isAvailable()
                ? "已内置 Vosk 中文离线模型，录音保存后会在本机直接转写。"
                : "未找到 Vosk 中文模型，请将模型解压到 app/src/main/assets/model-cn/。";
    }

    @Override
    public void transcribe(File audioFile, Callback callback) {
        executor.execute(() -> {
            try {
                if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0L) {
                    throw new IOException("音频文件不存在或为空");
                }
                Model model = getModel();
                TranscriptionResult result = transcribeDecodedPcm(audioFile, model);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Throwable throwable) {
                mainHandler.post(() -> callback.onError(throwable));
            }
        });
    }

    private Model getModel() throws IOException {
        synchronized (VoskAudioTranscriptionService.class) {
            if (cachedModel != null) {
                return cachedModel;
            }
            if (!isAvailable()) {
                throw new IOException("未找到 Vosk 中文模型，请将模型解压到 app/src/main/assets/model-cn/");
            }
            String modelPath = StorageService.sync(appContext, MODEL_ASSET_DIR, MODEL_TARGET_DIR);
            cachedModel = new Model(modelPath);
            return cachedModel;
        }
    }

    private TranscriptionResult transcribeDecodedPcm(File audioFile, Model model) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec decoder = null;
        Recognizer recognizer = null;
        try {
            extractor.setDataSource(audioFile.getAbsolutePath());
            int trackIndex = selectAudioTrack(extractor);
            if (trackIndex < 0) {
                throw new IOException("音频文件中没有可识别的音轨");
            }
            extractor.selectTrack(trackIndex);
            MediaFormat format = extractor.getTrackFormat(trackIndex);
            String mime = format.getString(MediaFormat.KEY_MIME);
            int sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int channelCount = format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                    ? format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    : 1;
            if (mime == null) {
                throw new IOException("无法识别音频编码格式");
            }

            decoder = MediaCodec.createDecoderByType(mime);
            decoder.configure(format, null, null, 0);
            decoder.start();
            recognizer = new Recognizer(model, sampleRate);
            recognizer.setWords(true);
            TranscriptAccumulator accumulator = new TranscriptAccumulator();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean outputDone = false;
            while (!outputDone) {
                if (!inputDone) {
                    int inputIndex = decoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputIndex >= 0) {
                        ByteBuffer inputBuffer = decoder.getInputBuffer(inputIndex);
                        if (inputBuffer == null) {
                            throw new IOException("无法读取音频解码输入缓冲区");
                        }
                        int sampleSize = extractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }

                int outputIndex = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
                if (outputIndex >= 0) {
                    ByteBuffer outputBuffer = decoder.getOutputBuffer(outputIndex);
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset);
                        outputBuffer.limit(info.offset + info.size);
                        if (feedPcmToRecognizer(outputBuffer.slice(), channelCount, recognizer)) {
                            accumulator.add(recognizer.getResult());
                        }
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outputIndex, false);
                } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat outputFormat = decoder.getOutputFormat();
                    if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }
            accumulator.add(recognizer.getFinalResult());
            return accumulator.build();
        } finally {
            if (recognizer != null) {
                recognizer.close();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            extractor.release();
        }
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    private boolean feedPcmToRecognizer(ByteBuffer pcmBuffer, int channelCount, Recognizer recognizer) {
        pcmBuffer.order(ByteOrder.LITTLE_ENDIAN);
        if (channelCount <= 1) {
            byte[] mono = new byte[pcmBuffer.remaining()];
            pcmBuffer.get(mono);
            return recognizer.acceptWaveForm(mono, mono.length);
        }

        ShortBuffer samples = pcmBuffer.asShortBuffer();
        int frameCount = samples.remaining() / channelCount;
        ByteBuffer mono = ByteBuffer.allocate(frameCount * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frameCount; frame++) {
            int mixed = 0;
            for (int channel = 0; channel < channelCount; channel++) {
                mixed += samples.get();
            }
            mono.putShort((short) (mixed / channelCount));
        }
        return recognizer.acceptWaveForm(mono.array(), mono.position());
    }

    private static final class TranscriptAccumulator {
        private final StringBuilder text = new StringBuilder();
        private long startOffsetMillis = Long.MAX_VALUE;
        private long endOffsetMillis = -1L;

        void add(String resultJson) {
            if (resultJson == null || resultJson.trim().isEmpty()) {
                return;
            }
            try {
                JSONObject result = new JSONObject(resultJson);
                String segmentText = result.optString("text", "").trim();
                if (!segmentText.isEmpty()) {
                    if (text.length() > 0) {
                        text.append(' ');
                    }
                    text.append(segmentText);
                }
                JSONArray words = result.optJSONArray("result");
                if (words == null) {
                    return;
                }
                for (int i = 0; i < words.length(); i++) {
                    JSONObject word = words.optJSONObject(i);
                    if (word == null) {
                        continue;
                    }
                    long start = Math.round(word.optDouble("start", -1d) * 1000d);
                    long end = Math.round(word.optDouble("end", -1d) * 1000d);
                    if (start >= 0L) {
                        startOffsetMillis = Math.min(startOffsetMillis, start);
                    }
                    if (end >= 0L) {
                        endOffsetMillis = Math.max(endOffsetMillis, end);
                    }
                }
            } catch (Throwable ignored) {
                // Keep any successfully parsed segments; malformed partial results are non-fatal.
            }
        }

        TranscriptionResult build() {
            if (startOffsetMillis == Long.MAX_VALUE || endOffsetMillis < startOffsetMillis) {
                return TranscriptionResult.textOnly(text.toString());
            }
            return new TranscriptionResult(text.toString(), startOffsetMillis, endOffsetMillis);
        }
    }
}
