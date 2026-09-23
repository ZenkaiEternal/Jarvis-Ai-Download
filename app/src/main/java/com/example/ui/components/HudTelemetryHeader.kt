package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JarvisVoiceState
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HudTelemetryHeader(
    voiceState: JarvisVoiceState,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy", Locale.US)
        while (true) {
            val now = Date()
            currentTime = timeFormat.format(now)
            currentDate = dateFormat.format(now)
            delay(1000)
        }
    }

    val stateBadgeColor by animateColorAsState(
        targetValue = when (voiceState) {
            JarvisVoiceState.IDLE -> JarvisCyan
            JarvisVoiceState.LISTENING -> JarvisGreen
            JarvisVoiceState.THINKING -> JarvisGold
            JarvisVoiceState.SPEAKING -> JarvisCyan
        },
        label = "stateBadgeColor"
    )

    val stateText = when (voiceState) {
        JarvisVoiceState.IDLE -> "STANDBY"
        JarvisVoiceState.LISTENING -> "VOICE ACTIVE"
        JarvisVoiceState.THINKING -> "COMPUTING"
        JarvisVoiceState.SPEAKING -> "TRANSMITTING"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .background(JarvisSurface.copy(alpha = 0.95f))
            .border(1.dp, JarvisCyan.copy(alpha = 0.35f), RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title & Subsystem identifier
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(stateBadgeColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.titleMedium,
                        color = JarvisCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MK-VIII",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisGold,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "$currentDate // $currentTime",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextSecondary
                )
            }

            // Right: Status badge & Audio Mute toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, stateBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = stateBadgeColor,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stateText,
                            style = MaterialTheme.typography.labelSmall,
                            color = stateBadgeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Mute / Voice output toggle
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("audio_mute_toggle")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Audio Muted" else "Audio Enabled",
                        tint = if (isMuted) JarvisRedAlert else JarvisCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
