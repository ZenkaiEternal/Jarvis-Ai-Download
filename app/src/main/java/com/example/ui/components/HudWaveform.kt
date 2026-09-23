package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.JarvisVoiceState
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldBright
import kotlin.math.sin

@Composable
fun HudWaveform(
    voiceState: JarvisVoiceState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    barCount: Int = 28
) {
    val phaseAnim = remember { Animatable(0f) }

    LaunchedEffect(voiceState) {
        if (voiceState != JarvisVoiceState.IDLE) {
            phaseAnim.animateTo(
                targetValue = (2 * Math.PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            phaseAnim.snapTo(0f)
        }
    }

    val primaryColor = if (voiceState == JarvisVoiceState.THINKING) JarvisGold else JarvisCyan
    val highlightColor = if (voiceState == JarvisVoiceState.THINKING) JarvisGoldBright else JarvisCyanBright

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val w = size.width
        val h = size.height
        val barWidth = (w / (barCount * 1.6f)).coerceAtLeast(3f)
        val gap = (w - (barWidth * barCount)) / (barCount - 1)
        val centerY = h / 2f

        for (i in 0 until barCount) {
            val progress = i.toFloat() / barCount.toFloat()
            val waveModifier = sin((progress * 4f * Math.PI) + phaseAnim.value).toFloat()
            val baseScale = when (voiceState) {
                JarvisVoiceState.LISTENING -> 0.35f + (amplitude * 0.65f)
                JarvisVoiceState.SPEAKING -> 0.30f + (amplitude * 0.70f)
                JarvisVoiceState.THINKING -> 0.25f + (Math.abs(waveModifier) * 0.40f)
                JarvisVoiceState.IDLE -> 0.08f + (Math.abs(waveModifier) * 0.05f)
            }

            // Bell-curve distribution so edges are lower and center is elevated
            val bell = sin(progress * Math.PI).toFloat()
            val barHeight = (h * (baseScale * bell + 0.05f)).coerceIn(4f, h)

            val x = i * (barWidth + gap)
            val y = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(highlightColor, primaryColor),
                    startY = y,
                    endY = y + barHeight
                ),
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
