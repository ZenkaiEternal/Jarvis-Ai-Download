package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.JarvisVoiceState
import com.example.domain.openjarvis.OpenJarvisConnectionState
import com.example.domain.openjarvis.VoiceListeningMode
import com.example.domain.vision.HandGestureType
import com.example.domain.voice.WakeEngineBackend
import java.util.Locale
import kotlin.math.abs
import com.example.ui.components.ArcReactorView
import com.example.ui.components.HandTrackerPreviewOverlay
import com.example.ui.components.HoloModelMode
import com.example.ui.components.HudConfirmationDialog
import com.example.ui.components.HudWaveform
import com.example.ui.components.Jarvis3DView
import com.example.ui.components.OpenJarvisConfigSheet
import com.example.ui.components.SqlTelemetrySheet
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisHoloWhite
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisVoidDeep
import com.example.ui.viewmodel.JarvisViewModel

@Composable
fun JarvisMainScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val emotion by viewModel.emotion.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val serverInfo by viewModel.serverInfo.collectAsStateWithLifecycle()
    val config by viewModel.openJarvisConfig.collectAsStateWithLifecycle()
    val wakeBackend by viewModel.wakeEngineBackend.collectAsStateWithLifecycle()
    val listeningMode by viewModel.listeningMode.collectAsStateWithLifecycle()
    val liveTranscript by viewModel.liveSpeechTranscript.collectAsStateWithLifecycle()
    val jarvisSubtitle by viewModel.jarvisSubtitle.collectAsStateWithLifecycle()
    val voiceHint by viewModel.voiceStatusHint.collectAsStateWithLifecycle()
    val wakeBurstNonce by viewModel.wakeBurstNonce.collectAsStateWithLifecycle()
    val resetPoseNonce by viewModel.resetPoseNonce.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val isHandTrackingEnabled by viewModel.isHandTrackingEnabled.collectAsStateWithLifecycle()
    val handTrackingState by viewModel.handTrackingState.collectAsStateWithLifecycle()
    val faceState by viewModel.faceRecognitionState.collectAsStateWithLifecycle()
    val handTrackingError by viewModel.handTrackingError.collectAsStateWithLifecycle()
    val isConfigSheetOpen by viewModel.isConfigSheetOpen.collectAsStateWithLifecycle()
    val isSqlSheetOpen by viewModel.isSqlSheetOpen.collectAsStateWithLifecycle()
    val sqlSummary by viewModel.sqlSummary.collectAsStateWithLifecycle()
    val logReports by viewModel.logReports.collectAsStateWithLifecycle()
    val fingertipMovements by viewModel.fingertipMovements.collectAsStateWithLifecycle()
    val faceEvents by viewModel.faceEvents.collectAsStateWithLifecycle()
    val faceProfiles by viewModel.faceProfiles.collectAsStateWithLifecycle()
    val sqlSyncStatus by viewModel.sqlSyncStatus.collectAsStateWithLifecycle()
    val holoModelMode by viewModel.holoModelMode.collectAsStateWithLifecycle()
    val isWireframeMode by viewModel.isWireframeMode.collectAsStateWithLifecycle()
    val isExplodedView by viewModel.isExplodedView.collectAsStateWithLifecycle()
    val isSentinelRunning by viewModel.isBackgroundSentinelRunning.collectAsStateWithLifecycle()
    val confirmationRequest by viewModel.currentConfirmation.collectAsStateWithLifecycle()

    var showTextPrompt by remember { mutableStateOf(false) }
    var textCommandInput by remember { mutableStateOf("") }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicPermissionGranted()
            viewModel.toggleVoiceListening()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.setHandTrackingEnabled(true)
        }
    }

    val handleVoiceClick = {
        val hasMic = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            viewModel.toggleVoiceListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val handleHandTrackingToggle = {
        if (isHandTrackingEnabled) {
            viewModel.setHandTrackingEnabled(false)
        } else {
            val hasCam = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (hasCam) {
                viewModel.setHandTrackingEnabled(true)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        JarvisSurfaceVariant.copy(alpha = 0.42f),
                        JarvisVoidDeep,
                        JarvisBackground
                    )
                )
            )
    ) {
        val isCompactHeight = maxHeight < 660.dp
        val reactorSize = when {
            maxWidth >= 600.dp -> 390.dp
            isCompactHeight -> 250.dp
            else -> 320.dp
        }

        // Subtle futuristic holographic corner reticles
        Canvas(modifier = Modifier.fillMaxSize()) {
            val len = 34f
            val pad = 28f
            val lineColor = JarvisCyan.copy(alpha = 0.28f)
            // Top-left
            drawLine(lineColor, Offset(pad, pad), Offset(pad + len, pad), 2.5f, StrokeCap.Round)
            drawLine(lineColor, Offset(pad, pad), Offset(pad, pad + len), 2.5f, StrokeCap.Round)
            // Top-right
            drawLine(lineColor, Offset(size.width - pad, pad), Offset(size.width - pad - len, pad), 2.5f, StrokeCap.Round)
            drawLine(lineColor, Offset(size.width - pad, pad), Offset(size.width - pad, pad + len), 2.5f, StrokeCap.Round)
            // Bottom-left
            drawLine(lineColor, Offset(pad, size.height - pad), Offset(pad + len, size.height - pad), 2.5f, StrokeCap.Round)
            drawLine(lineColor, Offset(pad, size.height - pad), Offset(pad, size.height - pad - len), 2.5f, StrokeCap.Round)
            // Bottom-right
            drawLine(lineColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad - len, size.height - pad), 2.5f, StrokeCap.Round)
            drawLine(lineColor, Offset(size.width - pad, size.height - pad), Offset(size.width - pad, size.height - pad - len), 2.5f, StrokeCap.Round)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. MINIMAL SYSTEM STATUS INDICATORS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // OpenJarvis Engine Link Pill
                val linkDotColor = when (connectionState) {
                    OpenJarvisConnectionState.CONNECTED -> JarvisGreen
                    OpenJarvisConnectionState.CLOUD_AUX -> JarvisElectricBlue
                    OpenJarvisConnectionState.CHECKING -> JarvisGold
                    OpenJarvisConnectionState.ON_DEVICE -> JarvisCyan
                    OpenJarvisConnectionState.OFFLINE -> JarvisRedAlert
                }
                StatusPill(
                    label = connectionState.badgeLabel,
                    dotColor = linkDotColor,
                    onClick = { viewModel.setConfigSheetOpen(true) },
                    modifier = Modifier.testTag("openjarvis_status_pill")
                )

                // Emotion & Wake/Privacy Indicators
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = emotion.displayLabel,
                        dotColor = emotion.composeColor,
                        onClick = { viewModel.setConfigSheetOpen(true) },
                        modifier = Modifier.testTag("emotion_status_pill")
                    )

                    val wakeActive = wakeBackend != WakeEngineBackend.STANDBY_IDLE ||
                        listeningMode == VoiceListeningMode.COMMAND_ACTIVE
                    StatusPill(
                        label = when {
                            listeningMode == VoiceListeningMode.COMMAND_ACTIVE -> "MIC LIVE"
                            wakeBackend == WakeEngineBackend.PORCUPINE_LOCAL -> "WAKE: JVX"
                            wakeBackend == WakeEngineBackend.LOCAL_VAD_STT -> "WAKE: ARM"
                            else -> "WAKE: OFF"
                        },
                        dotColor = if (wakeActive) JarvisCyanBright else JarvisTextSecondary,
                        onClick = { viewModel.toggleWakeStandby() },
                        modifier = Modifier.testTag("wake_status_pill")
                    )

                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier
                            .size(40.dp)
                            .minimumInteractiveComponentSize()
                            .clip(CircleShape)
                            .background(JarvisSurface.copy(alpha = 0.6f))
                            .border(1.dp, JarvisBorder, CircleShape)
                            .testTag("mute_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = if (isMuted) "Unmute voice" else "Mute voice",
                            tint = if (isMuted) JarvisRedAlert else JarvisCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. CENTRAL STAGE: ANIMATED REACTOR CORE + INTERACTIVE 3D JARVIS MODEL
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                ArcReactorView(
                    voiceState = voiceState,
                    amplitude = amplitude,
                    emotion = emotion,
                    wakeBurstNonce = wakeBurstNonce,
                    size = reactorSize,
                    onClick = handleVoiceClick
                )

                Jarvis3DView(
                    voiceState = voiceState,
                    amplitude = amplitude,
                    emotion = emotion,
                    targetYawDeg = handTrackingState.targetYawDeg,
                    targetPitchDeg = handTrackingState.targetPitchDeg,
                    targetOffsetX = handTrackingState.targetOffsetX,
                    targetOffsetY = handTrackingState.targetOffsetY,
                    targetZoom = handTrackingState.targetZoom,
                    modelMode = holoModelMode,
                    wireframeEnabled = isWireframeMode,
                    explodedFactor = if (isExplodedView) 1f else 0f,
                    soundEnabled = !isMuted,
                    wakeBurstNonce = wakeBurstNonce,
                    resetTouchNonce = resetPoseNonce,
                    onReactorTapped = handleVoiceClick,
                    onPoseReset = { viewModel.resetReactorPose() },
                    modifier = Modifier.fillMaxSize()
                )

                // Live holographic fingertip reticle over the floating stage
                if (isHandTrackingEnabled && handTrackingState.handDetected) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cx = (handTrackingState.fingertipX * size.width).coerceIn(16f, size.width - 16f)
                        val cy = (handTrackingState.fingertipY * size.height).coerceIn(16f, size.height - 16f)
                        val reticleColor = when (handTrackingState.gesture) {
                            HandGestureType.ZOOM -> JarvisGold
                            HandGestureType.CLOSED_FIST -> JarvisRedAlert
                            else -> JarvisCyanBright
                        }
                        drawCircle(
                            color = reticleColor.copy(alpha = 0.22f),
                            radius = 28f,
                            center = Offset(cx, cy)
                        )
                        drawCircle(
                            color = reticleColor.copy(alpha = 0.85f),
                            radius = 18f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.4f)
                        )
                        drawCircle(
                            color = JarvisHoloWhite,
                            radius = 4.5f,
                            center = Offset(cx, cy)
                        )
                    }
                }

                // Compact floating Zoom / Reset Pose + Live Face ID badges at TopStart
                if (isHandTrackingEnabled || abs(handTrackingState.targetZoom - 1.0f) > 0.04f) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatusPill(
                            label = String.format(Locale.US, "%.1fx • CENTER", handTrackingState.targetZoom),
                            dotColor = if (handTrackingState.gesture == HandGestureType.ZOOM) JarvisGold else JarvisCyanBright,
                            onClick = { viewModel.resetReactorPose() },
                            modifier = Modifier.testTag("reset_reactor_pose_button")
                        )
                        if (isHandTrackingEnabled && faceState.faceDetected) {
                            val faceDot = when (faceState.status) {
                                "VERIFIED" -> JarvisGreen
                                "UNRECOGNIZED" -> JarvisRedAlert
                                else -> JarvisCyan
                            }
                            StatusPill(
                                label = "ID: ${faceState.recognizedLabel}",
                                dotColor = faceDot,
                                onClick = { viewModel.setSqlSheetOpen(true) },
                                modifier = Modifier.testTag("live_face_id_pill")
                            )
                        }
                    }
                }

                // Visible front-camera hand tracking + facial recognition PiP overlay when enabled
                this@Column.AnimatedVisibility(
                    visible = isHandTrackingEnabled,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp)
                ) {
                    HandTrackerPreviewOverlay(
                        manager = viewModel.handTrackingManager,
                        trackingState = handTrackingState,
                        faceState = faceState,
                        errorText = handTrackingError,
                        isTrackingEnabled = isHandTrackingEnabled
                    )
                }

                // Compact translucent 3D Holo-Workshop switcher (Model / Wireframe / Explode)
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = "3D: ${holoModelMode.label}",
                        dotColor = if (holoModelMode == HoloModelMode.ENDURANCE) JarvisCyanBright else JarvisGold,
                        onClick = { viewModel.cycleHoloModelMode() },
                        modifier = Modifier.testTag("holo_model_switch_pill")
                    )
                    StatusPill(
                        label = "WIRE",
                        dotColor = if (isWireframeMode) JarvisCyanBright else JarvisTextSecondary,
                        onClick = { viewModel.toggleWireframeMode() },
                        modifier = Modifier.testTag("wireframe_toggle_pill")
                    )
                    StatusPill(
                        label = "EXPLODE",
                        dotColor = if (isExplodedView) JarvisGold else JarvisTextSecondary,
                        onClick = { viewModel.toggleExplodedView() },
                        modifier = Modifier.testTag("explode_toggle_pill")
                    )
                }
            }

            // 3. VOICE INTERACTION SUBTITLE & ACOUSTIC WAVEFORM HUD (NO CHAT BOX)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 580.dp)
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val activeStateLabel = when (voiceState) {
                    JarvisVoiceState.LISTENING -> "LISTENING"
                    JarvisVoiceState.THINKING -> "OPENJARVIS PROCESSING"
                    JarvisVoiceState.SPEAKING -> "RESPONDING"
                    JarvisVoiceState.IDLE -> if (handTrackingState.handDetected) {
                        String.format(
                            Locale.US,
                            "GESTURE // %s (%.1fx)",
                            handTrackingState.gesture.label,
                            handTrackingState.targetZoom
                        )
                    } else {
                        "STANDBY"
                    }
                }

                Text(
                    text = activeStateLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (voiceState == JarvisVoiceState.THINKING) JarvisElectricBlue else JarvisCyanBright,
                    fontWeight = FontWeight.Bold
                )

                HudWaveform(
                    voiceState = voiceState,
                    amplitude = amplitude,
                    height = 30.dp,
                    barCount = 32,
                    modifier = Modifier.fillMaxWidth(0.76f)
                )

                val displayLine = when {
                    voiceState == JarvisVoiceState.LISTENING && liveTranscript.isNotBlank() -> "\"$liveTranscript\""
                    !voiceHint.isNullOrBlank() && voiceState == JarvisVoiceState.IDLE -> voiceHint!!
                    else -> jarvisSubtitle
                }

                Text(
                    text = displayLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = JarvisHoloWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .testTag("jarvis_subtitle_hud")
                )

                // Optional inline text command bar (hidden unless toggled)
                AnimatedVisibility(visible = showTextPrompt) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textCommandInput,
                            onValueChange = { textCommandInput = it },
                            placeholder = { Text("Direct directive...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (textCommandInput.isNotBlank()) {
                                    viewModel.sendUserMessage(textCommandInput, fromVoice = false)
                                    textCommandInput = ""
                                    showTextPrompt = false
                                }
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("direct_command_input"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = JarvisCyan,
                                unfocusedBorderColor = JarvisBorder,
                                focusedContainerColor = JarvisSurface.copy(alpha = 0.8f),
                                unfocusedContainerColor = JarvisSurface.copy(alpha = 0.6f),
                                focusedTextColor = JarvisHoloWhite,
                                unfocusedTextColor = JarvisHoloWhite
                            )
                        )
                        IconButton(
                            onClick = {
                                if (textCommandInput.isNotBlank()) {
                                    viewModel.sendUserMessage(textCommandInput, fromVoice = false)
                                    textCommandInput = ""
                                    showTextPrompt = false
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(JarvisCyan)
                                .testTag("direct_command_send")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send directive",
                                tint = JarvisBackground
                            )
                        }
                    }
                }
            }

            // 4. COMPACT HOLOGRAPHIC CONTROL DOCK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Hand Tracking Toggle
                HoloDockButton(
                    icon = Icons.Default.PanTool,
                    contentDescription = "Toggle camera hand tracking",
                    isActive = isHandTrackingEnabled,
                    activeColor = JarvisGreen,
                    onClick = handleHandTrackingToggle,
                    modifier = Modifier.testTag("hand_tracking_toggle_button")
                )

                // Direct Keyboard Toggle (optional quiet input)
                HoloDockButton(
                    icon = if (showTextPrompt) Icons.Default.Close else Icons.Default.Keyboard,
                    contentDescription = "Toggle directive text input",
                    isActive = showTextPrompt,
                    activeColor = JarvisElectricBlue,
                    onClick = { showTextPrompt = !showTextPrompt },
                    modifier = Modifier.testTag("keyboard_toggle_button")
                )

                // Primary Voice Activation Orb
                val isMicLive = voiceState == JarvisVoiceState.LISTENING
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    if (isMicLive) JarvisHoloWhite else JarvisCyanBright,
                                    if (isMicLive) JarvisCyan else JarvisElectricBlue
                                )
                            )
                        )
                        .border(2.dp, JarvisHoloWhite.copy(alpha = 0.7f), CircleShape)
                        .clickable { handleVoiceClick() }
                        .testTag("primary_voice_orb_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isMicLive) Icons.Default.GraphicEq else Icons.Default.Mic,
                        contentDescription = if (isMicLive) "Stop listening" else "Speak to JARVIS",
                        tint = JarvisBackground,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // SQL Telemetry & Log Reports Archive Trigger
                HoloDockButton(
                    icon = Icons.Default.Storage,
                    contentDescription = "SQL telemetry & log reports",
                    isActive = isSqlSheetOpen,
                    activeColor = JarvisCyanBright,
                    onClick = { viewModel.setSqlSheetOpen(true) },
                    modifier = Modifier.testTag("sql_database_button")
                )

                // OpenJarvis Engine Config Sheet Trigger
                HoloDockButton(
                    icon = Icons.Default.Tune,
                    contentDescription = "OpenJarvis core configuration",
                    isActive = isConfigSheetOpen,
                    activeColor = JarvisCyan,
                    onClick = { viewModel.setConfigSheetOpen(true) },
                    modifier = Modifier.testTag("openjarvis_config_button")
                )
            }
        }

        OpenJarvisConfigSheet(
            isOpen = isConfigSheetOpen,
            config = config,
            serverInfo = serverInfo,
            isSentinelRunning = isSentinelRunning,
            onDismiss = { viewModel.setConfigSheetOpen(false) },
            onSaveConfig = { viewModel.saveConfig(it) },
            onProbeServer = { viewModel.probeOpenJarvisServer(it) },
            onToggleSentinel = { viewModel.toggleBackgroundSentinel() }
        )

        SqlTelemetrySheet(
            isOpen = isSqlSheetOpen,
            summary = sqlSummary,
            logReports = logReports,
            fingertipMovements = fingertipMovements,
            faceEvents = faceEvents,
            faceProfiles = faceProfiles,
            currentFaceState = faceState,
            syncStatusMessage = sqlSyncStatus,
            onDismiss = { viewModel.setSqlSheetOpen(false) },
            onGenerateReport = { viewModel.generateSnapshotLogReport() },
            onSyncToServer = { viewModel.syncSqlToCompanionServer() },
            onEnrollFace = { viewModel.enrollCurrentFace(it) },
            onDeleteFaceProfile = { viewModel.deleteFaceProfile(it) },
            onClearTelemetry = { viewModel.clearSqlTelemetry() },
            onBuildExportReport = { viewModel.buildExportReportText() }
        )

        HudConfirmationDialog(
            request = confirmationRequest,
            onDismiss = { viewModel.dismissConfirmation() },
            onConfirmed = { viewModel.confirmCurrentAction() }
        )
    }
}

@Composable
private fun StatusPill(
    label: String,
    dotColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .background(JarvisSurface.copy(alpha = 0.72f))
            .border(1.dp, dotColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = JarvisHoloWhite,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun HoloDockButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(50.dp)
            .clip(CircleShape)
            .background(
                if (isActive) activeColor.copy(alpha = 0.22f)
                else JarvisSurface.copy(alpha = 0.72f)
            )
            .border(
                width = 1.2.dp,
                color = if (isActive) activeColor else JarvisBorder,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (isActive) activeColor else JarvisTextSecondary,
            modifier = Modifier.size(22.dp)
        )
    }
}
