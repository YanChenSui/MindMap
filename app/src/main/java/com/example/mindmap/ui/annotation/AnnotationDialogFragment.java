package com.example.mindmap.ui.annotation;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.mindmap.MainActivity;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.service.ros.RosPredictionWorkflow;
import com.example.mindmap.ui.UiFactory;
import com.example.mindmap.ui.viewmodel.MoodMapViewModel;
import com.example.mindmap.util.AppConstants;
import com.example.mindmap.util.TimeFormatUtils;

/**
 * 创建情绪标注。没有视频或录音时也可以独立保存文字和评分。
 */
public class AnnotationDialogFragment extends DialogFragment {
    public static final String REQUEST_ANNOTATION_SAVED = "annotation_saved";
    public static final String REQUEST_ANNOTATION_DISCARDED = "annotation_discarded";

    private static final String ARG_TRIP_ID = "trip_id";
    private static final String ARG_LAT = "lat";
    private static final String ARG_LON = "lon";
    private static final String ARG_VIDEO = "video";
    private static final String ARG_VIDEO_THUMBNAIL = "video_thumbnail";
    private static final String ARG_AUDIO = "audio";
    private static final String ARG_SPEECH = "speech";
    private static final String ARG_PITCH = "pitch";
    private static final String ARG_ROLL = "roll";
    private static final String ARG_YAW = "yaw";
    private static final String ARG_DURATION = "duration";
    private static final String ARG_SPEECH_START_TIME = "speech_start_time";
    private static final String ARG_SPEECH_END_TIME = "speech_end_time";

    public static AnnotationDialogFragment newInstance(long tripId, double latitude, double longitude,
                                                       @Nullable String videoUri, @Nullable String videoThumbnailUri,
                                                       @Nullable String audioUri, @Nullable String speechText,
                                                       float pitch, float roll, float yaw, long durationMillis,
                                                       @Nullable Long speechStartTimeMillis,
                                                       @Nullable Long speechEndTimeMillis) {
        AnnotationDialogFragment fragment = new AnnotationDialogFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_TRIP_ID, tripId);
        args.putDouble(ARG_LAT, latitude);
        args.putDouble(ARG_LON, longitude);
        args.putString(ARG_VIDEO, videoUri);
        args.putString(ARG_VIDEO_THUMBNAIL, videoThumbnailUri);
        args.putString(ARG_AUDIO, audioUri);
        args.putString(ARG_SPEECH, speechText);
        args.putFloat(ARG_PITCH, pitch);
        args.putFloat(ARG_ROLL, roll);
        args.putFloat(ARG_YAW, yaw);
        args.putLong(ARG_DURATION, durationMillis);
        if (speechStartTimeMillis != null) {
            args.putLong(ARG_SPEECH_START_TIME, speechStartTimeMillis);
        }
        if (speechEndTimeMillis != null) {
            args.putLong(ARG_SPEECH_END_TIME, speechEndTimeMillis);
        }
        fragment.setArguments(args);
        return fragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MoodMapViewModel viewModel = ((MainActivity) requireActivity()).getSharedViewModel();
        RosPredictionWorkflow predictionWorkflow = new RosPredictionWorkflow(requireContext(), viewModel.getRepository());
        Bundle args = requireArguments();
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiFactory.dp(requireContext(), 20), 0, UiFactory.dp(requireContext(), 20), 0);
        ScrollView scrollView = new ScrollView(requireContext());
        scrollView.setFillViewport(false);
        ScoreRow visualPreference = new ScoreRow("Q1 视觉偏好", "你喜欢这个场景的程度");
        ScoreRow thoughtClarity = new ScoreRow("Q2 思绪清晰", "这个场景让我的思绪变得清晰");
        ScoreRow worryForget = new ScoreRow("Q3 忘记烦恼", "这个场景让我暂时忘记日常烦恼");
        ScoreRow restoredRelaxed = new ScoreRow("Q4 放松恢复", "这个场景让我感到恢复和放松");
        ScoreRow calm = new ScoreRow("Q5 平静感", "这个场景让我感到平静");
        ScoreRow interest = new ScoreRow("Q6 兴趣唤起", "这个场景激发了我的兴趣");
        ScoreRow focusedAlert = new ScoreRow("Q7 注意恢复", "这个场景让我感到专注和清醒");
        EditText note = new EditText(requireContext());
        note.setHint("文字备注（最多 1000 字）");
        note.setMinLines(3);
        EditText speech = new EditText(requireContext());
        speech.setHint("语音转写或手动补充文字");
        speech.setText(args.getString(ARG_SPEECH, ""));
        Long speechStartTime = args.containsKey(ARG_SPEECH_START_TIME)
                ? args.getLong(ARG_SPEECH_START_TIME)
                : null;
        Long speechEndTime = args.containsKey(ARG_SPEECH_END_TIME)
                ? args.getLong(ARG_SPEECH_END_TIME)
                : null;
        root.addView(UiFactory.mutedText(requireContext(), "拍摄角度：Pitch "
                + args.getFloat(ARG_PITCH) + "° / Roll "
                + args.getFloat(ARG_ROLL) + "° / Yaw "
                + args.getFloat(ARG_YAW) + "°"));
        root.addView(UiFactory.mutedText(requireContext(), "检测到的说话时间："
                + TimeFormatUtils.speechTimeRange(speechStartTime, speechEndTime)));
        root.addView(visualPreference.view);
        root.addView(thoughtClarity.view);
        root.addView(worryForget.view);
        root.addView(restoredRelaxed.view);
        root.addView(calm.view);
        root.addView(interest.view);
        root.addView(focusedAlert.view);
        root.addView(note);
        root.addView(speech);
        scrollView.addView(root);
        return new AlertDialog.Builder(requireContext())
                .setTitle("ROS 恢复性感知问卷")
                .setView(scrollView)
                .setPositiveButton("保存", (dialog, which) -> {
                    String noteText = note.getText().toString();
                    if (noteText.length() > AppConstants.MAX_NOTE_LENGTH) {
                        noteText = noteText.substring(0, AppConstants.MAX_NOTE_LENGTH);
                    }
                    AnnotationEntity annotation = new AnnotationEntity(args.getLong(ARG_TRIP_ID), args.getDouble(ARG_LAT), args.getDouble(ARG_LON), System.currentTimeMillis());
                    annotation.videoUri = args.getString(ARG_VIDEO);
                    annotation.videoThumbnailUri = args.getString(ARG_VIDEO_THUMBNAIL);
                    annotation.audioUri = args.getString(ARG_AUDIO);
                    annotation.textNote = noteText;
                    annotation.speechText = speech.getText().toString();
                    annotation.speechStartTimeMillis = speechStartTime;
                    annotation.speechEndTimeMillis = speechEndTime;
                    annotation.cameraPitch = args.getFloat(ARG_PITCH);
                    annotation.cameraRoll = args.getFloat(ARG_ROLL);
                    annotation.cameraYaw = args.getFloat(ARG_YAW);
                    annotation.durationMillis = args.getLong(ARG_DURATION);
                    viewModel.saveRosAnnotation(annotation,
                            visualPreference.score(),
                            thoughtClarity.score(),
                            worryForget.score(),
                            restoredRelaxed.score(),
                            calm.score(),
                            interest.score(),
                            focusedAlert.score(),
                            annotationId -> {
                                annotation.id = annotationId;
                                predictionWorkflow.start(annotation);
                            });
                    getParentFragmentManager().setFragmentResult(REQUEST_ANNOTATION_SAVED, new Bundle());
                })
                .setNeutralButton("不保存本次标注", (dialog, which) ->
                        getParentFragmentManager().setFragmentResult(REQUEST_ANNOTATION_DISCARDED, new Bundle()))
                .setNegativeButton("继续填写", null)
                .create();
    }

    private class ScoreRow {
        final View view;
        final TextView value;
        final SeekBar seekBar;

        ScoreRow(String label, String description) {
            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.VERTICAL);
            TextView title = new TextView(requireContext());
            title.setText(label + "：3");
            value = title;
            TextView subtitle = UiFactory.mutedText(requireContext(), description);
            seekBar = new SeekBar(requireContext());
            seekBar.setMax(4);
            seekBar.setProgress(2);
            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { value.setText(label + "：" + (progress + 1)); }
                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
            row.addView(title);
            row.addView(subtitle);
            row.addView(seekBar);
            view = row;
        }

        int score() {
            return seekBar.getProgress() + 1;
        }
    }

}
