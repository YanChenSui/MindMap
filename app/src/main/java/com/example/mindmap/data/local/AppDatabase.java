package com.example.mindmap.data.local;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Database;
import androidx.room.migration.Migration;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.mindmap.data.local.dao.AnnotationDao;
import com.example.mindmap.data.local.dao.RosPredictionDao;
import com.example.mindmap.data.local.dao.TrackPointDao;
import com.example.mindmap.data.local.dao.TripDao;
import com.example.mindmap.data.local.entity.AnnotationEntity;
import com.example.mindmap.data.local.entity.RosPredictionEntity;
import com.example.mindmap.data.local.entity.TrackPointEntity;
import com.example.mindmap.data.local.entity.TripEntity;
import com.example.mindmap.util.AppConstants;

/**
 * Room 数据库入口。所有写入由 Repository 的后台线程执行，禁止主线程数据库访问。
 */
@Database(entities = {TripEntity.class, TrackPointEntity.class, AnnotationEntity.class, RosPredictionEntity.class}, version = 7, exportSchema = true)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase instance;
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE annotations ADD COLUMN visualPreferenceScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN thoughtClarityScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN worryForgetScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN restoredRelaxedScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN rosCalmScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN interestScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN focusedAlertScore INTEGER NOT NULL DEFAULT 3");
            database.execSQL("ALTER TABLE annotations ADD COLUMN rosAverageScore REAL NOT NULL DEFAULT 3.0");
        }
    };
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `ros_predictions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `annotationId` INTEGER NOT NULL, `transcript` TEXT, `visualPreferenceScore` INTEGER NOT NULL, `thoughtClarityScore` INTEGER NOT NULL, `worryForgetScore` INTEGER NOT NULL, `restoredRelaxedScore` INTEGER NOT NULL, `rosCalmScore` INTEGER NOT NULL, `interestScore` INTEGER NOT NULL, `focusedAlertScore` INTEGER NOT NULL, `keywordsJson` TEXT, `reason` TEXT, `modelName` TEXT, `modelVersion` TEXT, `promptVersion` TEXT, `status` TEXT, `errorMessage` TEXT, `createdAt` INTEGER NOT NULL, FOREIGN KEY(`annotationId`) REFERENCES `annotations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_ros_predictions_annotationId` ON `ros_predictions` (`annotationId`)");
        }
    };
    private static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            addTextColumnIfMissing(database, "trips", "gender");
            addTextColumnIfMissing(database, "trips", "ageGroup");
            addTextColumnIfMissing(database, "trips", "educationLevel");
        }
    };
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            addTextColumnIfMissing(database, "trips", "accountName");
        }
    };
    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            addTextColumnIfMissing(database, "annotations", "originalVideoUri");
            addTextColumnIfMissing(database, "annotations", "blurredVideoUri");
            addTextColumnIfMissing(database, "annotations", "videoMosaicStatus", "'" + AppConstants.MOSAIC_STATUS_NONE + "'");
            addTextColumnIfMissing(database, "annotations", "videoMosaicError");
        }
    };
    private static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            addIntegerColumnIfMissing(database, "annotations", "speechStartTimeMillis");
            addIntegerColumnIfMissing(database, "annotations", "speechEndTimeMillis");
        }
    };

    private static void addTextColumnIfMissing(SupportSQLiteDatabase database, String tableName, String columnName) {
        addTextColumnIfMissing(database, tableName, columnName, null);
    }

    private static void addTextColumnIfMissing(SupportSQLiteDatabase database, String tableName, String columnName, String defaultValue) {
        if (hasColumn(database, tableName, columnName)) {
            return;
        }
        String defaultClause = defaultValue == null ? "" : " DEFAULT " + defaultValue;
        database.execSQL("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` TEXT" + defaultClause);
    }

    private static void addIntegerColumnIfMissing(SupportSQLiteDatabase database, String tableName, String columnName) {
        if (!hasColumn(database, tableName, columnName)) {
            database.execSQL("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` INTEGER");
        }
    }

    private static boolean hasColumn(SupportSQLiteDatabase database, String tableName, String columnName) {
        try (Cursor cursor = database.query("PRAGMA table_info(`" + tableName + "`)")) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && columnName.equals(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    public abstract TripDao tripDao();
    public abstract TrackPointDao trackPointDao();
    public abstract AnnotationDao annotationDao();
    public abstract RosPredictionDao rosPredictionDao();

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(), AppDatabase.class, "mood_map.db")
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                            .build();
                }
            }
        }
        return instance;
    }
}
