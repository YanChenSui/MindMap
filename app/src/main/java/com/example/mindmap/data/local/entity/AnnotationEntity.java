package com.example.mindmap.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.ColumnInfo;
import androidx.room.PrimaryKey;

/**
 * 用户在景观点创建的情绪标注，绑定位置、评分、文字、音频和视频资料。
 */
@Entity(
        tableName = "annotations",
        foreignKeys = @ForeignKey(
                entity = TripEntity.class,
                parentColumns = "id",
                childColumns = "tripId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("tripId"), @Index("timestamp")}
)
public class AnnotationEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long tripId;
    public double latitude;
    public double longitude;
    public long timestamp;
    public String videoUri;
    public String videoThumbnailUri;
    public String originalVideoUri;
    public String blurredVideoUri;
    @ColumnInfo(defaultValue = "'NONE'")
    public String videoMosaicStatus;
    public String videoMosaicError;
    public String audioUri;
    public String speechText;
    /** Unix epoch milliseconds for the first detected spoken word in the recorded media. */
    public Long speechStartTimeMillis;
    /** Unix epoch milliseconds for the end of the last detected spoken word in the recorded media. */
    public Long speechEndTimeMillis;
    public String textNote;
    public int pleasureScore;
    public int calmScore;
    public int relaxationScore;
    public int focusScore;
    public float averageScore;
    @ColumnInfo(defaultValue = "3")
    public int visualPreferenceScore;
    @ColumnInfo(defaultValue = "3")
    public int thoughtClarityScore;
    @ColumnInfo(defaultValue = "3")
    public int worryForgetScore;
    @ColumnInfo(defaultValue = "3")
    public int restoredRelaxedScore;
    @ColumnInfo(defaultValue = "3")
    public int rosCalmScore;
    @ColumnInfo(defaultValue = "3")
    public int interestScore;
    @ColumnInfo(defaultValue = "3")
    public int focusedAlertScore;
    @ColumnInfo(defaultValue = "3.0")
    public float rosAverageScore;
    public float cameraPitch;
    public float cameraRoll;
    public float cameraYaw;
    public long durationMillis;
    public String landscapeLabel;
    public long createdAt;

    public AnnotationEntity(long tripId, double latitude, double longitude, long timestamp) {
        this.tripId = tripId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.createdAt = System.currentTimeMillis();
    }
}
