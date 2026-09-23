package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisSurface
import com.example.ui.theme.JarvisSurfaceHighlight
import com.example.ui.theme.JarvisSurfaceVariant
import com.example.ui.theme.JarvisTextMuted
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatBubbleHud(
    message: ChatMessage,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isJarvis = message.sender == MessageSender.JARVIS
    val isSystem = message.sender == MessageSender.SYSTEM
    var isReasoningExpanded by remember { mutableStateOf(false) }

    val borderColor = when {
        isJarvis -> JarvisCyan.copy(alpha = 0.6f)
        isSystem -> JarvisGold.copy(alpha = 0.5f)
        else -> JarvisTextSecondary.copy(alpha = 0.3f)
    }

    val headerColor = when {
        isJarvis -> JarvisCyan
        isSystem -> JarvisGold
        else -> JarvisGold
    }

    val headerText = when {
        isJarvis -> "J.A.R.V.I.S. // CORE RESPONSE"
        isSystem -> "SUBSYSTEM TELEMETRY"
        else -> "OPERATOR // DIRECTIVE"
    }

    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(message.timestamp))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalAlignment = if (isJarvis || isSystem) Alignment.Start else Alignment.End
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isJarvis) JarvisSurface else JarvisSurfaceVariant)
                .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("chat_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.labelSmall,
                        color = headerColor,
                        fontWeight = FontWeight.Bold
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisTextMuted,
                            fontSize = 10.sp
                        )
                        if (isJarvis && message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = { onSpeak(message.text) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Replay speech",
                                    tint = JarvisCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Message text
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisTextPrimary
                )

                // Tool result display (if any)
                if (!message.toolResult.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(JarvisSurfaceHighlight)
                            .border(1.dp, JarvisCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = message.toolResult,
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisCyan,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                // High-level explainable reasoning accordion
                if (!message.highLevelReasoning.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isReasoningExpanded = !isReasoningExpanded }
                            .padding(vertical = 2.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = JarvisGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "PROTOCOL REASONING",
                            style = MaterialTheme.typography.labelSmall,
                            color = JarvisGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                        Icon(
                            imageVector = if (isReasoningExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = JarvisGold,
                            modifier = Modifier.size(14.dp)
                        )
                    }

                    AnimatedVisibility(visible = isReasoningExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(JarvisSurfaceVariant.copy(alpha = 0.7f))
                                .border(1.dp, JarvisGold.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = message.highLevelReasoning,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisTextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
