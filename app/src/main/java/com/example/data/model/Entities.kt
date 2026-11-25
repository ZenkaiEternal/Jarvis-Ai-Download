package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val value: String,
    val category: String = "PREFERENCE", // PREFERENCE, PROFILE, DIRECTIVE, FACT
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val tag: String = "LOG",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val dueTime: Long,
    val isCompleted: Boolean = false,
    val priority: String = "NORMAL"
)

@Entity(tableName = "audit_logs")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val details: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "EXECUTED" // EXECUTED, CONFIRMED, ABORTED
)

@Entity(
    tableName = "fingertip_movements",
    indices = [Index(value = ["timestamp"]), Index(value = ["sessionId"])]
)
data class FingertipMovementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val gesture: String,
    val fingertipX: Float,
    val fingertipY: Float,
    val offsetX: Float,
    val offsetY: Float,
    val yawDeg: Float,
    val pitchDeg: Float,
    val zoomScale: Float,
    val handCount: Int = 1,
    val syncedToCloud: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "face_profiles",
    indices = [Index(value = ["personName"], unique = true)]
)
data class FaceProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personName: String,
    val role: String = "AUTHORIZED",
    val signatureJson: String,
    val enrolledAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "facial_recognition_events",
    indices = [Index(value = ["timestamp"]), Index(value = ["matchedProfileId"])]
)
data class FaceRecognitionEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchedProfileId: Long? = null,
    val recognizedLabel: String,
    val confidence: Float,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headYawDeg: Float = 0f,
    val headPitchDeg: Float = 0f,
    val headRollDeg: Float = 0f,
    val boundingBoxSummary: String = "",
    val status: String = "DETECTION_ONLY", // VERIFIED, UNRECOGNIZED, DETECTION_ONLY
    val syncedToCloud: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "log_reports",
    indices = [Index(value = ["timestamp"]), Index(value = ["category"])]
)
data class LogReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // FINGERTIP, FACE_RECOGNITION, VOICE_CMD, OPENJARVIS_AI, SYSTEM, SQL_SYNC
    val severity: String = "INFO", // INFO, WARN, ALERT
    val title: String,
    val summary: String,
    val metricsJson: String = "{}",
    val syncedToCloud: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class SqlTelemetrySummary(
    val fingertipCount: Int = 0,
    val faceEventCount: Int = 0,
    val enrolledFaceCount: Int = 0,
    val logReportCount: Int = 0,
    val unsyncedCount: Int = 0
)

data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val highLevelReasoning: String? = null,
    val actionTaken: String? = null,
    val toolResult: String? = null,
    val isThinking: Boolean = false
)

enum class MessageSender {
    USER,
    JARVIS,
    SYSTEM
}

enum class JarvisVoiceState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}
