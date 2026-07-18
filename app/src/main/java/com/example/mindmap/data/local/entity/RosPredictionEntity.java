package com.example.mindmap.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "ros_predictions",
        foreignKeys = @ForeignKey(
                entity = AnnotationEntity.class,
                parentColumns = "id",
                childColumns = "annotationId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("annotationId")}
)
public class RosPredictionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public long annotationId;
    public String transcript;
    public int visualPreferenceScore;
    public int thoughtClarityScore;
    public int worryForgetScore;
    public int restoredRelaxedScore;
    public int rosCalmScore;
    public int interestScore;
    public int focusedAlertScore;
    public String keywordsJson;
    public String reason;
    public String modelName;
    public String modelVersion;
    public String promptVersion;
    public String status;
    public String errorMessage;
    public long createdAt;

    public RosPredictionEntity(long annotationId, String transcript, String modelName,
                               String modelVersion, String promptVersion, String status,
                               long createdAt) {
        this.annotationId = annotationId;
        this.transcript = transcript;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.status = status;
        this.createdAt = createdAt;
    }
}
