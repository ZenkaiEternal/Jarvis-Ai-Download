package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.DocumentAnalysisDialog
import com.example.ui.components.HudConfirmationDialog
import com.example.ui.components.HudNavigationBar
import com.example.ui.components.HudTelemetryHeader
import com.example.ui.theme.JarvisBackground
import com.example.ui.viewmodel.JarvisViewModel

@Composable
fun JarvisMainScreen(
    viewModel: JarvisViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val amplitude by viewModel.amplitude.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val confirmationRequest by viewModel.currentConfirmation.collectAsStateWithLifecycle()
    val isDocScannerOpen by viewModel.isDocScannerOpen.collectAsStateWithLifecycle()

    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val auditLogs by viewModel.auditLogs.collectAsStateWithLifecycle()

    // Audio recording permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleVoiceListening()
        }
    }

    val handleVoiceToggle = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.toggleVoiceListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        val isExpandedScreen = maxWidth >= 600.dp

        Scaffold(
            topBar = {
                HudTelemetryHeader(
                    voiceState = voiceState,
                    isMuted = isMuted,
                    onToggleMute = { viewModel.toggleMute() }
                )
            },
            bottomBar = {
                if (!isExpandedScreen) {
                    HudNavigationBar(
                        selectedTab = activeTab,
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            },
            containerColor = JarvisBackground
        ) { paddingValues ->
            if (isExpandedScreen) {
                // Adaptive layout for tablets / desktop
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    HudNavigationBar(
                        selectedTab = activeTab,
                        onTabSelected = { viewModel.selectTab(it) },
                        modifier = Modifier
                            .width(160.dp)
                            .fillMaxHeight()
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        TabContent(
                            activeTab = activeTab,
                            voiceState = voiceState,
                            amplitude = amplitude,
                            messages = messages,
                            memories = memories,
                            notes = notes,
                            reminders = reminders,
                            auditLogs = auditLogs,
                            onToggleVoice = handleVoiceToggle,
                            onSendMessage = { viewModel.sendUserMessage(it) },
                            onSpeakMessage = { viewModel.speak(it) },
                            onOpenDocScanner = { viewModel.setDocScannerOpen(true) },
                            onSaveMemory = { k, v, c -> viewModel.saveMemory(k, v, c) },
                            onDeleteMemory = { viewModel.deleteMemory(it) },
                            onClearAllMemories = { viewModel.clearAllMemories() },
                            onAddNote = { t, c, g -> viewModel.addNote(t, c, g) },
                            onDeleteNote = { viewModel.deleteNote(it) },
                            onAddReminder = { t, d, p -> viewModel.addReminder(t, d, p) },
                            onToggleReminder = { id, c -> viewModel.toggleReminder(id, c) },
                            onDeleteReminder = { viewModel.deleteReminder(it) }
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    TabContent(
                        activeTab = activeTab,
                        voiceState = voiceState,
                        amplitude = amplitude,
                        messages = messages,
                        memories = memories,
                        notes = notes,
                        reminders = reminders,
                        auditLogs = auditLogs,
                        onToggleVoice = handleVoiceToggle,
                        onSendMessage = { viewModel.sendUserMessage(it) },
                        onSpeakMessage = { viewModel.speak(it) },
                        onOpenDocScanner = { viewModel.setDocScannerOpen(true) },
                        onSaveMemory = { k, v, c -> viewModel.saveMemory(k, v, c) },
                        onDeleteMemory = { viewModel.deleteMemory(it) },
                        onClearAllMemories = { viewModel.clearAllMemories() },
                        onAddNote = { t, c, g -> viewModel.addNote(t, c, g) },
                        onDeleteNote = { viewModel.deleteNote(it) },
                        onAddReminder = { t, d, p -> viewModel.addReminder(t, d, p) },
                        onToggleReminder = { id, c -> viewModel.toggleReminder(id, c) },
                        onDeleteReminder = { viewModel.deleteReminder(it) }
                    )
                }
            }
        }

        // Consequential Action Confirmation Modal
        HudConfirmationDialog(
            request = confirmationRequest,
            onDismiss = { viewModel.dismissConfirmation() },
            onConfirmed = { viewModel.confirmCurrentAction() }
        )

        // Document Analysis Dialog
        DocumentAnalysisDialog(
            isOpen = isDocScannerOpen,
            onDismiss = { viewModel.setDocScannerOpen(false) },
            onAnalyze = { text -> viewModel.analyzeDocument(text) }
        )
    }
}

@Composable
private fun TabContent(
    activeTab: Int,
    voiceState: com.example.data.model.JarvisVoiceState,
    amplitude: Float,
    messages: List<com.example.data.model.ChatMessage>,
    memories: List<com.example.data.model.MemoryEntity>,
    notes: List<com.example.data.model.NoteEntity>,
    reminders: List<com.example.data.model.ReminderEntity>,
    auditLogs: List<com.example.data.model.AuditLogEntity>,
    onToggleVoice: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onOpenDocScanner: () -> Unit,
    onSaveMemory: (String, String, String) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    onAddNote: (String, String, String) -> Unit,
    onDeleteNote: (Long) -> Unit,
    onAddReminder: (String, Long, String) -> Unit,
    onToggleReminder: (Long, Boolean) -> Unit,
    onDeleteReminder: (Long) -> Unit
) {
    Crossfade(targetState = activeTab, label = "TabCrossfade") { tab ->
        when (tab) {
            0 -> HudCoreTab(
                voiceState = voiceState,
                amplitude = amplitude,
                messages = messages,
                onToggleVoice = onToggleVoice,
                onSendMessage = onSendMessage,
                onSpeakMessage = onSpeakMessage,
                onOpenDocScanner = onOpenDocScanner
            )
            1 -> TacticalToolsTab(
                onExecuteAction = onSendMessage,
                onOpenDocScanner = onOpenDocScanner
            )
            2 -> MemoryVaultTab(
                memories = memories,
                onSaveMemory = onSaveMemory,
                onDeleteMemory = onDeleteMemory,
                onClearAllMemories = onClearAllMemories
            )
            3 -> MissionLogsTab(
                notes = notes,
                reminders = reminders,
                onAddNote = onAddNote,
                onDeleteNote = onDeleteNote,
                onAddReminder = onAddReminder,
                onToggleReminder = onToggleReminder,
                onDeleteReminder = onDeleteReminder
            )
            4 -> SecurityAuditTab(
                auditLogs = auditLogs
            )
        }
    }
}
