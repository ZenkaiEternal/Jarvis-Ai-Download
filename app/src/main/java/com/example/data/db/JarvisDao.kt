package com.example.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.AuditLogEntity
import com.example.data.model.FaceProfileEntity
import com.example.data.model.FaceRecognitionEventEntity
import com.example.data.model.FingertipMovementEntity
import com.example.data.model.LogReportEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE `key` LIKE '%' || :query || '%' OR `value` LIKE '%' || :query || '%'")
    suspend fun searchMemories(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE `key` = :key LIMIT 1")
    suspend fun getMemoryByKey(key: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemory(memory: MemoryEntity): Long

    @Update
    suspend fun updateMemory(memory: MemoryEntity)

    @Delete
    suspend fun deleteMemory(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memories")
    suspend fun clearAllMemories()
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    fun getAllNotes(): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isCompleted ASC, dueTime ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isCompleted = :completed WHERE id = :id")
    suspend fun setCompleted(id: Long, completed: Boolean)

    @Delete
    suspend fun deleteReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteReminderById(id: Long)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestamp DESC LIMIT 50")
    fun getRecentLogs(): Flow<List<AuditLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: AuditLogEntity): Long

    @Query("DELETE FROM audit_logs")
    suspend fun clearLogs()
}

@Dao
interface FingertipMovementDao {
    @Query("SELECT * FROM fingertip_movements ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentMovements(limit: Int = 120): Flow<List<FingertipMovementEntity>>

    @Query("SELECT * FROM fingertip_movements WHERE syncedToCloud = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedMovements(limit: Int = 100): List<FingertipMovementEntity>

    @Query("SELECT COUNT(*) FROM fingertip_movements")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM fingertip_movements WHERE syncedToCloud = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovement(movement: FingertipMovementEntity): Long

    @Query("UPDATE fingertip_movements SET syncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM fingertip_movements")
    suspend fun clearAll()
}

@Dao
interface FaceProfileDao {
    @Query("SELECT * FROM face_profiles ORDER BY enrolledAt DESC")
    fun getAllProfiles(): Flow<List<FaceProfileEntity>>

    @Query("SELECT * FROM face_profiles ORDER BY enrolledAt DESC")
    suspend fun getAllProfilesSnapshot(): List<FaceProfileEntity>

    @Query("SELECT COUNT(*) FROM face_profiles")
    fun getCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: FaceProfileEntity): Long

    @Query("UPDATE face_profiles SET lastSeenAt = :seenAt WHERE id = :id")
    suspend fun updateLastSeen(id: Long, seenAt: Long)

    @Query("DELETE FROM face_profiles WHERE id = :id")
    suspend fun deleteProfileById(id: Long)

    @Query("DELETE FROM face_profiles")
    suspend fun clearAll()
}

@Dao
interface FaceRecognitionDao {
    @Query("SELECT * FROM facial_recognition_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int = 100): Flow<List<FaceRecognitionEventEntity>>

    @Query("SELECT * FROM facial_recognition_events WHERE syncedToCloud = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedEvents(limit: Int = 100): List<FaceRecognitionEventEntity>

    @Query("SELECT COUNT(*) FROM facial_recognition_events")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM facial_recognition_events WHERE syncedToCloud = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: FaceRecognitionEventEntity): Long

    @Query("UPDATE facial_recognition_events SET syncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM facial_recognition_events")
    suspend fun clearAll()
}

@Dao
interface LogReportDao {
    @Query("SELECT * FROM log_reports ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentReports(limit: Int = 150): Flow<List<LogReportEntity>>

    @Query("SELECT * FROM log_reports WHERE category = :category ORDER BY timestamp DESC LIMIT :limit")
    fun getReportsByCategory(category: String, limit: Int = 100): Flow<List<LogReportEntity>>

    @Query("SELECT * FROM log_reports WHERE syncedToCloud = 0 ORDER BY timestamp ASC LIMIT :limit")
    suspend fun getUnsyncedReports(limit: Int = 100): List<LogReportEntity>

    @Query("SELECT COUNT(*) FROM log_reports")
    fun getCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM log_reports WHERE syncedToCloud = 0")
    fun getUnsyncedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: LogReportEntity): Long

    @Query("UPDATE log_reports SET syncedToCloud = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("DELETE FROM log_reports")
    suspend fun clearAll()
}
