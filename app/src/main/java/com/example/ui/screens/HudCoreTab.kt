package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.JarvisVoiceState
import com.example.ui.components.ArcReactorView
import com.example.ui.components.ChatBubbleHud
import com.example.ui.components.HudWaveform
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceHighlight
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary

@Composable
fun HudCoreTab(
    voiceState: JarvisVoiceState,
    amplitude: Float,
    messages: List<ChatMessage>,
    onToggleVoice: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSpeakMessage: (String) -> Unit,
    onOpenDocScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisBackground)
    ) {
        // Upper HUD Zone: Central Arc Reactor & Waveform
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Arc Reactor Interactive Core
                ArcReactorView(
                    voiceState = voiceState,
                    amplitude = amplitude,
                    size = 180.dp,
                    onClick = onToggleVoice
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Voice Waveform HUD
                HudWaveform(
                    voiceState = voiceState,
                    amplitude = amplitude,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    height = 36.dp
                )

                // Status Prompt
                Text(
                    text = when (voiceState) {
                        JarvisVoiceState.LISTENING -> "LISTENING TO MICROPHONE // 'HEY JARVIS'"
                        JarvisVoiceState.THINKING -> "QUANTUM NEURAL PROCESSING..."
                        JarvisVoiceState.SPEAKING -> "TRANSMITTING VOCAL SYNTHESIS"
                        JarvisVoiceState.IDLE -> "TAP REACTOR OR SAY 'HEY JARVIS' TO ACTIVATE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when (voiceState) {
                        JarvisVoiceState.LISTENING -> JarvisGreen
                        JarvisVoiceState.THINKING -> JarvisGold
                        JarvisVoiceState.SPEAKING -> JarvisCyanBright
                        JarvisVoiceState.IDLE -> JarvisCyan.copy(alpha = 0.8f)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        // Quick Command Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickCommandChip("🌤 Weather Scan", Icons.Default.Cloud) {
                onSendMessage("What's the weather in San Francisco?")
            }
            QuickCommandChip("⚡ System Diagnostics", Icons.Default.Sensors) {
                onSendMessage("Run system diagnostics and battery check")
            }
            QuickCommandChip("🧠 Core Memories", Icons.Default.Memory) {
                onSendMessage("What do you remember about me?")
            }
            QuickCommandChip("🧮 Compute sqrt(256)", Icons.Default.Calculate) {
                onSendMessage("calculate sqrt(256) * 15")
            }
            QuickCommandChip("📑 Document Scanner", Icons.Default.Description) {
                onOpenDocScanner()
            }
        }

        // Conversation Terminal HUD
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(JarvisSurface.copy(alpha = 0.6f))
                .border(1.dp, JarvisCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubbleHud(
                        message = msg,
                        onSpeak = onSpeakMessage
                    )
                }
            }
        }

        // Bottom Command Input Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        "Transmit directive to J.A.R.V.I.S...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisTextSecondary.copy(alpha = 0.5f)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_field"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = JarvisCyan,
                    unfocusedBorderColor = JarvisCyan.copy(alpha = 0.35f),
                    focusedContainerColor = JarvisSurface,
                    unfocusedContainerColor = JarvisSurface,
                    focusedTextColor = JarvisTextPrimary,
                    unfocusedTextColor = JarvisTextPrimary
                ),
                shape = RoundedCornerShape(24.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Voice Mic Button
            IconButton(
                onClick = onToggleVoice,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (voiceState == JarvisVoiceState.LISTENING) JarvisGreen else JarvisSurfaceHighlight)
                    .border(
                        1.dp,
                        if (voiceState == JarvisVoiceState.LISTENING) JarvisGreen else JarvisCyan.copy(alpha = 0.5f),
                        CircleShape
                    )
                    .testTag("voice_mic_button")
            ) {
                Icon(
                    imageVector = if (voiceState == JarvisVoiceState.LISTENING) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = "Voice Input",
                    tint = if (voiceState == JarvisVoiceState.LISTENING) JarvisBackground else JarvisCyan,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Send Button
            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText)
                        inputText = ""
                    }
                },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(JarvisCyan)
                    .testTag("chat_send_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = JarvisBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun QuickCommandChip(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(JarvisSurfaceVariant)
            .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("quick_chip_${text.take(8)}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = JarvisCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp
            )
        }
    }
}
