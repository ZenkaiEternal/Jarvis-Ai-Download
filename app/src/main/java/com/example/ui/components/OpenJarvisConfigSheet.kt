package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.openjarvis.OpenJarvisConfig
import com.example.domain.openjarvis.OpenJarvisInferenceMode
import com.example.domain.openjarvis.OpenJarvisServerInfo
import com.example.domain.openjarvis.PinchAction
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisBorder
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisHoloWhite
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.util.Locale

/**
 * Minimal holographic link configuration sheet for OpenJarvis server URL,
 * model selection, voice parameters, wake sensitivity, and gesture pinch action.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OpenJarvisConfigSheet(
    isOpen: Boolean,
    config: OpenJarvisConfig,
    serverInfo: OpenJarvisServerInfo,
    isSentinelRunning: Boolean,
    onDismiss: () -> Unit,
    onSaveConfig: (OpenJarvisConfig) -> Unit,
    onProbeServer: (OpenJarvisConfig) -> Unit,
    onToggleSentinel: () -> Unit
) {
    if (!isOpen) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var serverUrl by remember(config.serverUrl) { mutableStateOf(config.serverUrl) }
    var apiToken by remember(config.apiToken) { mutableStateOf(config.apiToken) }
    var picovoiceKey by remember(config.picovoiceAccessKey) { mutableStateOf(config.picovoiceAccessKey) }
    var selectedModel by remember(config.selectedModel) { mutableStateOf(config.selectedModel) }
    var inferenceMode by remember(config.inferenceMode) { mutableStateOf(config.inferenceMode) }
    var wakeSensitivity by remember(config.wakeSensitivity) { mutableFloatStateOf(config.wakeSensitivity) }
    var speakingRate by remember(config.speakingRate) { mutableFloatStateOf(config.speakingRate) }
    var pinchAction by remember(config.pinchAction) { mutableStateOf(config.pinchAction) }

    val draftConfig = {
        config.copy(
            serverUrl = serverUrl,
            selectedModel = selectedModel,
            apiToken = apiToken,
            inferenceMode = inferenceMode,
            wakeSensitivity = wakeSensitivity,
            speakingRate = speakingRate,
            pinchAction = pinchAction,
            picovoiceAccessKey = picovoiceKey
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = JarvisSurface,
        contentColor = JarvisTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OPENJARVIS CORE LINK",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisCyanBright,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (serverInfo.reachable) {
                            "Connected • ${serverInfo.availableModels.size} models • ${serverInfo.availableSkills.size} skills"
                        } else {
                            "On-device mode active (${serverInfo.error ?: "backend standby"})"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (serverInfo.reachable) JarvisGreen else JarvisTextSecondary
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close configuration",
                        tint = JarvisTextSecondary
                    )
                }
            }

            // Server endpoint input + test sync
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("OpenJarvis Server URL (`jarvis serve`)") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("openjarvis_url_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = JarvisCyan,
                        unfocusedBorderColor = JarvisBorder,
                        focusedContainerColor = JarvisSurfaceVariant,
                        unfocusedContainerColor = JarvisSurfaceVariant,
                        focusedTextColor = JarvisHoloWhite,
                        unfocusedTextColor = JarvisTextPrimary
                    )
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, JarvisCyan.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                        .clickable { onProbeServer(draftConfig()) }
                        .testTag("probe_openjarvis_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Probe OpenJarvis Server",
                        tint = JarvisCyan
                    )
                }
            }

            // Inference Mode Chips
            Text(
                text = "INFERENCE ROUTING",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OpenJarvisInferenceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = inferenceMode == mode,
                        onClick = { inferenceMode = mode },
                        label = { Text(mode.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JarvisElectricBlue.copy(alpha = 0.25f),
                            selectedLabelColor = JarvisCyanBright
                        )
                    )
                }
            }

            // Available OpenJarvis Models
            val modelChoices = (listOf("auto") + serverInfo.availableModels).distinct()
            if (modelChoices.isNotEmpty()) {
                Text(
                    text = "MODEL SELECTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    modelChoices.forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { selectedModel = model },
                            label = { Text(model, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisHoloWhite
                            )
                        )
                    }
                }
            }

            // Pinch Gesture Action
            Text(
                text = "HAND PINCH ACTION",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextSecondary
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PinchAction.entries.forEach { action ->
                    FilterChip(
                        selected = pinchAction == action,
                        onClick = { pinchAction = action },
                        label = { Text(action.label, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                            selectedLabelColor = JarvisCyanBright
                        )
                    )
                }
            }

            // Wake Sensitivity
            Column {
                Text(
                    text = String.format(Locale.US, "WAKE SENSITIVITY: %.2f", wakeSensitivity),
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
                Slider(
                    value = wakeSensitivity,
                    onValueChange = { wakeSensitivity = it },
                    valueRange = 0.2f..0.95f,
                    colors = SliderDefaults.colors(thumbColor = JarvisCyan, activeTrackColor = JarvisCyan)
                )
            }

            // Voice Speed
            Column {
                Text(
                    text = String.format(Locale.US, "VOICE SPEED: %.2fx", speakingRate),
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
                Slider(
                    value = speakingRate,
                    onValueChange = { speakingRate = it },
                    valueRange = 0.75f..1.45f,
                    colors = SliderDefaults.colors(thumbColor = JarvisElectricBlue, activeTrackColor = JarvisElectricBlue)
                )
            }

            // Optional Credentials (never hardcoded)
            OutlinedTextField(
                value = apiToken,
                onValueChange = { apiToken = it },
                label = { Text("OpenJarvis Bearer Token (Optional)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisBorder,
                    focusedContainerColor = JarvisSurfaceVariant,
                    unfocusedContainerColor = JarvisSurfaceVariant
                )
            )

            OutlinedTextField(
                value = picovoiceKey,
                onValueChange = { picovoiceKey = it },
                label = { Text("Picovoice AccessKey (Optional for Porcupine Wake)") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisBorder,
                    focusedContainerColor = JarvisSurfaceVariant,
                    unfocusedContainerColor = JarvisSurfaceVariant
                )
            )

            // Foreground Background Sentinel Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(JarvisSurfaceVariant)
                    .border(1.dp, JarvisBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FOREGROUND SENTINEL SERVICE",
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisHoloWhite
                    )
                    Text(
                        text = "Persistent Android notification for wake-word listening",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisTextSecondary
                    )
                }
                Switch(
                    checked = isSentinelRunning,
                    onCheckedChange = { onToggleSentinel() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = JarvisBackground,
                        checkedTrackColor = JarvisCyan
                    )
                )
            }

            Button(
                onClick = {
                    val next = draftConfig()
                    onSaveConfig(next)
                    onProbeServer(next)
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("save_openjarvis_config"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = JarvisCyan,
                    contentColor = JarvisBackground
                ),
                border = BorderStroke(1.dp, JarvisHoloWhite.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APPLY & SYNC CORE", fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
