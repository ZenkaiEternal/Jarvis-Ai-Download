package com.example.domain.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
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
import com.example.domain.ai.JarvisEmotion
import com.example.domain.openjarvis.JarvisEvent
import com.example.domain.openjarvis.OpenJarvisRuntime
import com.example.domain.openjarvis.VoiceListeningMode
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
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onSpeechRecognized: (String) -> Unit,
    private val onWakeWordDetected: (String) -> Unit,
    private val onFallbackToSystemVoiceDialog: () -> Unit = {}
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "VoiceEngine"
    }

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRestartRunnable: Runnable? = null

    private val _voiceState = MutableStateFlow(JarvisVoiceState.IDLE)
    val voiceState: StateFlow<JarvisVoiceState> = _voiceState.asStateFlow()

    private val _listeningMode = MutableStateFlow(VoiceListeningMode.IDLE)
    val listeningMode: StateFlow<VoiceListeningMode> = _listeningMode.asStateFlow()

    private val _isContinuedConversation = MutableStateFlow(true)
    val isContinuedConversation: StateFlow<Boolean> = _isContinuedConversation.asStateFlow()

    private val _voiceStatusHint = MutableStateFlow<String?>(null)
    val voiceStatusHint: StateFlow<String?> = _voiceStatusHint.asStateFlow()

    private val _amplitude = MutableStateFlow(0.1f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _lastSpokenTranscript = MutableStateFlow("")
    val lastSpokenTranscript: StateFlow<String> = _lastSpokenTranscript.asStateFlow()

    private val _liveSpeechTranscript = MutableStateFlow("")
    val liveSpeechTranscript: StateFlow<String> = _liveSpeechTranscript.asStateFlow()

    private val _isSystemSttAvailable = MutableStateFlow(false)
    val isSystemSttAvailable: StateFlow<Boolean> = _isSystemSttAvailable.asStateFlow()

    var isContinuousMode: Boolean = false
        private set

    private var wakeSensitivity: Float = 0.62f
    private var picovoiceAccessKey: String = ""
    private var baseSpeakingRate: Float = 1.04f
    private var activeEmotion: JarvisEmotion = JarvisEmotion.CALMNESS

    private var isSpeakingNow: Boolean = false
    private var isRecognizerActive: Boolean = false
    private var speechSimJob: Job? = null
    private var nativeAudioJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var lastCommandSessionUsedFallback = false
    private var consecutiveErrors = 0
    private var syllablePulseBoost = 0f

    private var pendingSpeechText: String? = null
    private var pendingSpeechCallback: (() -> Unit)? = null
    private var toneGenerator: ToneGenerator? = null

    private val localWakeWordEngine = LocalWakeWordEngine(
        context = context,
        scope = scope,
        onWakeDetected = { remainder ->
            mainHandler.post {
                if (isContinuousMode && !isSpeakingNow && _listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE) {
                    _listeningMode.value = VoiceListeningMode.IDLE
                    playWakeChime()
                    OpenJarvisRuntime.emitEvent(JarvisEvent.VoiceWakeDetected("Jarvis"))
                    onWakeWordDetected(remainder)
                }
            }
        },
        onAmplitudeSample = { amp ->
            if (!isSpeakingNow && _listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE) {
                _amplitude.value = amp
            }
        },
        onNeedLocalSttSweep = {
            mainHandler.post {
                if (isContinuousMode && !isSpeakingNow && _listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE) {
                    _listeningMode.value = VoiceListeningMode.STANDBY_WAKE
                    startRecognizerInternal(isCommandSession = false, preferOffline = true)
                }
            }
        }
    )

    val wakeEngineBackend: StateFlow<WakeEngineBackend> = localWakeWordEngine.activeBackend
    val wakeEngineError: StateFlow<String?> = localWakeWordEngine.lastError

    init {
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator initialization skipped", e)
        }
        checkSystemCapabilities()
        initTts()
        initSpeechRecognizer()
    }

    fun updateSettings(
        sensitivity: Float,
        speakingRate: Float,
        picovoiceKey: String,
        emotion: JarvisEmotion
    ) {
        val wakeConfigChanged = (sensitivity != wakeSensitivity || picovoiceKey != picovoiceAccessKey)
        wakeSensitivity = sensitivity.coerceIn(0.2f, 0.95f)
        baseSpeakingRate = speakingRate.coerceIn(0.75f, 1.45f)
        picovoiceAccessKey = picovoiceKey.trim()
        activeEmotion = emotion

        if (wakeConfigChanged && isContinuousMode && _listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE && !isSpeakingNow) {
            startStandbyWakeListening()
        }
    }

    private fun initTts() {
        mainHandler.post {
            try {
                tts?.stop()
                tts?.shutdown()
                tts = TextToSpeech(context, this)
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing TextToSpeech", e)
            }
        }
    }

    private fun checkSystemCapabilities() {
        val hasSpeechRec = try {
            SpeechRecognizer.isRecognitionAvailable(context)
        } catch (_: Exception) {
            false
        }
        _isSystemSttAvailable.value = hasSpeechRec
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(audioAttributes)
                } catch (_: Exception) {
                }

                val localesToTry = listOf(
                    Locale.UK,
                    Locale("en", "GB"),
                    Locale.ENGLISH,
                    Locale.US,
                    Locale.getDefault()
                )
                for (loc in localesToTry) {
                    val result = engine.setLanguage(loc)
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        break
                    }
                }

                try {
                    val voices = engine.voices
                    if (!voices.isNullOrEmpty()) {
                        val preferredVoice = voices.firstOrNull { v ->
                            val name = v.name.lowercase(Locale.ROOT)
                            (name.contains("en-gb") || name.contains("en_gb")) &&
                                (name.contains("male") || !name.contains("female"))
                        } ?: voices.firstOrNull { v ->
                            v.name.lowercase(Locale.ROOT).contains("en-gb")
                        } ?: voices.firstOrNull { v ->
                            v.locale.language.equals("en", ignoreCase = true)
                        }
                        if (preferredVoice != null) {
                            engine.voice = preferredVoice
                        }
                    }
                } catch (_: Exception) {
                }

                applyProsody(engine)
            }
            isTtsInitialized = true

            pendingSpeechText?.let { queuedText ->
                val callback = pendingSpeechCallback
                pendingSpeechText = null
                pendingSpeechCallback = null
                speak(queuedText, callback)
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed: $status")
            isTtsInitialized = false
        }
    }

    private fun applyProsody(engine: TextToSpeech) {
        val pitch = activeEmotion.ttsPitchMultiplier.coerceIn(0.75f, 1.25f)
        val rate = (baseSpeakingRate * activeEmotion.ttsRateMultiplier).coerceIn(0.70f, 1.60f)
        engine.setPitch(pitch)
        engine.setSpeechRate(rate)
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
                }
            } catch (e: Exception) {
                Log.w(TAG, "SpeechRecognizer init exception", e)
                _isSystemSttAvailable.value = false
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isRecognizerActive = true
            consecutiveErrors = 0
            if (_listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) {
                _liveSpeechTranscript.value = ""
                _voiceStatusHint.value = null
                if (!isSpeakingNow) {
                    _voiceState.value = JarvisVoiceState.LISTENING
                }
            }
            OpenJarvisRuntime.emitEvent(JarvisEvent.VoiceListeningStarted(_listeningMode.value))
        }

        override fun onBeginningOfSpeech() {
            if (!isSpeakingNow && _listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) {
                _voiceState.value = JarvisVoiceState.LISTENING
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            if (_voiceState.value == JarvisVoiceState.LISTENING || _listeningMode.value == VoiceListeningMode.STANDBY_WAKE) {
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.08f, 1.0f)
                _amplitude.value = normalized
            }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            isRecognizerActive = false
            if (!isSpeakingNow && _listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) {
                _voiceState.value = JarvisVoiceState.THINKING
            }
        }

        override fun onError(error: Int) {
            isRecognizerActive = false
            _amplitude.value = 0.08f
            consecutiveErrors++

            val isCommandSession = _listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE
            val errorDesc = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                SpeechRecognizer.ERROR_CLIENT -> "Speech client reset"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
                SpeechRecognizer.ERROR_NETWORK -> "Network speech unavailable"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No speech matched"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Speech service error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Listening timed out"
                else -> "Voice code $error"
            }

            OpenJarvisRuntime.emitEvent(
                JarvisEvent.VoiceError(
                    errorCode = error,
                    message = errorDesc,
                    recoverable = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                )
            )

            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.IDLE
            }

            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                initSpeechRecognizer()
            }

            if (isCommandSession) {
                val isServiceFailure = error == SpeechRecognizer.ERROR_CLIENT ||
                    error == SpeechRecognizer.ERROR_SERVER ||
                    error == SpeechRecognizer.ERROR_NETWORK ||
                    error == SpeechRecognizer.ERROR_AUDIO ||
                    error == 11 || error == 12

                _listeningMode.value = VoiceListeningMode.IDLE
                if (isServiceFailure && !lastCommandSessionUsedFallback) {
                    lastCommandSessionUsedFallback = true
                    _voiceStatusHint.value = "Opening system recognizer..."
                    mainHandler.post { onFallbackToSystemVoiceDialog() }
                } else {
                    _voiceStatusHint.value = "$errorDesc — tap Reactor or pinch to speak."
                }
                if (isContinuousMode && !isSpeakingNow) {
                    scheduleRestartStandbyWake(900)
                }
                return
            }

            if (isContinuousMode && !isSpeakingNow && _listeningMode.value == VoiceListeningMode.STANDBY_WAKE) {
                _listeningMode.value = VoiceListeningMode.IDLE
                scheduleRestartStandbyWake(if (consecutiveErrors >= 4) 1800L else 450L)
            }
        }

        override fun onResults(results: Bundle?) {
            isRecognizerActive = false
            consecutiveErrors = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
            val bestText = selectBestMatch(matches)
            _amplitude.value = 0.08f

            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.IDLE
            }

            if (bestText.isNotBlank()) {
                _lastSpokenTranscript.value = bestText
                _liveSpeechTranscript.value = bestText
                _voiceStatusHint.value = null
                handleSpokenText(bestText)
            } else if (_listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) {
                _listeningMode.value = VoiceListeningMode.IDLE
                _voiceStatusHint.value = "Didn't catch that — tap Reactor to speak."
                if (isContinuousMode && !isSpeakingNow) {
                    scheduleRestartStandbyWake(600)
                }
            } else if (isContinuousMode && !isSpeakingNow) {
                _listeningMode.value = VoiceListeningMode.IDLE
                scheduleRestartStandbyWake(350)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                if (_listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) {
                    _lastSpokenTranscript.value = text
                    _liveSpeechTranscript.value = text
                    OpenJarvisRuntime.emitEvent(JarvisEvent.VoicePartialTranscript(text))
                } else if (_listeningMode.value == VoiceListeningMode.STANDBY_WAKE &&
                    WakeWordMatcher.containsJarvisTrigger(text) && !hasChimedForCurrentTurn
                ) {
                    hasChimedForCurrentTurn = true
                    playWakeChime()
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun selectBestMatch(matches: List<String>): String {
        if (matches.isEmpty()) return ""
        val wakeMatch = matches.firstOrNull { WakeWordMatcher.containsJarvisTrigger(it) }
        if (wakeMatch != null && _listeningMode.value == VoiceListeningMode.STANDBY_WAKE) {
            return wakeMatch
        }
        return matches.firstOrNull { it.isNotBlank() } ?: ""
    }

    private var hasChimedForCurrentTurn = false

    fun playAcousticChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 110)
        } catch (_: Exception) {
        }
    }

    fun playWakeChime() {
        scope.launch(Dispatchers.Default) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 75)
                delay(85)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 120)
            } catch (_: Exception) {
            }
        }
    }

    fun containsWakeWord(text: String): Boolean = WakeWordMatcher.containsJarvisTrigger(text)

    fun extractCommandAfterWakeWord(text: String): String = WakeWordMatcher.extractRemainingCommand(text)

    private fun handleSpokenText(text: String) {
        hasChimedForCurrentTurn = false
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        val currentMode = _listeningMode.value

        if (currentMode == VoiceListeningMode.STANDBY_WAKE) {
            if (WakeWordMatcher.containsJarvisTrigger(trimmed)) {
                _listeningMode.value = VoiceListeningMode.IDLE
                val handled = localWakeWordEngine.notifyExternalTranscriptDetected(trimmed)
                if (!handled && isContinuousMode && !isSpeakingNow) {
                    scheduleRestartStandbyWake(350)
                }
            } else if (isContinuousMode && !isSpeakingNow) {
                _listeningMode.value = VoiceListeningMode.IDLE
                scheduleRestartStandbyWake(300)
            }
        } else {
            val command = if (WakeWordMatcher.containsJarvisTrigger(trimmed)) {
                WakeWordMatcher.extractRemainingCommand(trimmed).ifBlank { trimmed }
            } else {
                trimmed
            }

            _listeningMode.value = VoiceListeningMode.IDLE
            playAcousticChime()
            OpenJarvisRuntime.emitEvent(JarvisEvent.VoiceCommandRecognized(command))
            onSpeechRecognized(command)
        }
    }

    fun setContinuousListening(enabled: Boolean) {
        isContinuousMode = enabled
        if (enabled) {
            if (_listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE && !isSpeakingNow) {
                startStandbyWakeListening()
            }
        } else {
            localWakeWordEngine.stop()
            if (_listeningMode.value == VoiceListeningMode.STANDBY_WAKE) {
                stopListening()
            }
        }
    }

    fun setContinuedConversation(enabled: Boolean) {
        _isContinuedConversation.value = enabled
    }

    fun startStandbyWakeListening() {
        if (isSpeakingNow || _listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        cancelPendingRestart()
        _listeningMode.value = VoiceListeningMode.STANDBY_WAKE
        localWakeWordEngine.start(
            sensitivity = wakeSensitivity,
            picovoiceAccessKey = picovoiceAccessKey
        )
    }

    fun startCommandListening() {
        cancelPendingRestart()
        localWakeWordEngine.stop()
        if (isSpeakingNow) {
            stopSpeaking()
        }
        hasChimedForCurrentTurn = false
        lastCommandSessionUsedFallback = false
        _liveSpeechTranscript.value = ""
        _voiceStatusHint.value = null
        _listeningMode.value = VoiceListeningMode.COMMAND_ACTIVE
        playWakeChime()

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            _voiceStatusHint.value = "Microphone permission required."
            _listeningMode.value = VoiceListeningMode.IDLE
            return
        }

        if (_isSystemSttAvailable.value) {
            startRecognizerInternal(isCommandSession = true, preferOffline = false)
        } else {
            startNativeAudioWaveformMonitor()
            mainHandler.post { onFallbackToSystemVoiceDialog() }
        }
    }

    fun startListeningForCommand() = startCommandListening()

    fun startListening(playChime: Boolean = false) {
        if (playChime) startCommandListening() else startStandbyWakeListening()
    }

    private fun startRecognizerInternal(isCommandSession: Boolean, preferOffline: Boolean) {
        mainHandler.post {
            try {
                if (isRecognizerActive) {
                    try {
                        speechRecognizer?.cancel()
                    } catch (_: Exception) {
                    }
                    isRecognizerActive = false
                }
                if (speechRecognizer == null) {
                    if (SpeechRecognizer.isRecognitionAvailable(context)) {
                        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                            setRecognitionListener(createRecognitionListener())
                        }
                    } else {
                        _isSystemSttAvailable.value = false
                        if (isCommandSession) {
                            startNativeAudioWaveformMonitor()
                            onFallbackToSystemVoiceDialog()
                        }
                        return@post
                    }
                }

                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                    putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                    if (preferOffline) {
                        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                    }
                }

                if (isCommandSession) {
                    _voiceState.value = JarvisVoiceState.LISTENING
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.w(TAG, "SpeechRecognizer startListening failed", e)
                isRecognizerActive = false
                if (isCommandSession) {
                    _listeningMode.value = VoiceListeningMode.IDLE
                    startNativeAudioWaveformMonitor()
                    onFallbackToSystemVoiceDialog()
                }
            }
        }
    }

    private fun startNativeAudioWaveformMonitor() {
        stopNativeAudioWaveformMonitor()
        if (isSpeakingNow) return

        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return

        nativeAudioJob = scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 16000
                val minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(2048)

                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBufferSize
                )
                audioRecord = recorder
                if (recorder.state != AudioRecord.STATE_INITIALIZED) return@launch

                recorder.startRecording()
                _voiceState.value = JarvisVoiceState.LISTENING
                val buffer = ShortArray(minBufferSize / 2)

                while (isActive && !isSpeakingNow && _listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        _amplitude.value = (rms / 2500.0).toFloat().coerceIn(0.08f, 1.0f)
                    }
                    delay(35)
                }

                try {
                    recorder.stop()
                    recorder.release()
                } catch (_: Exception) {
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in native AudioRecord monitor", e)
            } finally {
                if (!isSpeakingNow) {
                    _amplitude.value = 0.08f
                    _voiceState.value = JarvisVoiceState.IDLE
                }
            }
        }
    }

    private fun stopNativeAudioWaveformMonitor() {
        nativeAudioJob?.cancel()
        nativeAudioJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {
        }
        audioRecord = null
    }

    fun stopListening() {
        cancelPendingRestart()
        localWakeWordEngine.stop()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (_: Exception) {
            }
            stopNativeAudioWaveformMonitor()
            isRecognizerActive = false
            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.IDLE
            }
            _listeningMode.value = VoiceListeningMode.IDLE
        }
    }

    private fun cancelPendingRestart() {
        pendingRestartRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingRestartRunnable = null
    }

    private fun scheduleRestartStandbyWake(delayMillis: Long) {
        cancelPendingRestart()
        val runnable = Runnable {
            if (isContinuousMode && !isSpeakingNow && _listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE) {
                startStandbyWakeListening()
            }
        }
        pendingRestartRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    fun speak(text: String, onFinished: (() -> Unit)? = null) {
        stopListening()
        val spokenText = text
            .replace(Regex("\\[EMOTION:[A-Z_]+]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()

        if (spokenText.isBlank()) {
            onFinished?.invoke()
            return
        }

        if (!isTtsInitialized || tts == null) {
            pendingSpeechText = spokenText
            pendingSpeechCallback = onFinished
            initTts()
            return
        }

        tts?.let { applyProsody(it) }
        val utteranceId = "JARVIS_${System.currentTimeMillis()}"

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                isSpeakingNow = true
                _voiceState.value = JarvisVoiceState.SPEAKING
                startSpeechEnvelope()
                OpenJarvisRuntime.emitEvent(JarvisEvent.VoiceSpeakingStarted(spokenText))
            }

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                // Synchronize reactor & 3D core energy pulse with live TTS word cadence
                val wordLen = (end - start).coerceIn(1, 12)
                syllablePulseBoost = (0.35f + wordLen * 0.055f).coerceIn(0.35f, 0.98f)
            }

            override fun onDone(id: String?) {
                finishSpeaking(onFinished)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                finishSpeaking(onFinished)
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val result = tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            finishSpeaking(onFinished)
        }
    }

    private fun finishSpeaking(onFinished: (() -> Unit)?) {
        isSpeakingNow = false
        _voiceState.value = JarvisVoiceState.IDLE
        stopSpeechEnvelope()
        OpenJarvisRuntime.emitEvent(JarvisEvent.VoiceSpeakingFinished)
        if (onFinished != null) {
            mainHandler.post { onFinished.invoke() }
        } else if (isContinuousMode && _listeningMode.value != VoiceListeningMode.COMMAND_ACTIVE) {
            scheduleRestartStandbyWake(450)
        }
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
        }
        isSpeakingNow = false
        if (_voiceState.value == JarvisVoiceState.SPEAKING) {
            _voiceState.value = JarvisVoiceState.IDLE
        }
        stopSpeechEnvelope()
    }

    fun setThinkingState(isThinking: Boolean) {
        if (isThinking) {
            _voiceState.value = JarvisVoiceState.THINKING
        } else if (_voiceState.value == JarvisVoiceState.THINKING) {
            _voiceState.value = JarvisVoiceState.IDLE
        }
    }

    private fun startSpeechEnvelope() {
        speechSimJob?.cancel()
        speechSimJob = scope.launch(Dispatchers.Default) {
            var phase = 0.0
            while (isActive && _voiceState.value == JarvisVoiceState.SPEAKING) {
                phase += 0.35
                syllablePulseBoost *= 0.82f
                val cadence = (0.32f + 0.18f * sin(phase).toFloat() + syllablePulseBoost * 0.52f)
                    .coerceIn(0.15f, 1.0f)
                _amplitude.value = cadence
                delay(45)
            }
            _amplitude.value = 0.08f
        }
    }

    private fun stopSpeechEnvelope() {
        speechSimJob?.cancel()
        speechSimJob = null
        syllablePulseBoost = 0f
        _amplitude.value = 0.08f
    }

    fun destroy() {
        cancelPendingRestart()
        localWakeWordEngine.stop()
        stopNativeAudioWaveformMonitor()
        tts?.stop()
        tts?.shutdown()
        try {
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechSimJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (_: Exception) {
        }
        toneGenerator = null
    }
}
