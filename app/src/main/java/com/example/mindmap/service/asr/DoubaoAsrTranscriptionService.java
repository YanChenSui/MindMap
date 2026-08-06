package com.example.mindmap.service.asr;

import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import com.example.mindmap.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Transcribes saved media files through Volcengine/Doubao BigModel file ASR. */
public class DoubaoAsrTranscriptionService implements TranscriptionService {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 120_000;
    private static final String SUCCESS_STATUS_CODE = "20000000";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public boolean isAvailable() {
        return hasNewConsoleCredential() || hasLegacyConsoleCredential();
    }

    @Override
    public String getCompatibilityNote() {
        return isAvailable()
                ? "Doubao ASR is configured and saved media will be transcribed in the cloud."
                : "Doubao ASR is not configured. The app will fall back to offline transcription.";
    }

    @Override
    public void transcribe(File audioFile, Callback callback) {
        executor.execute(() -> {
            try {
                if (!isAvailable()) {
                    throw new IOException("Doubao ASR credentials are missing.");
                }
                if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0L) {
                    throw new IOException("Audio or video file does not exist.");
                }
                TranscriptionResult result = requestTranscription(audioFile);
                mainHandler.post(() -> callback.onSuccess(result));
            } catch (Throwable throwable) {
                mainHandler.post(() -> callback.onError(throwable));
            }
        });
    }

    private TranscriptionResult requestTranscription(File mediaFile) throws Exception {
        DecodedAudioFile preparedAudio = AudioFileNormalizer.prepareForDoubao(mediaFile);
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.DOUBAO_ASR_API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("X-Api-Resource-Id", BuildConfig.DOUBAO_ASR_RESOURCE_ID);
            connection.setRequestProperty("X-Api-Request-Id", UUID.randomUUID().toString());
            connection.setRequestProperty("X-Api-Sequence", "-1");
            if (hasNewConsoleCredential()) {
                connection.setRequestProperty("X-Api-Key", BuildConfig.DOUBAO_ASR_API_KEY);
            } else {
                connection.setRequestProperty("X-Api-App-Key", BuildConfig.DOUBAO_ASR_APP_KEY);
                connection.setRequestProperty("X-Api-Access-Key", BuildConfig.DOUBAO_ASR_ACCESS_KEY);
            }

            byte[] body = buildRequestBody(preparedAudio).getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(body.length);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }

            int httpStatus = connection.getResponseCode();
            String apiStatus = connection.getHeaderField("X-Api-Status-Code");
            String apiMessage = connection.getHeaderField("X-Api-Message");
            String logId = connection.getHeaderField("X-Tt-Logid");
            String response = readResponse(httpStatus >= 200 && httpStatus < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            connection.disconnect();

            if (httpStatus < 200 || httpStatus >= 300 || !SUCCESS_STATUS_CODE.equals(apiStatus)) {
                throw new IOException("Doubao ASR request failed. http=" + httpStatus
                        + ", code=" + nullToEmpty(apiStatus)
                        + ", message=" + nullToEmpty(apiMessage)
                        + ", logid=" + nullToEmpty(logId)
                        + ", body=" + response);
            }
            return parseResult(response);
        } finally {
            if (preparedAudio.temporary && preparedAudio.file.exists()) {
                preparedAudio.file.delete();
            }
        }
    }

    private String buildRequestBody(DecodedAudioFile audioFile) throws Exception {
        JSONObject audio = new JSONObject()
                .put("data", encodeFile(audioFile.file))
                .put("format", audioFile.format);
        JSONObject request = new JSONObject()
                .put("model_name", "bigmodel")
                .put("enable_itn", true)
                .put("enable_punc", true)
                .put("show_utterances", true);
        return new JSONObject()
                .put("user", new JSONObject().put("uid", credentialForUid()))
                .put("audio", audio)
                .put("request", request)
                .toString();
    }

    private String encodeFile(File file) throws IOException {
        byte[] buffer = new byte[(int) file.length()];
        int offset = 0;
        try (FileInputStream inputStream = new FileInputStream(file)) {
            while (offset < buffer.length) {
                int read = inputStream.read(buffer, offset, buffer.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }
        if (offset != buffer.length) {
            throw new IOException("Failed to read media file.");
        }
        return Base64.encodeToString(buffer, Base64.NO_WRAP);
    }

    private String readResponse(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    static TranscriptionResult parseResult(String response) throws Exception {
        JSONObject json = new JSONObject(response);
        JSONObject result = json.optJSONObject("result");
        if (result == null) {
            return TranscriptionResult.textOnly("");
        }

        String text = result.optString("text", "").trim();
        JSONArray utterances = result.optJSONArray("utterances");
        StringBuilder builder = new StringBuilder();
        long startOffsetMillis = Long.MAX_VALUE;
        long endOffsetMillis = -1L;
        if (utterances != null) {
            for (int i = 0; i < utterances.length(); i++) {
                JSONObject item = utterances.optJSONObject(i);
                String itemText = item == null ? "" : item.optString("text", "").trim();
                if (!itemText.isEmpty()) {
                    builder.append(itemText);
                }
                if (item != null) {
                    long start = optMillis(item, "start_time", "startTime");
                    long end = optMillis(item, "end_time", "endTime");
                    if (start < 0L) {
                        start = optSecondsAsMillis(item, "start");
                    }
                    if (end < 0L) {
                        end = optSecondsAsMillis(item, "end");
                    }
                    if (start >= 0L) {
                        startOffsetMillis = Math.min(startOffsetMillis, start);
                    }
                    if (end >= 0L) {
                        endOffsetMillis = Math.max(endOffsetMillis, end);
                    }
                }
            }
        }
        String finalText = text.isEmpty() ? builder.toString() : text;
        if (startOffsetMillis == Long.MAX_VALUE || endOffsetMillis < startOffsetMillis) {
            return TranscriptionResult.textOnly(finalText);
        }
        return new TranscriptionResult(finalText, startOffsetMillis, endOffsetMillis);
    }

    private static long optMillis(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.has(key)) {
                return Math.max(-1L, Math.round(item.optDouble(key, -1d)));
            }
        }
        return -1L;
    }

    private static long optSecondsAsMillis(JSONObject item, String key) {
        return item.has(key) ? Math.max(-1L, Math.round(item.optDouble(key, -1d) * 1000d)) : -1L;
    }

    private boolean hasNewConsoleCredential() {
        return !isBlank(BuildConfig.DOUBAO_ASR_API_KEY);
    }

    private boolean hasLegacyConsoleCredential() {
        return !isBlank(BuildConfig.DOUBAO_ASR_APP_KEY) && !isBlank(BuildConfig.DOUBAO_ASR_ACCESS_KEY);
    }

    private String credentialForUid() {
        if (hasNewConsoleCredential()) {
            return BuildConfig.DOUBAO_ASR_API_KEY;
        }
        return BuildConfig.DOUBAO_ASR_APP_KEY;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
