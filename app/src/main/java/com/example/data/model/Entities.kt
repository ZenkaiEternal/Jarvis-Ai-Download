package com.example.data.model

import androidx.room.Entity
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
