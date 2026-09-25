package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.api.WeatherClient
import com.example.data.db.JarvisDatabase
import com.example.data.model.ChatMessage
import com.example.data.model.JarvisVoiceState
import com.example.data.model.MemoryEntity
import com.example.data.model.MessageSender
import com.example.data.model.NoteEntity
import com.example.data.model.ReminderEntity
import com.example.data.repository.JarvisRepository
import com.example.domain.ai.JarvisBrain
import com.example.domain.security.AntiVirusScanReport
import com.example.domain.security.ConfirmationRequest
import com.example.domain.security.SecurityEngine
import com.example.domain.service.JarvisBackgroundService
import com.example.domain.tools.JarvisTool
import com.example.domain.tools.WeatherTool
import com.example.domain.voice.VoiceEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getInstance(application)
    val repository = JarvisRepository(database)
    private val securityEngine = SecurityEngine(repository)
    private val geminiClient = GeminiClient()
    private val weatherClient = WeatherClient()
    private val weatherTool = WeatherTool(weatherClient)

    val brain = JarvisBrain(
        context = application,
        repository = repository,
        geminiClient = geminiClient,
        securityEngine = securityEngine,
        weatherTool = weatherTool
    )

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(false)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _currentConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val currentConfirmation: StateFlow<ConfirmationRequest?> = _currentConfirmation.asStateFlow()

    private val _activeTab = MutableStateFlow(0)
    val activeTab: StateFlow<Int> = _activeTab.asStateFlow()

    private val _isDocScannerOpen = MutableStateFlow(false)
    val isDocScannerOpen: StateFlow<Boolean> = _isDocScannerOpen.asStateFlow()

    private val _antiVirusReport = MutableStateFlow<AntiVirusScanReport?>(null)
    val antiVirusReport: StateFlow<AntiVirusScanReport?> = _antiVirusReport.asStateFlow()

    private val _isScanningAntiVirus = MutableStateFlow(false)
    val isScanningAntiVirus: StateFlow<Boolean> = _isScanningAntiVirus.asStateFlow()

    val memories = repository.memories.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val notes = repository.notes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val reminders = repository.reminders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val auditLogs = repository.auditLogs.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val voiceEngine: VoiceEngine = VoiceEngine(
        context = application,
        scope = viewModelScope,
        onSpeechRecognized = { text ->
            onSpeechReceived(text)
        },
        onWakeWordDetected = { remainder ->
            onWakeWordDetected(remainder)
        }
    )

    val liveSpeechTranscript: StateFlow<String> = voiceEngine.liveSpeechTranscript
    val veronicaStatus = brain.veronicaEngine.status

    private val _isVeronicaModalOpen = MutableStateFlow(false)
    val isVeronicaModalOpen: StateFlow<Boolean> = _isVeronicaModalOpen.asStateFlow()

    fun setVeronicaModalOpen(open: Boolean) {
        _isVeronicaModalOpen.value = open
    }

    fun activateVeronicaWithCode(code: String): Boolean {
        val success = brain.veronicaEngine.verifyAndActivate(code)
        if (success) {
            sendUserMessage("activate veronica code $code")
        }
        return success
    }

    fun deployVeronicaCage() {
        sendUserMessage("deploy orbital cage")
    }

    fun repairVeronicaArmor() {
        sendUserMessage("repair hulkbuster armor")
    }

    fun veronicaHeavyStrike() {
        sendUserMessage("hulkbuster heavy strike")
    }

    fun deactivateVeronica() {
        sendUserMessage("deactivate veronica")
    }

    val isBackgroundSentinelRunning: StateFlow<Boolean> = JarvisBackgroundService.isServiceRunning
    val backgroundStatusText: StateFlow<String> = JarvisBackgroundService.lastStatusText
    val isSystemSttAvailable: StateFlow<Boolean> = voiceEngine.isSystemSttAvailable

    private fun onWakeWordDetected(remainder: String) {
        viewModelScope.launch {
            if (!_isMuted.value) {
                if (remainder.isBlank()) {
                    // Spoken "wake up" only: Provide assistant verbal confirmation, then immediately begin listening for the directive!
                    voiceEngine.speak("At your service, sir. What is your directive?") {
                        voiceEngine.startListeningForCommand()
                    }
                } else {
                    sendUserMessage(remainder)
                }
            } else if (remainder.isNotBlank()) {
                sendUserMessage(remainder)
            }
        }
    }

    fun onPermissionGranted() {
        voiceEngine.startListening()
    }

    fun runAntiVirusScan() {
        if (_isScanningAntiVirus.value) return
        _isScanningAntiVirus.value = true
        voiceEngine.speak("Initiating cybernetic anti-virus scan, sir.")

        viewModelScope.launch {
            try {
                val report = brain.antiVirusEngine.runDeepScan()
                _antiVirusReport.value = report

                repository.logAction(
                    action = "ANTIVIRUS_SCAN",
                    details = "Scanned ${report.totalAppsScanned} packages. Threat status: ${report.threatLevel}. Score: ${report.securityScore}%.",
                    status = "COMPLETED"
                )

                val announcement = "Anti-virus scan complete, sir. ${report.totalAppsScanned} packages analyzed. Threat status is ${report.threatLevel}, with an overall security score of ${report.securityScore}%."
                voiceEngine.speak(announcement)
            } catch (e: Exception) {
                voiceEngine.speak("Anti-virus scan completed with warnings, sir.")
            } finally {
                _isScanningAntiVirus.value = false
            }
        }
    }

    fun toggleBackgroundSentinel() {
        val app = getApplication<Application>()
        if (isBackgroundSentinelRunning.value) {
            JarvisBackgroundService.stop(app)
        } else {
            voiceEngine.stopListening()
            JarvisBackgroundService.start(app)
        }
    }

    val voiceState: StateFlow<JarvisVoiceState> = voiceEngine.voiceState
    val amplitude: StateFlow<Float> = voiceEngine.amplitude

    init {
        // Monitor network state
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm != null) {
            val isCurrentlyOnline = try {
                val net = cm.activeNetwork
                val caps = cm.getNetworkCapabilities(net)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            } catch (e: Exception) {
                false
            }
            _isDeviceOnline.value = isCurrentlyOnline

            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        _isDeviceOnline.value = true
                    }
                    override fun onLost(network: Network) {
                        _isDeviceOnline.value = false
                    }
                })
            } catch (e: Exception) {
                // Ignore callback registration failures on restrictive platforms
            }
        }

        // Initial welcome message from JARVIS highlighting offline capability & wake-word
        val initialGreeting = ChatMessage(
            sender = MessageSender.JARVIS,
            text = "Good day, sir. J.A.R.V.I.S. online. Autonomous offline intelligence engaged. Say 'Wake up' or tap the Arc Reactor to give a command.",
            highLevelReasoning = "System startup sequence completed. All peripheral tools mounted. Core memory and offline audio subroutines standing by.",
            actionTaken = "System Diagnostics"
        )
        _messages.value = listOf(initialGreeting)
        voiceEngine.speak(initialGreeting.text)

        // Enable continuous Assistant Standby listening so saying 'Wake up' works immediately
        voiceEngine.setContinuousListening(true)

        // Populate sample tactical memory and mission log if empty
        viewModelScope.launch {
            repository.saveMemory("DESIGNATION", "Operator", "PROFILE")
            repository.saveMemory("PRIORITY_PROJECT", "Mark VII Exoskeleton Framework", "DIRECTIVE")
            repository.addNote("System Architecture", "Quantum neural link operating with dual backup heuristics.", "TACTICAL")
            repository.addReminder("Calibrate orbital telemetry", System.currentTimeMillis() + 7200000L, "HIGH")
        }
    }

    fun selectTab(tabIndex: Int) {
        _activeTab.value = tabIndex
    }

    fun setDocScannerOpen(isOpen: Boolean) {
        _isDocScannerOpen.value = isOpen
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        if (_isMuted.value) {
            voiceEngine.stopSpeaking()
        }
    }

    fun toggleVoiceListening() {
        if (voiceState.value == JarvisVoiceState.LISTENING) {
            voiceEngine.stopListening()
        } else {
            voiceEngine.startListening(playChime = true)
        }
    }

    fun speak(text: String) {
        if (!_isMuted.value) {
            voiceEngine.speak(text)
        }
    }

    private fun onSpeechReceived(text: String) {
        if (text.isNotBlank()) {
            sendUserMessage(text)
        }
    }

    fun sendUserMessage(text: String) {
        if (text.isBlank()) return

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            text = text
        )
        _messages.value = _messages.value + userMsg

        voiceEngine.setThinkingState(true)

        viewModelScope.launch {
            try {
                val responseMsg = brain.processUserQuery(
                    userInput = text,
                    onRequestConfirmation = { req ->
                        _currentConfirmation.value = req
                    }
                )

                _messages.value = _messages.value + responseMsg

                if (!_isMuted.value && responseMsg.text.isNotBlank()) {
                    voiceEngine.speak(responseMsg.text)
                }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "A cognitive anomaly occurred: ${e.localizedMessage ?: "Unknown error"}. Standing by for next instruction.",
                    highLevelReasoning = "Exception caught in neural runtime. Running recovery sequence."
                )
                _messages.value = _messages.value + errorMsg
            } finally {
                voiceEngine.setThinkingState(false)
            }
        }
    }

    fun executeDirectTool(tool: JarvisTool, params: Map<String, String>) {
        viewModelScope.launch {
            voiceEngine.setThinkingState(true)
            val result = tool.execute(getApplication(), params)
            voiceEngine.setThinkingState(false)

            result.directAction?.invoke()

            val msg = ChatMessage(
                sender = MessageSender.JARVIS,
                text = result.summary,
                highLevelReasoning = "Direct tool execution: ${tool.name}",
                actionTaken = tool.name,
                toolResult = result.displayData
            )
            _messages.value = _messages.value + msg

            if (!_isMuted.value) {
                voiceEngine.speak(result.summary)
            }
        }
    }

    fun dismissConfirmation() {
        _currentConfirmation.value?.onCancel?.invoke()
        _currentConfirmation.value = null
    }

    fun confirmCurrentAction() {
        val req = _currentConfirmation.value ?: return
        viewModelScope.launch {
            req.onConfirm()
            _currentConfirmation.value = null
            val confirmedMsg = ChatMessage(
                sender = MessageSender.SYSTEM,
                text = "Protocol authorized and executed: ${req.title}",
                highLevelReasoning = "Security gate confirmed by authorized operator."
            )
            _messages.value = _messages.value + confirmedMsg
        }
    }

    fun saveMemory(key: String, value: String, category: String) {
        viewModelScope.launch {
            repository.saveMemory(key, value, category)
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch {
            repository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        val request = ConfirmationRequest(
            title = "CONFIRM PURGE OF MEMORY VAULT",
            description = "Are you certain you wish to delete all stored user directives and memories? This cannot be undone.",
            onConfirm = {
                repository.clearMemories()
            }
        )
        _currentConfirmation.value = request
    }

    fun addNote(title: String, content: String, tag: String) {
        viewModelScope.launch {
            repository.addNote(title, content, tag)
        }
    }

    fun deleteNote(id: Long) {
        viewModelScope.launch {
            repository.deleteNote(id)
        }
    }

    fun addReminder(title: String, dueTime: Long, priority: String) {
        viewModelScope.launch {
            repository.addReminder(title, dueTime, priority)
        }
    }

    fun toggleReminder(id: Long, completed: Boolean) {
        viewModelScope.launch {
            repository.toggleReminder(id, completed)
        }
    }

    fun deleteReminder(id: Long) {
        viewModelScope.launch {
            repository.deleteReminder(id)
        }
    }

    fun analyzeDocument(text: String) {
        sendUserMessage("Analyze this document: $text")
    }

    override fun onCleared() {
        super.onCleared()
        voiceEngine.destroy()
    }
}
