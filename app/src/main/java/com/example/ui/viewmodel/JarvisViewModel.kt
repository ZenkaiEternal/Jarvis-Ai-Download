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
import com.example.data.model.FaceProfileEntity
import com.example.data.model.FaceRecognitionEventEntity
import com.example.data.model.FingertipMovementEntity
import com.example.data.model.JarvisVoiceState
import com.example.data.model.LogReportEntity
import com.example.data.model.SqlTelemetrySummary
import com.example.data.repository.JarvisRepository
import com.example.domain.ai.JarvisBrain
import com.example.domain.ai.JarvisEmotion
import com.example.domain.openjarvis.OpenJarvisBridge
import com.example.domain.openjarvis.OpenJarvisConfig
import com.example.domain.openjarvis.OpenJarvisConnectionState
import com.example.domain.openjarvis.OpenJarvisPreferences
import com.example.domain.openjarvis.OpenJarvisServerInfo
import com.example.domain.openjarvis.PinchAction
import com.example.domain.openjarvis.VoiceListeningMode
import com.example.domain.security.ConfirmationRequest
import com.example.domain.security.SecurityEngine
import com.example.domain.service.JarvisBackgroundService
import com.example.domain.tools.WeatherTool
import com.example.domain.vision.FaceProfileSnapshot
import com.example.domain.vision.FaceRecognitionOutput
import com.example.domain.vision.FaceSignatureMatcher
import com.example.domain.vision.HandGestureType
import com.example.domain.vision.HandTrackingManager
import com.example.domain.vision.HandTrackingOutput
import com.example.domain.voice.VoiceEngine
import com.example.domain.voice.WakeEngineBackend
import com.example.ui.components.HoloModelMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

class JarvisViewModel(application: Application) : AndroidViewModel(application) {

    private val database = JarvisDatabase.getInstance(application)
    val repository = JarvisRepository(database)
    private val securityEngine = SecurityEngine(repository)
    private val geminiClient = GeminiClient()
    private val weatherClient = WeatherClient()
    private val weatherTool = WeatherTool(weatherClient)
    private val openJarvisBridge = OpenJarvisBridge()
    private val preferences = OpenJarvisPreferences(application)

    private val visionSessionId: String = UUID.randomUUID().toString().take(8)
    private var lastTipSqlLogMs = 0L
    private var lastLoggedTipX = 0.5f
    private var lastLoggedTipY = 0.5f
    private var lastLoggedZoom = 1.0f
    private var lastLoggedGesture = HandGestureType.NO_HAND

    private var lastFaceSqlLogMs = 0L
    private var lastLoggedFaceStatus = ""
    private var lastLoggedFaceLabel = ""

    val brain = JarvisBrain(
        context = application,
        repository = repository,
        geminiClient = geminiClient,
        securityEngine = securityEngine,
        weatherTool = weatherTool,
        openJarvisBridge = openJarvisBridge
    )

    val openJarvisConfig: StateFlow<OpenJarvisConfig> = preferences.configFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = OpenJarvisConfig()
    )

    val sqlSummary: StateFlow<SqlTelemetrySummary> = repository.sqlSummary.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SqlTelemetrySummary()
    )

    val fingertipMovements: StateFlow<List<FingertipMovementEntity>> = repository.fingertipMovements.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val faceProfiles: StateFlow<List<FaceProfileEntity>> = repository.faceProfiles.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val faceEvents: StateFlow<List<FaceRecognitionEventEntity>> = repository.faceEvents.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val logReports: StateFlow<List<LogReportEntity>> = repository.logReports.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _emotion = MutableStateFlow(JarvisEmotion.CALMNESS)
    val emotion: StateFlow<JarvisEmotion> = _emotion.asStateFlow()

    private val _connectionState = MutableStateFlow(OpenJarvisConnectionState.ON_DEVICE)
    val connectionState: StateFlow<OpenJarvisConnectionState> = _connectionState.asStateFlow()

    private val _serverInfo = MutableStateFlow(OpenJarvisServerInfo(reachable = false))
    val serverInfo: StateFlow<OpenJarvisServerInfo> = _serverInfo.asStateFlow()

    private val _jarvisSubtitle = MutableStateFlow(
        "JARVIS X online. Say \"Jarvis\" or tap the core to command."
    )
    val jarvisSubtitle: StateFlow<String> = _jarvisSubtitle.asStateFlow()

    private val _wakeBurstNonce = MutableStateFlow(0)
    val wakeBurstNonce: StateFlow<Int> = _wakeBurstNonce.asStateFlow()

    private val _resetPoseNonce = MutableStateFlow(0)
    val resetPoseNonce: StateFlow<Int> = _resetPoseNonce.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isDeviceOnline = MutableStateFlow(false)
    val isDeviceOnline: StateFlow<Boolean> = _isDeviceOnline.asStateFlow()

    private val _isHandTrackingEnabled = MutableStateFlow(false)
    val isHandTrackingEnabled: StateFlow<Boolean> = _isHandTrackingEnabled.asStateFlow()

    private val _isConfigSheetOpen = MutableStateFlow(false)
    val isConfigSheetOpen: StateFlow<Boolean> = _isConfigSheetOpen.asStateFlow()

    private val _isSqlSheetOpen = MutableStateFlow(false)
    val isSqlSheetOpen: StateFlow<Boolean> = _isSqlSheetOpen.asStateFlow()

    private val _sqlSyncStatus = MutableStateFlow<String?>(null)
    val sqlSyncStatus: StateFlow<String?> = _sqlSyncStatus.asStateFlow()

    private val _holoModelMode = MutableStateFlow(HoloModelMode.JARVIS_CORE)
    val holoModelMode: StateFlow<HoloModelMode> = _holoModelMode.asStateFlow()

    private val _isWireframeMode = MutableStateFlow(false)
    val isWireframeMode: StateFlow<Boolean> = _isWireframeMode.asStateFlow()

    private val _isExplodedView = MutableStateFlow(false)
    val isExplodedView: StateFlow<Boolean> = _isExplodedView.asStateFlow()

    private val _currentConfirmation = MutableStateFlow<ConfirmationRequest?>(null)
    val currentConfirmation: StateFlow<ConfirmationRequest?> = _currentConfirmation.asStateFlow()

    private val _systemVoiceDialogTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val systemVoiceDialogTrigger: SharedFlow<Unit> = _systemVoiceDialogTrigger.asSharedFlow()

    val voiceEngine: VoiceEngine = VoiceEngine(
        context = application,
        scope = viewModelScope,
        onSpeechRecognized = { text ->
            sendUserMessage(text, fromVoice = true)
        },
        onWakeWordDetected = { remainder ->
            handleWakeWordDetected(remainder)
        },
        onFallbackToSystemVoiceDialog = {
            _systemVoiceDialogTrigger.tryEmit(Unit)
        }
    )

    val handTrackingManager: HandTrackingManager = HandTrackingManager(
        context = application,
        onPinchAction = {
            handlePinchGestureAction()
        },
        onFaceRecognized = { faceOutput ->
            handleFaceRecognitionEvent(faceOutput)
        }
    )

    val handTrackingState: StateFlow<HandTrackingOutput> = handTrackingManager.trackingState
    val faceRecognitionState: StateFlow<FaceRecognitionOutput> = handTrackingManager.faceState
    val handTrackingError: StateFlow<String?> = handTrackingManager.errorState

    val voiceState: StateFlow<JarvisVoiceState> = voiceEngine.voiceState
    val amplitude: StateFlow<Float> = voiceEngine.amplitude
    val liveSpeechTranscript: StateFlow<String> = voiceEngine.liveSpeechTranscript
    val voiceStatusHint: StateFlow<String?> = voiceEngine.voiceStatusHint
    val listeningMode: StateFlow<VoiceListeningMode> = voiceEngine.listeningMode
    val wakeEngineBackend: StateFlow<WakeEngineBackend> = voiceEngine.wakeEngineBackend

    val isBackgroundSentinelRunning: StateFlow<Boolean> = JarvisBackgroundService.isServiceRunning

    init {
        observeNetworkConnectivity(application)
        observeConfigUpdates()
        observeSqlTelemetryStreams()
        probeOpenJarvisServer()

        viewModelScope.launch {
            repository.saveMemory("DESIGNATION", "Operator", "PROFILE")
            repository.recordLogReport(
                category = "SYSTEM",
                severity = "INFO",
                title = "JARVIS X Core Boot",
                summary = "SQL telemetry database initialized (fingertip_movements, facial_recognition_events, face_profiles, log_reports)."
            )
        }
    }

    private fun observeSqlTelemetryStreams() {
        // Keep HandTrackingManager synced with enrolled facial profiles from Room SQL
        viewModelScope.launch {
            repository.faceProfiles.collect { profiles ->
                val snapshots = profiles.mapNotNull { entity ->
                    val vec = FaceSignatureMatcher.parseDescriptor(entity.signatureJson)
                    if (vec.isEmpty()) null
                    else FaceProfileSnapshot(
                        id = entity.id,
                        personName = entity.personName,
                        role = entity.role,
                        descriptor = vec
                    )
                }
                handTrackingManager.updateEnrolledProfiles(snapshots)
            }
        }

        // Capture throttled fingertip movements & gesture milestones in Room SQL
        viewModelScope.launch {
            handTrackingState.collect { state ->
                if (!state.handDetected || state.gesture == HandGestureType.NO_HAND) return@collect
                val now = System.currentTimeMillis()
                val posDelta = abs(state.fingertipX - lastLoggedTipX) + abs(state.fingertipY - lastLoggedTipY)
                val zoomDelta = abs(state.targetZoom - lastLoggedZoom)
                val gestureChanged = state.gesture != lastLoggedGesture

                if ((now - lastTipSqlLogMs >= 240L && (posDelta >= 0.025f || zoomDelta >= 0.06f)) || gestureChanged) {
                    lastTipSqlLogMs = now
                    lastLoggedTipX = state.fingertipX
                    lastLoggedTipY = state.fingertipY
                    lastLoggedZoom = state.targetZoom

                    repository.recordFingertipMovement(visionSessionId, state)

                    if (gestureChanged && (state.gesture == HandGestureType.ZOOM || state.gesture == HandGestureType.PINCH || state.gesture == HandGestureType.CLOSED_FIST)) {
                        val metrics = JSONObject()
                            .put("gesture", state.gesture.name)
                            .put("tipX", state.fingertipX.toDouble())
                            .put("tipY", state.fingertipY.toDouble())
                            .put("zoom", state.targetZoom.toDouble())
                            .toString()
                        repository.recordLogReport(
                            category = "FINGERTIP",
                            severity = "INFO",
                            title = "Gesture Transition: ${state.gesture.label}",
                            summary = String.format(
                                Locale.US,
                                "Fingertip=(%.2f, %.2f) Offset=(%.2f, %.2f) Zoom=%.2fx",
                                state.fingertipX, state.fingertipY, state.targetOffsetX, state.targetOffsetY, state.targetZoom
                            ),
                            metricsJson = metrics
                        )
                    }
                    lastLoggedGesture = state.gesture
                }
            }
        }
    }

    private fun handleFaceRecognitionEvent(output: FaceRecognitionOutput) {
        if (!output.faceDetected) return
        val now = System.currentTimeMillis()
        val stateChanged = output.status != lastLoggedFaceStatus || output.recognizedLabel != lastLoggedFaceLabel
        if (!stateChanged && now - lastFaceSqlLogMs < 4500L) return

        lastFaceSqlLogMs = now
        lastLoggedFaceStatus = output.status
        lastLoggedFaceLabel = output.recognizedLabel

        viewModelScope.launch {
            repository.recordFaceRecognitionEvent(output)
            if (stateChanged) {
                val severity = if (output.status == "UNRECOGNIZED") "ALERT" else "INFO"
                val metrics = JSONObject()
                    .put("status", output.status)
                    .put("label", output.recognizedLabel)
                    .put("confidence", output.confidence.toDouble())
                    .put("headYawDeg", output.headYawDeg.toDouble())
                    .toString()
                repository.recordLogReport(
                    category = "FACE_RECOGNITION",
                    severity = severity,
                    title = "Face Scan: ${output.recognizedLabel}",
                    summary = String.format(
                        Locale.US,
                        "Status=%s (%.0f%%) Head=(%.1f°, %.1f°)",
                        output.status,
                        output.confidence * 100f,
                        output.headYawDeg,
                        output.headPitchDeg
                    ),
                    metricsJson = metrics
                )
            }
        }
    }

    private fun observeConfigUpdates() {
        viewModelScope.launch {
            openJarvisConfig.collect { cfg ->
                voiceEngine.updateSettings(
                    sensitivity = cfg.wakeSensitivity,
                    speakingRate = cfg.speakingRate,
                    picovoiceKey = cfg.picovoiceAccessKey,
                    emotion = _emotion.value
                )
            }
        }
        viewModelScope.launch {
            _emotion.collect { em ->
                val cfg = openJarvisConfig.value
                voiceEngine.updateSettings(
                    sensitivity = cfg.wakeSensitivity,
                    speakingRate = cfg.speakingRate,
                    picovoiceKey = cfg.picovoiceAccessKey,
                    emotion = em
                )
            }
        }
    }

    private fun observeNetworkConnectivity(application: Application) {
        val cm = application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        _isDeviceOnline.value = try {
            val net = cm.activeNetwork
            val caps = cm.getNetworkCapabilities(net)
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (_: Exception) {
            false
        }
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isDeviceOnline.value = true
                    probeOpenJarvisServer()
                }

                override fun onLost(network: Network) {
                    _isDeviceOnline.value = false
                    _connectionState.value = OpenJarvisConnectionState.ON_DEVICE
                }
            })
        } catch (_: Exception) {
        }
    }

    fun probeOpenJarvisServer(overrideConfig: OpenJarvisConfig? = null) {
        viewModelScope.launch {
            val cfg = overrideConfig ?: openJarvisConfig.value
            _connectionState.value = OpenJarvisConnectionState.CHECKING
            val info = openJarvisBridge.probeServer(cfg)
            _serverInfo.value = info
            _connectionState.value = if (info.reachable) {
                OpenJarvisConnectionState.CONNECTED
            } else {
                OpenJarvisConnectionState.ON_DEVICE
            }
        }
    }

    fun saveConfig(next: OpenJarvisConfig) {
        viewModelScope.launch {
            preferences.updateConfig { next }
        }
    }

    fun setConfigSheetOpen(open: Boolean) {
        _isConfigSheetOpen.value = open
    }

    fun setSqlSheetOpen(open: Boolean) {
        _isSqlSheetOpen.value = open
        if (!open) {
            _sqlSyncStatus.value = null
        }
    }

    fun enrollCurrentFace(personName: String) {
        val face = faceRecognitionState.value
        if (!face.faceDetected || face.descriptor.isEmpty()) {
            _sqlSyncStatus.value = "Look into the front camera with Vision enabled before enrolling."
            return
        }
        viewModelScope.launch {
            repository.enrollFaceProfile(
                personName = personName,
                role = "AUTHORIZED",
                descriptor = face.descriptor
            )
            _sqlSyncStatus.value = "Enrolled facial signature for \"${personName.ifBlank { "Operator" }}\"."
        }
    }

    fun deleteFaceProfile(id: Long) {
        viewModelScope.launch {
            repository.deleteFaceProfile(id)
            _sqlSyncStatus.value = "Removed enrolled face profile #$id."
        }
    }

    fun generateSnapshotLogReport() {
        viewModelScope.launch {
            val report = repository.generateSnapshotReport(
                handState = handTrackingState.value,
                faceState = faceRecognitionState.value,
                engineStatus = connectionState.value.badgeLabel
            )
            _sqlSyncStatus.value = "Recorded SQL snapshot #${report.id}."
        }
    }

    fun syncSqlToCompanionServer() {
        viewModelScope.launch {
            _sqlSyncStatus.value = "Syncing SQL telemetry batch..."
            val batch = repository.getUnsyncedBatch()
            if (batch.totalCount == 0) {
                _sqlSyncStatus.value = "All SQL records are already synced."
                return@launch
            }
            val result = openJarvisBridge.syncSqlTelemetry(
                config = openJarvisConfig.value,
                fingertipMovements = batch.fingertipMovements,
                faceEvents = batch.faceEvents,
                logReports = batch.logReports
            )
            if (result.success) {
                repository.markBatchSynced(batch)
                repository.recordLogReport(
                    category = "SQL_SYNC",
                    severity = "INFO",
                    title = "Cloud SQL Telemetry Sync",
                    summary = result.message
                )
            } else {
                repository.recordLogReport(
                    category = "SQL_SYNC",
                    severity = "WARN",
                    title = "Cloud SQL Sync Deferred",
                    summary = "${result.message}. Records retained safely in local Room SQLite."
                )
            }
            _sqlSyncStatus.value = result.message
        }
    }

    fun clearSqlTelemetry() {
        viewModelScope.launch {
            repository.clearTelemetryArchives()
            _sqlSyncStatus.value = "Cleared fingertip, face event, and log report archives."
        }
    }

    suspend fun buildExportReportText(): String {
        return repository.buildFormattedSqlReportExport(30)
    }

    fun setHandTrackingEnabled(enabled: Boolean) {
        _isHandTrackingEnabled.value = enabled
        if (!enabled) {
            handTrackingManager.stop()
        }
        viewModelScope.launch {
            repository.recordLogReport(
                category = "SYSTEM",
                severity = "INFO",
                title = if (enabled) "Vision Sensor Activated" else "Vision Sensor Stopped",
                summary = if (enabled) {
                    "Front-camera fingertip tracking and facial recognition enabled."
                } else {
                    "Front-camera capture stopped."
                }
            )
        }
    }

    fun toggleHandTracking() {
        setHandTrackingEnabled(!_isHandTrackingEnabled.value)
    }

    fun resetReactorPose() {
        handTrackingManager.resetPose()
        _resetPoseNonce.value += 1
        _wakeBurstNonce.value += 1
        _jarvisSubtitle.value = "Floating reactor pose & zoom centered."
    }

    fun setHoloModelMode(mode: HoloModelMode) {
        if (_holoModelMode.value == mode) return
        _holoModelMode.value = mode
        _wakeBurstNonce.value += 1
        _jarvisSubtitle.value = if (mode == HoloModelMode.ENDURANCE) {
            "Loaded Interstellar Endurance centrifuge & Ranger."
        } else {
            "Loaded Procedural Circuit JARVIS Neural Core."
        }
        viewModelScope.launch {
            repository.recordLogReport(
                category = "SYSTEM",
                severity = "INFO",
                title = "3D Model Switched: ${mode.label}",
                summary = "Holographic projection switched to ${mode.label}."
            )
        }
    }

    fun cycleHoloModelMode() {
        val next = if (_holoModelMode.value == HoloModelMode.JARVIS_CORE) {
            HoloModelMode.ENDURANCE
        } else {
            HoloModelMode.JARVIS_CORE
        }
        setHoloModelMode(next)
    }

    fun toggleWireframeMode() {
        val next = !_isWireframeMode.value
        _isWireframeMode.value = next
        _wakeBurstNonce.value += 1
        _jarvisSubtitle.value = if (next) "Wireframe schematic enabled." else "Solid holographic rendering restored."
        viewModelScope.launch {
            repository.recordLogReport(
                category = "SYSTEM",
                severity = "INFO",
                title = if (next) "Wireframe Mode Enabled" else "Wireframe Mode Disabled",
                summary = "3D viewport wireframe toggled to $next."
            )
        }
    }

    fun toggleExplodedView() {
        val next = !_isExplodedView.value
        _isExplodedView.value = next
        _wakeBurstNonce.value += 1
        _jarvisSubtitle.value = if (next) "Exploded assembly inspection active." else "Modules re-docked to nominal assembly."
        viewModelScope.launch {
            repository.recordLogReport(
                category = "SYSTEM",
                severity = "INFO",
                title = if (next) "Exploded View Active" else "Assembly Restored",
                summary = "3D module separation factor set to ${if (next) "1.0" else "0.0"}."
            )
        }
    }

    private fun handlePinchGestureAction() {
        when (openJarvisConfig.value.pinchAction) {
            PinchAction.TOGGLE_LISTENING -> {
                _wakeBurstNonce.value += 1
                toggleVoiceListening()
            }
            PinchAction.CYCLE_EMOTION -> {
                _emotion.value = _emotion.value.next()
                _wakeBurstNonce.value += 1
                _jarvisSubtitle.value = "Expression calibrated: ${_emotion.value.displayLabel}"
            }
            PinchAction.RESET_MODEL_POSE -> {
                resetReactorPose()
            }
            PinchAction.RUN_DIAGNOSTICS -> {
                sendUserMessage("system diagnostics", fromVoice = false)
            }
        }
    }

    private fun handleWakeWordDetected(remainder: String) {
        _wakeBurstNonce.value += 1
        _emotion.value = JarvisEmotion.CURIOSITY
        viewModelScope.launch {
            repository.recordLogReport(
                category = "VOICE_CMD",
                severity = "INFO",
                title = "Wake-Word Triggered",
                summary = if (remainder.isBlank()) "Local trigger \"Jarvis\" detected." else "Trigger + command: \"$remainder\""
            )
            if (remainder.isNotBlank()) {
                sendUserMessage(remainder, fromVoice = true)
            } else if (!_isMuted.value) {
                _jarvisSubtitle.value = "At your service, sir."
                voiceEngine.speak("At your service, sir.") {
                    voiceEngine.startCommandListening()
                }
            } else {
                voiceEngine.startCommandListening()
            }
        }
    }

    fun onMicPermissionGranted() {
        if (openJarvisConfig.value.wakeWordEnabled) {
            voiceEngine.setContinuousListening(true)
        }
    }

    fun toggleWakeStandby() {
        val next = !voiceEngine.isContinuousMode
        viewModelScope.launch {
            preferences.updateConfig { it.copy(wakeWordEnabled = next) }
        }
        voiceEngine.setContinuousListening(next)
        _jarvisSubtitle.value = if (next) {
            "Local \"Jarvis\" wake listener armed."
        } else {
            "Wake listener paused. Tap the core to speak."
        }
    }

    fun toggleBackgroundSentinel() {
        val app = getApplication<Application>()
        if (isBackgroundSentinelRunning.value) {
            JarvisBackgroundService.stop(app)
            voiceEngine.setContinuousListening(openJarvisConfig.value.wakeWordEnabled)
        } else {
            voiceEngine.stopListening()
            JarvisBackgroundService.start(app)
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
        if (_isMuted.value) {
            voiceEngine.stopSpeaking()
        }
    }

    fun toggleVoiceListening() {
        if (listeningMode.value == VoiceListeningMode.COMMAND_ACTIVE && voiceState.value == JarvisVoiceState.LISTENING) {
            voiceEngine.stopListening()
        } else {
            _wakeBurstNonce.value += 1
            _emotion.value = JarvisEmotion.CURIOSITY
            voiceEngine.startCommandListening()
        }
    }

    fun sendUserMessage(text: String, fromVoice: Boolean = false) {
        if (text.isBlank()) return
        val lowerCheck = text.lowercase(Locale.ROOT)
        if (lowerCheck.contains("open sql") || lowerCheck.contains("show sql") ||
            lowerCheck.contains("open log report") || lowerCheck.contains("show log report")
        ) {
            _isSqlSheetOpen.value = true
        }
        if (lowerCheck.contains("endurance") || lowerCheck.contains("interstellar") || lowerCheck.contains("spaceship")) {
            setHoloModelMode(HoloModelMode.ENDURANCE)
        } else if (lowerCheck.contains("jarvis core") || lowerCheck.contains("neural sphere") ||
            lowerCheck.contains("neural matrix") || lowerCheck.contains("arc core")
        ) {
            setHoloModelMode(HoloModelMode.JARVIS_CORE)
        }
        if (lowerCheck.contains("wireframe")) {
            if (!_isWireframeMode.value) toggleWireframeMode()
        } else if (lowerCheck.contains("solid mode") || lowerCheck.contains("disable wireframe")) {
            if (_isWireframeMode.value) toggleWireframeMode()
        }
        if (lowerCheck.contains("disassemble") || lowerCheck.contains("explode")) {
            if (!_isExplodedView.value) toggleExplodedView()
        } else if (lowerCheck.contains("assemble") || lowerCheck.contains("re-dock") || lowerCheck.contains("collapse")) {
            if (_isExplodedView.value) toggleExplodedView()
        }

        voiceEngine.setThinkingState(true)
        _emotion.value = JarvisEmotion.CONCENTRATION
        _jarvisSubtitle.value = "Processing..."

        viewModelScope.launch {
            try {
                val result = brain.processUserQueryWithTelemetry(
                    userInput = text,
                    config = openJarvisConfig.value,
                    currentEmotion = _emotion.value,
                    onStreamDelta = { partial ->
                        _jarvisSubtitle.value = partial
                    },
                    onRequestConfirmation = { req ->
                        _currentConfirmation.value = req
                    }
                )

                _emotion.value = result.emotion
                _connectionState.value = result.connectionState
                _jarvisSubtitle.value = result.message.text

                val lowerInput = text.lowercase(Locale.ROOT).trim()
                val isClosingPhrase = lowerInput == "thank you" || lowerInput == "thanks" ||
                    lowerInput == "stop" || lowerInput == "cancel" ||
                    lowerInput.contains("stand down") || lowerInput.contains("goodbye")

                val shouldAutoFollowUp = fromVoice &&
                    voiceEngine.isContinuedConversation.value &&
                    !isClosingPhrase

                if (!_isMuted.value && result.message.text.isNotBlank()) {
                    voiceEngine.speak(result.message.text) {
                        if (shouldAutoFollowUp) {
                            voiceEngine.startCommandListening()
                        }
                    }
                }
            } catch (e: Exception) {
                _emotion.value = JarvisEmotion.CONCERN
                _jarvisSubtitle.value = "Cognitive anomaly: ${e.localizedMessage ?: "Unknown error"}"
            } finally {
                voiceEngine.setThinkingState(false)
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
            _emotion.value = JarvisEmotion.SATISFACTION
            _jarvisSubtitle.value = "Authorized: ${req.title}"
        }
    }

    fun onAppBackgrounded() {
        if (_isHandTrackingEnabled.value) {
            handTrackingManager.stop()
        }
    }

    override fun onCleared() {
        super.onCleared()
        handTrackingManager.stop()
        voiceEngine.destroy()
    }
}
