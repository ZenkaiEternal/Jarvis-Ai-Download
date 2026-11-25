package com.example.data.repository

import com.example.data.db.JarvisDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.FaceProfileEntity
import com.example.data.model.FaceRecognitionEventEntity
import com.example.data.model.FingertipMovementEntity
import com.example.data.model.LogReportEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity
import com.example.data.model.SqlTelemetrySummary
import com.example.domain.vision.FaceRecognitionOutput
import com.example.domain.vision.FaceSignatureMatcher
import com.example.domain.vision.HandTrackingOutput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UnsyncedSqlBatch(
    val fingertipMovements: List<FingertipMovementEntity>,
    val faceEvents: List<FaceRecognitionEventEntity>,
    val logReports: List<LogReportEntity>
) {
    val totalCount: Int
        get() = fingertipMovements.size + faceEvents.size + logReports.size
}

class JarvisRepository(private val database: JarvisDatabase) {
    val memories: Flow<List<MemoryEntity>> = database.memoryDao().getAllMemories()
    val notes: Flow<List<NoteEntity>> = database.noteDao().getAllNotes()
    val reminders: Flow<List<ReminderEntity>> = database.reminderDao().getAllReminders()
    val auditLogs: Flow<List<AuditLogEntity>> = database.auditLogDao().getRecentLogs()

    val fingertipMovements: Flow<List<FingertipMovementEntity>> =
        database.fingertipMovementDao().getRecentMovements(120)
    val faceProfiles: Flow<List<FaceProfileEntity>> =
        database.faceProfileDao().getAllProfiles()
    val faceEvents: Flow<List<FaceRecognitionEventEntity>> =
        database.faceRecognitionDao().getRecentEvents(100)
    val logReports: Flow<List<LogReportEntity>> =
        database.logReportDao().getRecentReports(150)

    val sqlSummary: Flow<SqlTelemetrySummary> = combine(
        database.fingertipMovementDao().getCount(),
        database.faceRecognitionDao().getCount(),
        database.faceProfileDao().getCount(),
        database.logReportDao().getCount(),
        combine(
            database.fingertipMovementDao().getUnsyncedCount(),
            database.faceRecognitionDao().getUnsyncedCount(),
            database.logReportDao().getUnsyncedCount()
        ) { uTip, uFace, uLog -> uTip + uFace + uLog }
    ) { tipCount, faceCount, profileCount, logCount, unsynced ->
        SqlTelemetrySummary(
            fingertipCount = tipCount,
            faceEventCount = faceCount,
            enrolledFaceCount = profileCount,
            logReportCount = logCount,
            unsyncedCount = unsynced
        )
    }

    suspend fun saveMemory(key: String, value: String, category: String = "PREFERENCE"): Long {
        return database.memoryDao().insertMemory(
            MemoryEntity(
                key = key.trim(),
                value = value.trim(),
                category = category,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteMemory(id: Long) {
        database.memoryDao().deleteMemoryById(id)
    }

    suspend fun clearMemories() {
        database.memoryDao().clearAllMemories()
    }

    suspend fun searchMemories(query: String): List<MemoryEntity> {
        return database.memoryDao().searchMemories(query)
    }

    suspend fun addNote(title: String, content: String, tag: String = "LOG"): Long {
        return database.noteDao().insertNote(
            NoteEntity(
                title = title.trim(),
                content = content.trim(),
                tag = tag,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteNote(id: Long) {
        database.noteDao().deleteNoteById(id)
    }

    suspend fun addReminder(title: String, dueTime: Long, priority: String = "NORMAL"): Long {
        return database.reminderDao().insertReminder(
            ReminderEntity(
                title = title.trim(),
                dueTime = dueTime,
                isCompleted = false,
                priority = priority
            )
        )
    }

    suspend fun toggleReminder(id: Long, isCompleted: Boolean) {
        database.reminderDao().setCompleted(id, isCompleted)
    }

    suspend fun deleteReminder(id: Long) {
        database.reminderDao().deleteReminderById(id)
    }

    suspend fun logAction(action: String, details: String, status: String = "EXECUTED") {
        database.auditLogDao().insertLog(
            AuditLogEntity(
                action = action,
                details = details,
                status = status,
                timestamp = System.currentTimeMillis()
            )
        )
        recordLogReport(
            category = "SYSTEM",
            severity = if (status == "ABORTED") "WARN" else "INFO",
            title = action,
            summary = details,
            metricsJson = JSONObject().put("status", status).toString()
        )
    }

    suspend fun recordFingertipMovement(
        sessionId: String,
        output: HandTrackingOutput
    ): Long {
        val handCount = if (output.secondaryLandmarks.isNotEmpty()) 2 else if (output.handDetected) 1 else 0
        return database.fingertipMovementDao().insertMovement(
            FingertipMovementEntity(
                sessionId = sessionId,
                gesture = output.gesture.name,
                fingertipX = output.fingertipX,
                fingertipY = output.fingertipY,
                offsetX = output.targetOffsetX,
                offsetY = output.targetOffsetY,
                yawDeg = output.targetYawDeg,
                pitchDeg = output.targetPitchDeg,
                zoomScale = output.targetZoom,
                handCount = handCount,
                syncedToCloud = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun enrollFaceProfile(
        personName: String,
        role: String = "AUTHORIZED",
        descriptor: List<Float>
    ): Long {
        val cleanName = personName.trim().ifBlank { "Operator" }
        val id = database.faceProfileDao().insertProfile(
            FaceProfileEntity(
                personName = cleanName,
                role = role.trim().ifBlank { "AUTHORIZED" },
                signatureJson = FaceSignatureMatcher.serializeDescriptor(descriptor),
                enrolledAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis()
            )
        )
        recordLogReport(
            category = "FACE_RECOGNITION",
            severity = "INFO",
            title = "Face Profile Enrolled",
            summary = "Enrolled facial biometric signature for \"$cleanName\" ($role).",
            metricsJson = JSONObject()
                .put("profileId", id)
                .put("personName", cleanName)
                .put("dimensions", descriptor.size)
                .toString()
        )
        return id
    }

    suspend fun deleteFaceProfile(id: Long) {
        database.faceProfileDao().deleteProfileById(id)
    }

    suspend fun recordFaceRecognitionEvent(output: FaceRecognitionOutput): Long {
        val now = System.currentTimeMillis()
        if (output.matchedProfileId != null) {
            database.faceProfileDao().updateLastSeen(output.matchedProfileId, now)
        }
        val boxText = output.normalizedBox?.let { b ->
            String.format(Locale.US, "[%.2f,%.2f,%.2f,%.2f]", b.left, b.top, b.right, b.bottom)
        } ?: ""
        return database.faceRecognitionDao().insertEvent(
            FaceRecognitionEventEntity(
                matchedProfileId = output.matchedProfileId,
                recognizedLabel = output.recognizedLabel,
                confidence = output.confidence,
                smilingProbability = output.smilingProbability,
                leftEyeOpenProbability = output.leftEyeOpenProbability,
                rightEyeOpenProbability = output.rightEyeOpenProbability,
                headYawDeg = output.headYawDeg,
                headPitchDeg = output.headPitchDeg,
                headRollDeg = output.headRollDeg,
                boundingBoxSummary = boxText,
                status = output.status,
                syncedToCloud = false,
                timestamp = now
            )
        )
    }

    suspend fun recordLogReport(
        category: String,
        severity: String = "INFO",
        title: String,
        summary: String,
        metricsJson: String = "{}"
    ): Long {
        return database.logReportDao().insertReport(
            LogReportEntity(
                category = category.trim().uppercase(Locale.ROOT),
                severity = severity.trim().uppercase(Locale.ROOT),
                title = title.trim(),
                summary = summary.trim(),
                metricsJson = metricsJson,
                syncedToCloud = false,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun generateSnapshotReport(
        handState: HandTrackingOutput,
        faceState: FaceRecognitionOutput,
        engineStatus: String
    ): LogReportEntity {
        val tipTotal = database.fingertipMovementDao().getCount().first()
        val faceTotal = database.faceRecognitionDao().getCount().first()
        val profileTotal = database.faceProfileDao().getCount().first()
        val reportTotal = database.logReportDao().getCount().first()

        val metrics = JSONObject().apply {
            put("fingertipRows", tipTotal)
            put("faceEvents", faceTotal)
            put("enrolledFaces", profileTotal)
            put("existingReports", reportTotal)
            put("activeGesture", handState.gesture.name)
            put("zoomScale", handState.targetZoom.toDouble())
            put("recognizedFace", faceState.recognizedLabel)
            put("faceConfidence", faceState.confidence.toDouble())
            put("engineStatus", engineStatus)
        }

        val summaryText = "SQL Snapshot: $tipTotal fingertip samples, $faceTotal face events ($profileTotal profiles), $reportTotal reports. Face=${faceState.recognizedLabel}, Gesture=${handState.gesture.label}, Link=$engineStatus."
        val report = LogReportEntity(
            category = "SYSTEM",
            severity = "INFO",
            title = "SQL Telemetry Snapshot Report",
            summary = summaryText,
            metricsJson = metrics.toString(),
            syncedToCloud = false,
            timestamp = System.currentTimeMillis()
        )
        val id = database.logReportDao().insertReport(report)
        return report.copy(id = id)
    }

    suspend fun getUnsyncedBatch(): UnsyncedSqlBatch {
        return UnsyncedSqlBatch(
            fingertipMovements = database.fingertipMovementDao().getUnsyncedMovements(100),
            faceEvents = database.faceRecognitionDao().getUnsyncedEvents(100),
            logReports = database.logReportDao().getUnsyncedReports(100)
        )
    }

    suspend fun markBatchSynced(batch: UnsyncedSqlBatch) {
        if (batch.fingertipMovements.isNotEmpty()) {
            database.fingertipMovementDao().markSynced(batch.fingertipMovements.map { it.id })
        }
        if (batch.faceEvents.isNotEmpty()) {
            database.faceRecognitionDao().markSynced(batch.faceEvents.map { it.id })
        }
        if (batch.logReports.isNotEmpty()) {
            database.logReportDao().markSynced(batch.logReports.map { it.id })
        }
    }

    suspend fun clearTelemetryArchives() {
        database.fingertipMovementDao().clearAll()
        database.faceRecognitionDao().clearAll()
        database.logReportDao().clearAll()
    }

    suspend fun buildFormattedSqlReportExport(limit: Int = 25): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        val reports = database.logReportDao().getRecentReports(limit).first()
        val tips = database.fingertipMovementDao().getRecentMovements(limit).first()
        val faces = database.faceRecognitionDao().getRecentEvents(limit).first()
        val sb = StringBuilder()
        sb.appendLine("=== JARVIS X SQL TELEMETRY & LOG REPORT ===")
        sb.appendLine("Generated: ${fmt.format(Date())}")
        sb.appendLine()
        sb.appendLine("[LOG REPORTS (${reports.size} recent)]")
        reports.forEach { r ->
            sb.appendLine("${fmt.format(Date(r.timestamp))} | ${r.severity} | ${r.category} | ${r.title}: ${r.summary}")
        }
        sb.appendLine()
        sb.appendLine("[FACIAL RECOGNITION EVENTS (${faces.size} recent)]")
        faces.forEach { f ->
            sb.appendLine("${fmt.format(Date(f.timestamp))} | ${f.status} | ${f.recognizedLabel} (${String.format(Locale.US, "%.0f%%", f.confidence * 100f)}) | yaw=${String.format(Locale.US, "%.1f", f.headYawDeg)}")
        }
        sb.appendLine()
        sb.appendLine("[FINGERTIP MOVEMENTS (${tips.size} recent)]")
        tips.forEach { t ->
            sb.appendLine("${fmt.format(Date(t.timestamp))} | ${t.gesture} | tip=(${String.format(Locale.US, "%.2f,%.2f", t.fingertipX, t.fingertipY)}) | zoom=${String.format(Locale.US, "%.2fx", t.zoomScale)}")
        }
        return sb.toString()
    }
}
