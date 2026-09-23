package com.example.data.repository

import com.example.data.db.JarvisDatabase
import com.example.data.model.AuditLogEntity
import com.example.data.model.MemoryEntity
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity
import kotlinx.coroutines.flow.Flow

class JarvisRepository(private val database: JarvisDatabase) {
    val memories: Flow<List<MemoryEntity>> = database.memoryDao().getAllMemories()
    val notes: Flow<List<NoteEntity>> = database.noteDao().getAllNotes()
    val reminders: Flow<List<ReminderEntity>> = database.reminderDao().getAllReminders()
    val auditLogs: Flow<List<AuditLogEntity>> = database.auditLogDao().getRecentLogs()

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
    }
}
