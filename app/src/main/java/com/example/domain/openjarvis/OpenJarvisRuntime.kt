package com.example.domain.openjarvis

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * OpenJarvis-inspired modular runtime primitives:
 * - Event-driven Event Bus synchronizing Voice Channels, Agents, and HUD state
 * - Local-first execution trace & telemetry log (Intelligence / Engine / Tools / Learning)
 */
enum class VoiceListeningMode {
    IDLE,
    STANDBY_WAKE,
    COMMAND_ACTIVE
}

sealed class JarvisEvent {
    data class VoiceWakeDetected(val phrase: String) : JarvisEvent()
    data class VoiceListeningStarted(val mode: VoiceListeningMode) : JarvisEvent()
    data class VoicePartialTranscript(val text: String) : JarvisEvent()
    data class VoiceCommandRecognized(val command: String) : JarvisEvent()
    data class VoiceError(val errorCode: Int, val message: String, val recoverable: Boolean) : JarvisEvent()
    data class VoiceSpeakingStarted(val text: String) : JarvisEvent()
    data object VoiceSpeakingFinished : JarvisEvent()
    data class TraceRecorded(val trace: OpenJarvisTrace) : JarvisEvent()
}

data class OpenJarvisTrace(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val engine: String, // LOCAL_NLU, LOCAL_TOOL, GEMINI_CLOUD, OFFLINE_MATRIX
    val maskedInput: String,
    val action: String,
    val latencyMs: Long,
    val success: Boolean
)

object OpenJarvisRuntime {
    private val _events = MutableSharedFlow<JarvisEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<JarvisEvent> = _events.asSharedFlow()

    private val _traces = MutableStateFlow<List<OpenJarvisTrace>>(emptyList())
    val traces: StateFlow<List<OpenJarvisTrace>> = _traces.asStateFlow()

    fun emitEvent(event: JarvisEvent) {
        _events.tryEmit(event)
    }

    fun recordTrace(trace: OpenJarvisTrace) {
        _traces.value = (listOf(trace) + _traces.value).take(50)
        _events.tryEmit(JarvisEvent.TraceRecorded(trace))
    }
}
