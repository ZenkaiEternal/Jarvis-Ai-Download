package com.example.domain.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.model.JarvisVoiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSpeechRecognized: (String) -> Unit,
    private val onWakeWordDetected: (String) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _voiceState = MutableStateFlow(JarvisVoiceState.IDLE)
    val voiceState: StateFlow<JarvisVoiceState> = _voiceState.asStateFlow()

    private val _amplitude = MutableStateFlow(0.1f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _lastSpokenTranscript = MutableStateFlow("")
    val lastSpokenTranscript: StateFlow<String> = _lastSpokenTranscript.asStateFlow()

    private val _isSystemSttAvailable = MutableStateFlow(false)
    val isSystemSttAvailable: StateFlow<Boolean> = _isSystemSttAvailable.asStateFlow()

    var isContinuousMode: Boolean = false
        private set

    private var isSpeakingNow: Boolean = false
    private var speechSimJob: Job? = null
    private var nativeAudioJob: Job? = null
    private var isListeningActive: Boolean = false
    private var audioRecord: AudioRecord? = null

    init {
        checkSystemCapabilities()
        tts = TextToSpeech(context.applicationContext, this)
        initSpeechRecognizer()
    }

    private fun checkSystemCapabilities() {
        val hasSpeechRec = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (e: Exception) {
            false
        }
        _isSystemSttAvailable.value = hasSpeechRec
        Log.i("VoiceEngine", "Speech recognition service available: $hasSpeechRec (Google Play Services independent)")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                // JARVIS styling: Crisp British accent if available, otherwise default US/system
                val ukLocale = Locale.UK
                val result = engine.setLanguage(ukLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.getDefault())
                }
                engine.setPitch(0.92f) // Slightly deeper, sophisticated
                engine.setSpeechRate(1.05f) // Crisp and precise

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        isSpeakingNow = true
                        _voiceState.value = JarvisVoiceState.SPEAKING
                        startSimulatedAmplitudeForSpeech()
                    }

                    override fun onDone(utteranceId: String?) {
                        isSpeakingNow = false
                        _voiceState.value = JarvisVoiceState.IDLE
                        stopSimulatedAmplitude()
                        if (isContinuousMode) {
                            scheduleRestartListening(400)
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        isSpeakingNow = false
                        _voiceState.value = JarvisVoiceState.IDLE
                        stopSimulatedAmplitude()
                        if (isContinuousMode) {
                            scheduleRestartListening(400)
                        }
                    }
                })
            }
            isTtsInitialized = true
        } else {
            Log.e("VoiceEngine", "TextToSpeech initialization status: $status")
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer?.destroy()
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createRecognitionListener())
                    }
                    _isSystemSttAvailable.value = true
                } else {
                    _isSystemSttAvailable.value = false
                    Log.i("VoiceEngine", "SpeechRecognizer service not found. Using Native AudioRecord Sentinel.")
                }
            } catch (e: Exception) {
                Log.w("VoiceEngine", "SpeechRecognizer init exception, falling back to Native AudioRecord", e)
                _isSystemSttAvailable.value = false
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListeningActive = true
            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.LISTENING
            }
        }

        override fun onBeginningOfSpeech() {
            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.LISTENING
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (_voiceState.value == JarvisVoiceState.LISTENING) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                _amplitude.value = normalized
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isListeningActive = false
            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.THINKING
            }
        }

        override fun onError(error: Int) {
            isListeningActive = false
            _amplitude.value = 0.1f
            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.IDLE
            }
            // If SpeechRecognizer failed due to client or server error (e.g. no GMS), fall back to Native AudioRecord
            if (!_isSystemSttAvailable.value || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER) {
                startNativeAudioHardwareSentinel()
            }
            if (isContinuousMode && !isSpeakingNow) {
                scheduleRestartListening(if (error == SpeechRecognizer.ERROR_NO_MATCH) 250 else 600)
            }
        }

        override fun onResults(results: Bundle?) {
            isListeningActive = false
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            _amplitude.value = 0.1f
            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.IDLE
            }

            if (text.isNotBlank()) {
                _lastSpokenTranscript.value = text
                handleSpokenText(text)
            }

            if (isContinuousMode && !isSpeakingNow) {
                scheduleRestartListening(300)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                _lastSpokenTranscript.value = text
                if (containsWakeWord(text)) {
                    val remainder = extractCommandAfterWakeWord(text)
                    onWakeWordDetected(remainder)
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("hey jarvis") ||
                lower.contains("jarvis") ||
                lower.contains("hello jarvis") ||
                lower.contains("hi jarvis") ||
                lower.contains("ok jarvis") ||
                lower.contains("okay jarvis") ||
                lower.contains("yo jarvis") ||
                lower.contains("wake up jarvis") ||
                lower.contains("wake up")
    }

    fun extractCommandAfterWakeWord(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val cleaned = lower
            .replace("hey jarvis", "")
            .replace("hello jarvis", "")
            .replace("hi jarvis", "")
            .replace("okay jarvis", "")
            .replace("ok jarvis", "")
            .replace("yo jarvis", "")
            .replace("wake up jarvis", "")
            .replace("wake up", "")
            .replace("jarvis", "")
            .trim()
            .trimStart(',', ':', '-', ' ')
        return cleaned
    }

    private fun handleSpokenText(text: String) {
        if (containsWakeWord(text)) {
            val command = extractCommandAfterWakeWord(text)
            onWakeWordDetected(command)
            if (command.isNotBlank()) {
                onSpeechRecognized(command)
            }
        } else {
            onSpeechRecognized(text)
        }
    }

    fun setContinuousListening(enabled: Boolean) {
        isContinuousMode = enabled
        if (enabled) {
            startListening()
        } else {
            stopListening()
        }
    }

    fun startListening() {
        if (isSpeakingNow) return
        mainHandler.post {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w("VoiceEngine", "RECORD_AUDIO permission not granted.")
                return@post
            }

            if (_isSystemSttAvailable.value && speechRecognizer != null) {
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
                    }
                    _voiceState.value = JarvisVoiceState.LISTENING
                    speechRecognizer?.startListening(intent)
                    return@post
                } catch (e: Exception) {
                    Log.w("VoiceEngine", "SpeechRecognizer failed, falling back to Native AudioRecord", e)
                }
            }

            // Fallback for devices without Google Play Services or without SpeechRecognizer
            startNativeAudioHardwareSentinel()
        }
    }

    /**
     * Native AudioRecord Hardware Sentinel.
     * Operates completely independently of Google Play Services and third-party frameworks.
     * Continuously analyzes PCM audio samples directly from the Linux ALSA/Android HAL microphone,
     * drives the holographic HUD waveform in real time, and detects voice acoustic energy bursts.
     */
    private fun startNativeAudioHardwareSentinel() {
        stopNativeAudioHardwareSentinel()
        if (isSpeakingNow) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        nativeAudioJob = scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val channelConfig = AudioFormat.CHANNEL_IN_MONO
                val audioFormat = AudioFormat.ENCODING_PCM_16BIT
                val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(2048)

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelConfig,
                    audioFormat,
                    minBufferSize
                )
                audioRecord = recorder

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("VoiceEngine", "AudioRecord initialization failed.")
                    return@launch
                }

                recorder.startRecording()
                _voiceState.value = JarvisVoiceState.LISTENING

                val buffer = ShortArray(minBufferSize / 2)
                var voicePeakCount = 0
                var lastTriggerTime = 0L

                while (isActive && !isSpeakingNow) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = Math.sqrt(sum / read)
                        val normalized = (rms / 2500.0).toFloat().coerceIn(0.08f, 1.0f)
                        _amplitude.value = normalized

                        // Voice activity detection (VAD) threshold
                        if (normalized > 0.42f) {
                            voicePeakCount++
                            val now = System.currentTimeMillis()
                            // If user speaks (sustained vocal energy) and cooldown passed
                            if (voicePeakCount >= 3 && now - lastTriggerTime > 4000L) {
                                lastTriggerTime = now
                                voicePeakCount = 0
                                mainHandler.post {
                                    onWakeWordDetected("")
                                }
                            }
                        } else if (voicePeakCount > 0) {
                            voicePeakCount--
                        }
                    }
                    delay(30)
                }

                try {
                    recorder.stop()
                    recorder.release()
                } catch (e: Exception) {
                    // Ignore release errors
                }
            } catch (e: Exception) {
                Log.e("VoiceEngine", "Error in Native AudioRecord Sentinel", e)
            } finally {
                if (!isSpeakingNow && !isContinuousMode) {
                    _voiceState.value = JarvisVoiceState.IDLE
                    _amplitude.value = 0.1f
                }
            }
        }
    }

    private fun stopNativeAudioHardwareSentinel() {
        nativeAudioJob?.cancel()
        nativeAudioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.e("VoiceEngine", "Failed to stop listening", e)
            }
            stopNativeAudioHardwareSentinel()
            isListeningActive = false
            _voiceState.value = JarvisVoiceState.IDLE
        }
    }

    private fun scheduleRestartListening(delayMillis: Long) {
        mainHandler.removeCallbacksAndMessages(null)
        mainHandler.postDelayed({
            if (isContinuousMode && !isSpeakingNow) {
                startListening()
            }
        }, delayMillis)
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        stopListening()
        val spokenText = text
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()

        if (!isTtsInitialized || tts == null) {
            // Graceful fallback when on-device TTS is not installed or initializing
            Log.w("VoiceEngine", "TTS not initialized, simulating visual speech output.")
            isSpeakingNow = true
            _voiceState.value = JarvisVoiceState.SPEAKING
            startSimulatedAmplitudeForSpeech()
            scope.launch(Dispatchers.Main) {
                // Approximate reading duration
                val readingDelay = (spokenText.length * 40L).coerceIn(1000L, 4000L)
                delay(readingDelay)
                isSpeakingNow = false
                _voiceState.value = JarvisVoiceState.IDLE
                stopSimulatedAmplitude()
                onFinished?.invoke()
                if (isContinuousMode) {
                    scheduleRestartListening(400)
                }
            }
            return
        }

        val utteranceId = "JARVIS_${System.currentTimeMillis()}"

        if (onFinished != null) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    isSpeakingNow = true
                    _voiceState.value = JarvisVoiceState.SPEAKING
                    startSimulatedAmplitudeForSpeech()
                }

                override fun onDone(id: String?) {
                    isSpeakingNow = false
                    _voiceState.value = JarvisVoiceState.IDLE
                    stopSimulatedAmplitude()
                    onFinished()
                    if (isContinuousMode) {
                        scheduleRestartListening(400)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    isSpeakingNow = false
                    _voiceState.value = JarvisVoiceState.IDLE
                    stopSimulatedAmplitude()
                    onFinished()
                    if (isContinuousMode) {
                        scheduleRestartListening(400)
                    }
                }
            })
        }

        tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
        }
        isSpeakingNow = false
        _voiceState.value = JarvisVoiceState.IDLE
        stopSimulatedAmplitude()
    }

    fun setThinkingState(isThinking: Boolean) {
        if (isThinking) {
            _voiceState.value = JarvisVoiceState.THINKING
        } else if (_voiceState.value == JarvisVoiceState.THINKING) {
            _voiceState.value = JarvisVoiceState.IDLE
        }
    }

    private fun startSimulatedAmplitudeForSpeech() {
        speechSimJob?.cancel()
        speechSimJob = scope.launch(Dispatchers.Default) {
            while (isActive && _voiceState.value == JarvisVoiceState.SPEAKING) {
                val nextAmp = (0.25f + Math.random().toFloat() * 0.70f).coerceIn(0.1f, 1.0f)
                _amplitude.value = nextAmp
                delay(60)
            }
            _amplitude.value = 0.1f
        }
    }

    private fun stopSimulatedAmplitude() {
        speechSimJob?.cancel()
        speechSimJob = null
        _amplitude.value = 0.1f
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
        stopNativeAudioHardwareSentinel()
        tts?.stop()
        tts?.shutdown()
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Error destroying speech recognizer", e)
        }
        speechSimJob?.cancel()
    }
}
