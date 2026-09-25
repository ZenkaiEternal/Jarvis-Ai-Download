package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Shield
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
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldBright
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
    isSentinelActive: Boolean = false,
    onToggleSentinel: () -> Unit = {},
    isDeviceOnline: Boolean = false,
    isVeronicaActive: Boolean = false,
    onOpenVeronica: () -> Unit = {},
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
            .padding(horizontal = 14.dp, vertical = 10.dp)
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

            // Right: Network Indicator & Background Sentinel Button & Status badge & Audio Mute toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Autonomous Offline / Online Uplink Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isDeviceOnline) JarvisGreen.copy(alpha = 0.15f) else JarvisCyan.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (isDeviceOnline) JarvisGreen.copy(alpha = 0.5f) else JarvisCyan.copy(alpha = 0.4f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = if (isDeviceOnline) JarvisGreen else JarvisCyanBright,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isDeviceOnline) "ONLINE" else "OFFLINE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDeviceOnline) JarvisGreen else JarvisCyanBright,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Background Sentinel Toggle Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSentinelActive) JarvisGreen.copy(alpha = 0.18f) else JarvisSurfaceVariant)
                        .border(
                            1.dp,
                            if (isSentinelActive) JarvisGreen else JarvisCyan.copy(alpha = 0.35f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onToggleSentinel)
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                        .testTag("toggle_sentinel_header_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Shield,
                            contentDescription = "Background Sentinel",
                            tint = if (isSentinelActive) JarvisGreen else JarvisTextSecondary,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isSentinelActive) "SENTINEL" else "SENTINEL",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isSentinelActive) JarvisGreen else JarvisTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(5.dp))

                // Veronica Protocol Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isVeronicaActive) JarvisRedAlert.copy(alpha = 0.25f) else JarvisSurfaceVariant)
                        .border(
                            1.dp,
                            if (isVeronicaActive) JarvisGoldBright else JarvisRedAlert.copy(alpha = 0.45f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable(onClick = onOpenVeronica)
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                        .testTag("veronica_header_button")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = "Veronica",
                            tint = if (isVeronicaActive) JarvisGoldBright else JarvisRedAlert,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = if (isVeronicaActive) "VERONICA" else "VERONICA",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isVeronicaActive) JarvisGoldBright else JarvisRedAlert,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Status Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(JarvisSurfaceVariant)
                        .border(1.dp, stateBadgeColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 5.dp)
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
                            fontSize = 9.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Mute / Voice output toggle
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(34.dp)
                        .testTag("audio_mute_toggle")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Audio Muted" else "Audio Enabled",
                        tint = if (isMuted) JarvisRedAlert else JarvisCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
