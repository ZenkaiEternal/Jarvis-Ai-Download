package com.example.domain.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
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
    private val onWakeWordDetected: () -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsInitialized = false

    private val _voiceState = MutableStateFlow(JarvisVoiceState.IDLE)
    val voiceState: StateFlow<JarvisVoiceState> = _voiceState.asStateFlow()

    private val _amplitude = MutableStateFlow(0.1f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _lastSpokenTranscript = MutableStateFlow("")
    val lastSpokenTranscript: StateFlow<String> = _lastSpokenTranscript.asStateFlow()

    private var speechSimJob: Job? = null

    init {
        tts = TextToSpeech(context.applicationContext, this)
        initSpeechRecognizer()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { engine ->
                // JARVIS styling: Calm, crisp, refined British accent if available, else default
                val ukLocale = Locale.UK
                val result = engine.setLanguage(ukLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    engine.setLanguage(Locale.US)
                }
                engine.setPitch(0.92f) // Slightly deeper, sophisticated
                engine.setSpeechRate(1.05f) // Crisp and precise

                engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _voiceState.value = JarvisVoiceState.SPEAKING
                        startSimulatedAmplitudeForSpeech()
                    }

                    override fun onDone(utteranceId: String?) {
                        _voiceState.value = JarvisVoiceState.IDLE
                        stopSimulatedAmplitude()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _voiceState.value = JarvisVoiceState.IDLE
                        stopSimulatedAmplitude()
                    }
                })
            }
            isTtsInitialized = true
        } else {
            Log.e("VoiceEngine", "TextToSpeech initialization failed: $status")
        }
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        _voiceState.value = JarvisVoiceState.LISTENING
                    }

                    override fun onBeginningOfSpeech() {
                        _voiceState.value = JarvisVoiceState.LISTENING
                    }

                    override fun onRmsChanged(rmsdB: Float) {
                        // Normalize RMS dB typically in [-2, 10] to [0.1, 1.0]
                        val normalized = ((rmsdB + 2f) / 12f).coerceIn(0.1f, 1.0f)
                        _amplitude.value = normalized
                    }

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        _voiceState.value = JarvisVoiceState.THINKING
                    }

                    override fun onError(error: Int) {
                        _voiceState.value = JarvisVoiceState.IDLE
                        _amplitude.value = 0.1f
                    }

                    override fun onResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        _voiceState.value = JarvisVoiceState.IDLE
                        _amplitude.value = 0.1f
                        if (text.isNotBlank()) {
                            _lastSpokenTranscript.value = text
                            checkForWakeWordAndDispatch(text)
                        }
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val text = matches?.firstOrNull() ?: ""
                        if (text.isNotBlank()) {
                            _lastSpokenTranscript.value = text
                            if (containsWakeWord(text)) {
                                onWakeWordDetected()
                            }
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        }
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("hey jarvis") ||
                lower.contains("jarvis") ||
                lower.contains("hello jarvis") ||
                lower.contains("wake up")
    }

    private fun checkForWakeWordAndDispatch(text: String) {
        val lower = text.lowercase(Locale.ROOT)
        var cleaned = text
        if (containsWakeWord(text)) {
            onWakeWordDetected()
            // Clean wake word prefix if user said "Hey Jarvis what time is it"
            cleaned = lower
                .replace("hey jarvis", "")
                .replace("hello jarvis", "")
                .replace("jarvis", "")
                .replace("wake up", "")
                .trim()
            if (cleaned.isBlank()) {
                cleaned = "status report"
            }
        }
        onSpeechRecognized(cleaned)
    }

    fun startListening() {
        stopSpeaking()
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        try {
            _voiceState.value = JarvisVoiceState.LISTENING
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Failed to start listening", e)
            _voiceState.value = JarvisVoiceState.IDLE
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e("VoiceEngine", "Failed to stop listening", e)
        }
        _voiceState.value = JarvisVoiceState.IDLE
    }

    fun speak(text: String) {
        if (!isTtsInitialized || tts == null) return
        stopListening()
        // Strip markdown stars or technical tags for clean speech
        val spokenText = text
            .replace(Regex("[*#_`~]"), "")
            .replace(Regex("\\[.*?\\]"), "")
            .trim()
        val utteranceId = "JARVIS_${System.currentTimeMillis()}"
        tts?.speak(spokenText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stopSpeaking() {
        if (isTtsInitialized) {
            tts?.stop()
        }
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
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        speechSimJob?.cancel()
    }
}
