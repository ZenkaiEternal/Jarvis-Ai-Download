package com.example.domain.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import ai.picovoice.porcupine.Porcupine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

enum class WakeEngineBackend(val label: String) {
    PORCUPINE_LOCAL("PORCUPINE LOCAL"),
    LOCAL_VAD_STT("LOCAL ACOUSTIC + STT"),
    STANDBY_IDLE("STANDBY")
}

/**
 * Pure-Kotlin helper for wake-word deduplication, sensitivity mapping, and transcript matching.
 */
object WakeWordMatcher {
    private val wakeTokens = listOf(
        "hey jarvis", "jarvis", "jar vis", "jervis", "hello jarvis",
        "ok jarvis", "okay jarvis", "yo jarvis", "wake up jarvis", "wake up"
    )

    fun containsJarvisTrigger(transcript: String): Boolean {
        val normalized = transcript.lowercase().trim()
        if (normalized.isBlank()) return false
        return wakeTokens.any { normalized.contains(it) }
    }

    fun extractRemainingCommand(transcript: String): String {
        var cleaned = transcript.lowercase().trim()
        for (token in wakeTokens.sortedByDescending { it.length }) {
            cleaned = cleaned.replace(token, " ")
        }
        return cleaned.trim().trimStart(',', ':', '-', '!', '?', ' ')
    }

    fun sensitivityToRmsThreshold(sensitivity: Float): Double {
        val s = sensitivity.coerceIn(0.2f, 0.95f)
        // Higher sensitivity = lower RMS trigger threshold
        return 1450.0 - (s * 1050.0)
    }

    fun canActivateWake(nowMs: Long, lastActivationMs: Long, cooldownMs: Long = 2200L): Boolean {
        return (nowMs - lastActivationMs) >= cooldownMs
    }
}

/**
 * Local "Jarvis" wake-word detector:
 * - Uses Picovoice Porcupine on-device (`BuiltInKeyword.JARVIS`) when the operator provides an AccessKey.
 * - Falls back seamlessly to zero-key local AudioRecord VAD + on-device SpeechRecognizer keyword spotting,
 *   keeping microphone data local without background cloud streaming.
 */
class LocalWakeWordEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onWakeDetected: (remainingCommand: String) -> Unit,
    private val onAmplitudeSample: (Float) -> Unit,
    private val onNeedLocalSttSweep: () -> Unit
) {
    companion object {
        private const val TAG = "LocalWakeWordEngine"
        private const val SAMPLE_RATE = 16000
    }

    private val _activeBackend = MutableStateFlow(WakeEngineBackend.STANDBY_IDLE)
    val activeBackend: StateFlow<WakeEngineBackend> = _activeBackend.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private var listenerJob: Job? = null
    private var porcupine: Porcupine? = null
    private var audioRecord: AudioRecord? = null
    private var lastActivationMs: Long = 0L
    private var lastSweepMs: Long = 0L

    fun start(
        sensitivity: Float,
        picovoiceAccessKey: String
    ) {
        stop()
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasMic) {
            _activeBackend.value = WakeEngineBackend.STANDBY_IDLE
            return
        }

        val cleanKey = picovoiceAccessKey.trim()
        if (cleanKey.isNotEmpty()) {
            val startedPorcupine = tryStartPorcupine(cleanKey, sensitivity)
            if (startedPorcupine) return
        }

        startLocalAcousticVadLoop(sensitivity)
    }

    private fun tryStartPorcupine(accessKey: String, sensitivity: Float): Boolean {
        return try {
            val engine = Porcupine.Builder()
                .setAccessKey(accessKey)
                .setKeyword(Porcupine.BuiltInKeyword.JARVIS)
                .setSensitivity(sensitivity.coerceIn(0.2f, 0.95f))
                .build(context)
            porcupine = engine
            _activeBackend.value = WakeEngineBackend.PORCUPINE_LOCAL
            _lastError.value = null

            listenerJob = scope.launch(Dispatchers.IO) {
                var recorder: AudioRecord? = null
                try {
                    val frameLen = engine.frameLength
                    val minBuf = AudioRecord.getMinBufferSize(
                        engine.sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(frameLen * 2)

                    recorder = AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        engine.sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBuf
                    )
                    audioRecord = recorder
                    if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                        _lastError.value = "AudioRecord unavailable for Porcupine"
                        startLocalAcousticVadLoop(sensitivity)
                        return@launch
                    }

                    recorder.startRecording()
                    val frame = ShortArray(frameLen)
                    while (isActive) {
                        val read = recorder.read(frame, 0, frameLen)
                        if (read == frameLen) {
                            onAmplitudeSample(computeNormalizedAmplitude(frame, read))
                            val keywordIdx = engine.process(frame)
                            if (keywordIdx >= 0) {
                                val now = System.currentTimeMillis()
                                if (WakeWordMatcher.canActivateWake(now, lastActivationMs)) {
                                    lastActivationMs = now
                                    onWakeDetected("")
                                }
                            }
                        } else {
                            delay(20)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Porcupine loop error, recovering with local VAD+STT", e)
                    _lastError.value = e.localizedMessage
                    startLocalAcousticVadLoop(sensitivity)
                } finally {
                    try {
                        recorder?.stop()
                        recorder?.release()
                    } catch (_: Exception) {
                    }
                    audioRecord = null
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Porcupine init failed (${e.message}); falling back to local VAD + STT", e)
            _lastError.value = "Porcupine key invalid/unavailable; using Local VAD"
            false
        }
    }

    private fun startLocalAcousticVadLoop(sensitivity: Float) {
        listenerJob?.cancel()
        _activeBackend.value = WakeEngineBackend.LOCAL_VAD_STT
        _lastError.value = null

        listenerJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                val frameSize = 512
                val minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(frameSize * 4)

                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf
                )
                audioRecord = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    // Device hardware busy or emulator without mic: gracefully trigger local STT standby
                    onNeedLocalSttSweep()
                    return@launch
                }

                recorder.startRecording()
                val buffer = ShortArray(frameSize)
                val rmsThreshold = WakeWordMatcher.sensitivityToRmsThreshold(sensitivity)
                var speechFrames = 0

                while (isActive) {
                    val read = recorder.read(buffer, 0, frameSize)
                    if (read > 0) {
                        val rms = computeRms(buffer, read)
                        val normAmp = (rms / 2500.0).toFloat().coerceIn(0.06f, 1.0f)
                        onAmplitudeSample(normAmp)

                        val zcr = computeZeroCrossingRate(buffer, read)
                        val isSpeechLike = rms > rmsThreshold && zcr in 0.03f..0.42f
                        if (isSpeechLike) {
                            speechFrames++
                            val now = System.currentTimeMillis()
                            if (speechFrames >= 4 && (now - lastSweepMs) > 2400L) {
                                lastSweepMs = now
                                speechFrames = 0
                                // Pause raw AudioRecord briefly and let on-device SpeechRecognizer verify "Jarvis"
                                try {
                                    recorder.stop()
                                    recorder.release()
                                } catch (_: Exception) {
                                }
                                audioRecord = null
                                onNeedLocalSttSweep()
                                return@launch
                            }
                        } else {
                            speechFrames = (speechFrames - 1).coerceAtLeast(0)
                        }
                    } else {
                        delay(25)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Local acoustic VAD error: ${e.message}")
                _lastError.value = e.localizedMessage
            } finally {
                try {
                    recorder?.stop()
                    recorder?.release()
                } catch (_: Exception) {
                }
                audioRecord = null
            }
        }
    }

    fun notifyExternalTranscriptDetected(transcript: String): Boolean {
        if (!WakeWordMatcher.containsJarvisTrigger(transcript)) return false
        val now = System.currentTimeMillis()
        if (!WakeWordMatcher.canActivateWake(now, lastActivationMs)) return false
        lastActivationMs = now
        val remainder = WakeWordMatcher.extractRemainingCommand(transcript)
        onWakeDetected(remainder)
        return true
    }

    fun stop() {
        listenerJob?.cancel()
        listenerJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
        try {
            porcupine?.delete()
        } catch (_: Exception) {
        }
        porcupine = null
        _activeBackend.value = WakeEngineBackend.STANDBY_IDLE
    }

    private fun computeRms(samples: ShortArray, count: Int): Double {
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i].toDouble()
            sum += s * s
        }
        return sqrt(sum / count.coerceAtLeast(1))
    }

    private fun computeNormalizedAmplitude(samples: ShortArray, count: Int): Float {
        return (computeRms(samples, count) / 2500.0).toFloat().coerceIn(0.06f, 1.0f)
    }

    private fun computeZeroCrossingRate(samples: ShortArray, count: Int): Float {
        if (count < 2) return 0f
        var crossings = 0
        for (i in 1 until count) {
            if ((samples[i - 1] >= 0 && samples[i] < 0) || (samples[i - 1] < 0 && samples[i] >= 0)) {
                crossings++
            }
        }
        return crossings.toFloat() / (count - 1).toFloat()
    }
}
