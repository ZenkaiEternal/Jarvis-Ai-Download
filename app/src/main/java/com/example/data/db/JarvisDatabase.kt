package com.example.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.FaceProfileEntity
import com.example.data.model.FaceRecognitionEventEntity
import com.example.data.model.FingertipMovementEntity
import com.example.data.model.LogReportEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity

@Database(
    entities = [
        MemoryEntity::class,
        NoteEntity::class,
        ReminderEntity::class,
        AuditLogEntity::class,
        FingertipMovementEntity::class,
        FaceProfileEntity::class,
        FaceRecognitionEventEntity::class,
        LogReportEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JarvisDatabase : RoomDatabase() {
    abstract fun memoryDao(): MemoryDao
    abstract fun noteDao(): NoteDao
    abstract fun reminderDao(): ReminderDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun fingertipMovementDao(): FingertipMovementDao
    abstract fun faceProfileDao(): FaceProfileDao
    abstract fun faceRecognitionDao(): FaceRecognitionDao
    abstract fun logReportDao(): LogReportDao

    companion object {
        @Volatile
        private var INSTANCE: JarvisDatabase? = null

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `fingertip_movements` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `sessionId` TEXT NOT NULL,
                        `gesture` TEXT NOT NULL,
                        `fingertipX` REAL NOT NULL,
                        `fingertipY` REAL NOT NULL,
                        `offsetX` REAL NOT NULL,
                        `offsetY` REAL NOT NULL,
                        `yawDeg` REAL NOT NULL,
                        `pitchDeg` REAL NOT NULL,
                        `zoomScale` REAL NOT NULL,
                        `handCount` INTEGER NOT NULL,
                        `syncedToCloud` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fingertip_movements_timestamp` ON `fingertip_movements` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_fingertip_movements_sessionId` ON `fingertip_movements` (`sessionId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `face_profiles` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `personName` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `signatureJson` TEXT NOT NULL,
                        `enrolledAt` INTEGER NOT NULL,
                        `lastSeenAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_face_profiles_personName` ON `face_profiles` (`personName`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `facial_recognition_events` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `matchedProfileId` INTEGER,
                        `recognizedLabel` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `smilingProbability` REAL,
                        `leftEyeOpenProbability` REAL,
                        `rightEyeOpenProbability` REAL,
                        `headYawDeg` REAL NOT NULL,
                        `headPitchDeg` REAL NOT NULL,
                        `headRollDeg` REAL NOT NULL,
                        `boundingBoxSummary` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `syncedToCloud` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_facial_recognition_events_timestamp` ON `facial_recognition_events` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_facial_recognition_events_matchedProfileId` ON `facial_recognition_events` (`matchedProfileId`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `log_reports` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `category` TEXT NOT NULL,
                        `severity` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `metricsJson` TEXT NOT NULL,
                        `syncedToCloud` INTEGER NOT NULL,
                        `timestamp` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_reports_timestamp` ON `log_reports` (`timestamp`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_log_reports_category` ON `log_reports` (`category`)")
            }
        }

        fun getInstance(context: Context): JarvisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    JarvisDatabase::class.java,
                    "jarvis_core.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
