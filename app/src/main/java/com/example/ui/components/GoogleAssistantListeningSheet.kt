package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.JarvisVoiceState
import com.example.ui.theme.JarvisBackground
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceHighlight
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoogleAssistantListeningSheet(
    voiceState: JarvisVoiceState,
    amplitude: Float,
    liveTranscript: String,
    onStopListening: () -> Unit,
    onCommandSelected: (String) -> Unit,
    onLaunchSystemVoiceDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isVisible = voiceState == JarvisVoiceState.LISTENING

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250, easing = FastOutSlowInEasing)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            JarvisSurfaceHighlight,
                            JarvisSurface,
                            JarvisBackground
                        )
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        listOf(JarvisCyan, JarvisRedAlert, JarvisGold, JarvisGreen)
                    ),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("google_assistant_listening_sheet")
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Row with Google Assistant Iconic 4-Dots & Dismiss button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GoogleAssistantAnimatedDots(amplitude = amplitude)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Listening...",
                            style = MaterialTheme.typography.titleMedium,
                            color = JarvisCyanBright,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // System Voice Dialog fallback
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(JarvisSurface)
                                .border(1.dp, JarvisCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                .clickable { onLaunchSystemVoiceDialog() }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .testTag("btn_system_voice_modal")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.RecordVoiceOver,
                                    contentDescription = "System Voice",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Google Dialog",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisCyan,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = onStopListening,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(JarvisSurface)
                                .testTag("btn_close_listening")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = JarvisTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Real-Time Spoken Transcription Box (Google Assistant Style)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(JarvisBackground.copy(alpha = 0.8f))
                        .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (liveTranscript.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = JarvisGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = liveTranscript,
                                style = MaterialTheme.typography.bodyLarge,
                                color = JarvisTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        Text(
                            text = "Speak now... (e.g. 'Turn on flashlight', 'Activate Veronica', 'Scan for viruses')",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextMuted,
                            fontSize = 14.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Command Suggestion Chips
                Text(
                    text = "QUICK DIRECTIVES:",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTextMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssistantSuggestionChip("🔦 Flashlight On") { onCommandSelected("turn on flashlight") }
                    AssistantSuggestionChip("🛡️ Anti-Virus Scan") { onCommandSelected("scan for viruses") }
                    AssistantSuggestionChip("🚀 Activate Veronica (Code 3000)") { onCommandSelected("activate veronica code 3000") }
                    AssistantSuggestionChip("🔋 Battery Status") { onCommandSelected("battery status") }
                    AssistantSuggestionChip("🔊 Volume to Max") { onCommandSelected("max volume") }
                    AssistantSuggestionChip("💡 Flashlight Off") { onCommandSelected("turn off flashlight") }
                }
            }
        }
    }
}

@Composable
fun GoogleAssistantAnimatedDots(amplitude: Float) {
    val infiniteTransition = rememberInfiniteTransition(label = "DotsAnimation")

    val bounce1 by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot1"
    )

    val bounce2 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = -4f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot2"
    )

    val bounce3 by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(440, delayMillis = 200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot3"
    )

    val bounce4 by infiniteTransition.animateFloat(
        initialValue = 3f,
        targetValue = -3f,
        animationSpec = infiniteRepeatable(
            animation = tween(410, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "Dot4"
    )

    val scaleAmp = (amplitude * 10f).coerceIn(1f, 12f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .offset(y = (bounce1 * (scaleAmp / 5f)).dp)
                .size((8 + amplitude * 6).dp)
                .clip(CircleShape)
                .background(JarvisCyanBright)
        )
        Box(
            modifier = Modifier
                .offset(y = (bounce2 * (scaleAmp / 5f)).dp)
                .size((8 + amplitude * 6).dp)
                .clip(CircleShape)
                .background(JarvisRedAlert)
        )
        Box(
            modifier = Modifier
                .offset(y = (bounce3 * (scaleAmp / 5f)).dp)
                .size((8 + amplitude * 6).dp)
                .clip(CircleShape)
                .background(JarvisGold)
        )
        Box(
            modifier = Modifier
                .offset(y = (bounce4 * (scaleAmp / 5f)).dp)
                .size((8 + amplitude * 6).dp)
                .clip(CircleShape)
                .background(JarvisGreen)
        )
    }
}

@Composable
fun AssistantSuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurface)
            .border(1.dp, JarvisCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = JarvisTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
