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
import android.speech.tts.Voice
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

    companion object {
        private const val TAG = "VoiceEngine"
    }

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

    private val _liveSpeechTranscript = MutableStateFlow("")
    val liveSpeechTranscript: StateFlow<String> = _liveSpeechTranscript.asStateFlow()

    private val _isSystemSttAvailable = MutableStateFlow(false)
    val isSystemSttAvailable: StateFlow<Boolean> = _isSystemSttAvailable.asStateFlow()

    var isContinuousMode: Boolean = false
        private set

    private var isSpeakingNow: Boolean = false
    private var speechSimJob: Job? = null
    private var nativeAudioJob: Job? = null
    private var isListeningActive: Boolean = false
    private var audioRecord: AudioRecord? = null

    // Pending speech queue in case speak() is called while TTS is initializing
    private var pendingSpeechText: String? = null
    private var pendingSpeechCallback: (() -> Unit)? = null

    // Audio chime generator for acoustic feedback
    private var toneGenerator: ToneGenerator? = null

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
        } catch (e: Exception) {
            false
        }
        _isSystemSttAvailable.value = hasSpeechRec
        Log.i(TAG, "Speech recognition service available: $hasSpeechRec")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                // Configure audio attributes for voice guidance
                try {
                    val audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                    engine.setAudioAttributes(audioAttributes)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not set AudioAttributes on TTS", e)
                }

                // JARVIS personality: Crisp British English voice if available, fallback gracefully
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
                        Log.i(TAG, "TTS language set to: ${loc.displayName}")
                        break
                    }
                }

                // Select preferred JARVIS voice profile (deep, sophisticated British male timbre)
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
                            Log.i(TAG, "Selected voice: ${preferredVoice.name}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Voice enumeration not available or failed", e)
                }

                engine.setPitch(0.92f) // Slightly deeper, sophisticated
                engine.setSpeechRate(1.04f) // Crisp, precise cadence

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
            Log.i(TAG, "TextToSpeech successfully initialized.")

            // Drain any pending speech that arrived prior to initialization
            pendingSpeechText?.let { queuedText ->
                val callback = pendingSpeechCallback
                pendingSpeechText = null
                pendingSpeechCallback = null
                speak(queuedText, callback)
            }
        } else {
            Log.e(TAG, "TextToSpeech initialization failed with status: $status")
            isTtsInitialized = false
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
                    Log.i(TAG, "SpeechRecognizer created successfully.")
                } else {
                    _isSystemSttAvailable.value = false
                    Log.i(TAG, "System SpeechRecognizer not available. Fallback to Native AudioRecord sentinel.")
                }
            } catch (e: Exception) {
                Log.w(TAG, "SpeechRecognizer init exception, falling back to Native AudioRecord", e)
                _isSystemSttAvailable.value = false
            }
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            isListeningActive = true
            _liveSpeechTranscript.value = ""
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
            Log.w(TAG, "SpeechRecognizer error: $error")
            isListeningActive = false
            _amplitude.value = 0.1f

            if (!isSpeakingNow) {
                _voiceState.value = JarvisVoiceState.IDLE
            }

            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                // Re-initialize clean speech recognizer without killing the subsystem
                initSpeechRecognizer()
            }

            // Fallback to Native AudioRecord ONLY if system has no speech recognition service at all
            if (!_isSystemSttAvailable.value) {
                startNativeAudioHardwareSentinel()
            }

            if (isContinuousMode && !isSpeakingNow) {
                val restartDelay = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> 200L
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> 500L
                    SpeechRecognizer.ERROR_CLIENT -> 600L
                    else -> 700L
                }
                scheduleRestartListening(restartDelay)
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
                _liveSpeechTranscript.value = text
                handleSpokenText(text)
            }

            if (isContinuousMode && !isSpeakingNow) {
                scheduleRestartListening(400)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                _lastSpokenTranscript.value = text
                _liveSpeechTranscript.value = text
                if (containsWakeWord(text) && !hasChimedForCurrentTurn) {
                    hasChimedForCurrentTurn = true
                    playWakeChime()
                }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private var hasChimedForCurrentTurn = false
    private var isAwaitingDirective = false

    fun playAcousticChime() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
        } catch (e: Exception) {
            Log.w(TAG, "Could not play acoustic chime", e)
        }
    }

    /**
     * Iconic 2-tone melodic Google Assistant / JARVIS futuristic wake chime.
     */
    fun playWakeChime() {
        scope.launch(Dispatchers.Default) {
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                delay(85)
                toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 130)
            } catch (e: Exception) {
                Log.w(TAG, "Could not play wake chime", e)
            }
        }
    }

    fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT).trim()
        return lower.contains("wake up") ||
                lower.contains("wake-up") ||
                lower.contains("hey jarvis") ||
                lower.contains("jarvis") ||
                lower.contains("hello jarvis") ||
                lower.contains("hi jarvis") ||
                lower.contains("ok jarvis") ||
                lower.contains("okay jarvis") ||
                lower.contains("yo jarvis") ||
                lower.startsWith("wake")
    }

    fun extractCommandAfterWakeWord(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        val cleaned = lower
            .replace("wake up jarvis", "")
            .replace("wake up", "")
            .replace("wake-up", "")
            .replace("hey jarvis", "")
            .replace("hello jarvis", "")
            .replace("hi jarvis", "")
            .replace("okay jarvis", "")
            .replace("ok jarvis", "")
            .replace("yo jarvis", "")
            .replace("jarvis", "")
            .trim()
            .trimStart(',', ':', '-', ' ', '!', '?')
        return cleaned
    }

    private fun handleSpokenText(text: String) {
        hasChimedForCurrentTurn = false
        val trimmed = text.trim()
        if (trimmed.isBlank()) return

        if (containsWakeWord(trimmed)) {
            val command = extractCommandAfterWakeWord(trimmed)
            if (command.isNotBlank()) {
                isAwaitingDirective = false
                playAcousticChime()
                onSpeechRecognized(command)
            } else {
                isAwaitingDirective = true
                playWakeChime()
                onWakeWordDetected("")
            }
        } else {
            // User spoken text is directly a command/directive!
            isAwaitingDirective = false
            playAcousticChime()
            onSpeechRecognized(trimmed)
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

    /**
     * Opens active listening specifically for the user's directive after wake acknowledgment.
     */
    fun startListeningForCommand() {
        isAwaitingDirective = true
        if (isSpeakingNow) return
        startListening(playChime = true)
    }

    fun startListening(playChime: Boolean = false) {
        if (isSpeakingNow) return
        hasChimedForCurrentTurn = false
        if (playChime) {
            playWakeChime()
        }
        mainHandler.post {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "RECORD_AUDIO permission not granted.")
                return@post
            }

            if (_isSystemSttAvailable.value) {
                if (speechRecognizer == null) {
                    initSpeechRecognizer()
                }
                try {
                    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                    }
                    _voiceState.value = JarvisVoiceState.LISTENING
                    speechRecognizer?.startListening(intent)
                    return@post
                } catch (e: Exception) {
                    Log.w(TAG, "SpeechRecognizer startListening failed, falling back to Native AudioRecord", e)
                }
            }

            // Standalone Fallback for devices without Google Play Services or without SpeechRecognizer
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
                    Log.e(TAG, "AudioRecord initialization failed.")
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
                            if (voicePeakCount >= 3 && now - lastTriggerTime > 3500L) {
                                lastTriggerTime = now
                                voicePeakCount = 0
                                mainHandler.post {
                                    playWakeChime()
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
                Log.e(TAG, "Error in Native AudioRecord Sentinel", e)
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
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop listening", e)
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

        if (spokenText.isBlank()) {
            onFinished?.invoke()
            return
        }

        if (!isTtsInitialized || tts == null) {
            Log.w(TAG, "TTS not yet initialized. Queuing utterance: $spokenText")
            pendingSpeechText = spokenText
            pendingSpeechCallback = onFinished
            // Attempt re-init in case it hadn't started
            initTts()
            return
        }

        val utteranceId = "JARVIS_${System.currentTimeMillis()}"

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
                if (onFinished != null) {
                    mainHandler.post {
                        onFinished.invoke()
                    }
                } else if (isContinuousMode) {
                    scheduleRestartListening(400)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                isSpeakingNow = false
                _voiceState.value = JarvisVoiceState.IDLE
                stopSimulatedAmplitude()
                if (onFinished != null) {
                    mainHandler.post {
                        onFinished.invoke()
                    }
                } else if (isContinuousMode) {
                    scheduleRestartListening(400)
                }
            }
        })

        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
        }

        val result = tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS speak failed with code: $result. Retrying...")
            isSpeakingNow = false
            _voiceState.value = JarvisVoiceState.IDLE
            stopSimulatedAmplitude()
        }
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
                val nextAmp = (0.28f + Math.random().toFloat() * 0.68f).coerceIn(0.1f, 1.0f)
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
            Log.e(TAG, "Error destroying speech recognizer", e)
        }
        speechSimJob?.cancel()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // Ignore
        }
        toneGenerator = null
    }
}
