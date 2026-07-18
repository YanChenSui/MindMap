package com.example.mindmap.service.ros;

import com.example.mindmap.BuildConfig;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.util.AppConstants;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Predicts ROS scores from transcribed user speech through a Volcengine Responses API endpoint. */
public class DoubaoRosPredictionService {
    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 60_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public boolean isAvailable() {
        return !isBlank(BuildConfig.DOUBAO_LLM_API_KEY) && !isBlank(BuildConfig.DOUBAO_LLM_MODEL);
    }

    public void predict(long annotationId, String transcript, Callback callback) {
        executor.execute(() -> {
            try {
                if (isBlank(transcript)) {
                    throw new IOException("Transcript is empty.");
                }
                if (!isAvailable()) {
                    throw new IOException("Doubao LLM credentials are missing.");
                }
                callback.onComplete(requestPrediction(annotationId, transcript));
            } catch (Throwable throwable) {
                callback.onComplete(failedPrediction(annotationId, transcript, throwable));
            }
        });
    }

    private RosPredictionEntity requestPrediction(long annotationId, String transcript) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(BuildConfig.DOUBAO_LLM_API_URL).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Authorization", "Bearer " + BuildConfig.DOUBAO_LLM_API_KEY);

        byte[] body = buildRequestBody(transcript).getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(body.length);
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(body);
        }

        int httpStatus = connection.getResponseCode();
        String response = readResponse(httpStatus >= 200 && httpStatus < 300
                ? connection.getInputStream()
                : connection.getErrorStream());
        connection.disconnect();
        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IOException("Doubao LLM request failed. http=" + httpStatus + ", body=" + response);
        }
        return parsePrediction(annotationId, transcript, response);
    }

    private String buildRequestBody(String transcript) throws Exception {
        JSONArray input = new JSONArray()
                .put(new JSONObject()
                        .put("role", "system")
                        .put("content", systemPrompt()))
                .put(new JSONObject()
                        .put("role", "user")
                        .put("content", "用户语音转写内容：\n" + transcript));
        return new JSONObject()
                .put("model", BuildConfig.DOUBAO_LLM_MODEL)
                .put("input", input)
                .put("temperature", 0.1)
                .toString();
    }

    private String systemPrompt() {
        return "你是环境心理学 ROS 问卷评分助手。"
                + "请根据用户关于当前场景的语音转写，提取中文关键词，并估计七个 1-5 分评分。"
                + "评分含义：1=很弱，2=较弱，3=中等，4=较强，5=很强。"
                + "字段含义：visualPreferenceScore=用户对场景的喜爱程度；thoughtClarityScore=思路清晰程度；"
                + "worryForgetScore=忘记日常烦恼程度；restoredRelaxedScore=恢复与放松程度；"
                + "rosCalmScore=平静程度；interestScore=兴趣程度；focusedAlertScore=专注和警觉程度。"
                + "reason 必须使用中文，概括说明评分依据，不要使用英文句子。"
                + "只返回合法 JSON："
                + "{\"keywords\":[\"关键词\"],\"scores\":{\"visualPreferenceScore\":3,"
                + "\"thoughtClarityScore\":3,\"worryForgetScore\":3,\"restoredRelaxedScore\":3,"
                + "\"rosCalmScore\":3,\"interestScore\":3,\"focusedAlertScore\":3},\"reason\":\"中文评分依据\"}";
    }

    private RosPredictionEntity parsePrediction(long annotationId, String transcript, String response) throws Exception {
        JSONObject root = new JSONObject(response);
        String content = extractResponseText(root);
        if (isBlank(content)) {
            throw new IOException("No text content in LLM response.");
        }
        JSONObject payload = new JSONObject(extractJson(content));
        JSONObject scores = payload.getJSONObject("scores");

        RosPredictionEntity prediction = new RosPredictionEntity(
                annotationId,
                transcript,
                "doubao",
                BuildConfig.DOUBAO_LLM_MODEL,
                BuildConfig.ROS_PROMPT_VERSION,
                AppConstants.PREDICTION_STATUS_SUCCESS,
                System.currentTimeMillis());
        prediction.visualPreferenceScore = clampScore(scores.optInt("visualPreferenceScore", 3));
        prediction.thoughtClarityScore = clampScore(scores.optInt("thoughtClarityScore", 3));
        prediction.worryForgetScore = clampScore(scores.optInt("worryForgetScore", 3));
        prediction.restoredRelaxedScore = clampScore(scores.optInt("restoredRelaxedScore", 3));
        prediction.rosCalmScore = clampScore(scores.optInt("rosCalmScore", 3));
        prediction.interestScore = clampScore(scores.optInt("interestScore", 3));
        prediction.focusedAlertScore = clampScore(scores.optInt("focusedAlertScore", 3));
        JSONArray keywords = payload.optJSONArray("keywords");
        prediction.keywordsJson = keywords == null ? "[]" : keywords.toString();
        prediction.reason = payload.optString("reason", "");
        return prediction;
    }

    private String extractResponseText(JSONObject root) throws Exception {
        String outputText = root.optString("output_text", "");
        if (!outputText.trim().isEmpty()) {
            return outputText;
        }
        JSONArray output = root.optJSONArray("output");
        if (output != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < output.length(); i++) {
                JSONObject item = output.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                JSONArray content = item.optJSONArray("content");
                if (content == null) {
                    continue;
                }
                for (int j = 0; j < content.length(); j++) {
                    JSONObject contentItem = content.optJSONObject(j);
                    if (contentItem == null) {
                        continue;
                    }
                    String text = contentItem.optString("text", contentItem.optString("output_text", ""));
                    if (!text.trim().isEmpty()) {
                        builder.append(text);
                    }
                }
            }
            if (builder.length() > 0) {
                return builder.toString();
            }
        }
        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            return "";
        }
        JSONObject message = choices.getJSONObject(0).optJSONObject("message");
        return message == null ? "" : message.optString("content", "");
    }

    public RosPredictionEntity failedPrediction(long annotationId, String transcript, Throwable throwable) {
        RosPredictionEntity prediction = new RosPredictionEntity(
                annotationId,
                transcript,
                "doubao",
                BuildConfig.DOUBAO_LLM_MODEL,
                BuildConfig.ROS_PROMPT_VERSION,
                AppConstants.PREDICTION_STATUS_FAILED,
                System.currentTimeMillis());
        prediction.errorMessage = throwable == null ? "Unknown error." : throwable.getMessage();
        return prediction;
    }

    private String extractJson(String content) throws IOException {
        String value = content == null ? "" : content.trim();
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IOException("LLM response is not JSON: " + value);
        }
        return value.substring(start, end + 1);
    }

    private int clampScore(int score) {
        return Math.max(1, Math.min(5, score));
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public interface Callback {
        void onComplete(RosPredictionEntity prediction);
    }
}
