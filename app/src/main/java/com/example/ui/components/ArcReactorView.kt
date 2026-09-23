package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.JarvisVoiceState
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldBright
import com.example.ui.theme.JarvisRedAlert
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ArcReactorView(
    voiceState: JarvisVoiceState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    size: Dp = 220.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorTransition")

    // Continuous smooth rotation for outer dial
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (voiceState == JarvisVoiceState.THINKING) 3000 else 12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    // Counter rotation for inner segmented ring
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (voiceState == JarvisVoiceState.THINKING) 2500 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    // Radar scanning angle
    val radarAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radarAngle"
    )

    // Pulsing core breathing effect
    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    JarvisVoiceState.LISTENING -> 400
                    JarvisVoiceState.THINKING -> 600
                    JarvisVoiceState.SPEAKING -> 500
                    else -> 2000
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val primaryColor = when (voiceState) {
        JarvisVoiceState.LISTENING -> JarvisCyanBright
        JarvisVoiceState.THINKING -> JarvisGold
        JarvisVoiceState.SPEAKING -> JarvisCyan
        JarvisVoiceState.IDLE -> JarvisCyan
    }

    val secondaryColor = when (voiceState) {
        JarvisVoiceState.LISTENING -> JarvisCyan
        JarvisVoiceState.THINKING -> JarvisGoldBright
        JarvisVoiceState.SPEAKING -> JarvisGold
        JarvisVoiceState.IDLE -> JarvisCyan.copy(alpha = 0.6f)
    }

    Box(
        modifier = modifier
            .size(size)
            .testTag("arc_reactor_orb")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
            val radius = (size.toPx() / 2f) - 10f

            // 1. Outer decorative ring with tick marks
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.25f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 2f)
                )

                // 24 outer tick marks
                for (i in 0 until 24) {
                    val angle = (i * 15) * (Math.PI / 180.0)
                    val tickLen = if (i % 6 == 0) 14f else 6f
                    val strokeW = if (i % 6 == 0) 3f else 1.5f
                    val startX = center.x + ((radius - tickLen) * cos(angle)).toFloat()
                    val startY = center.y + ((radius - tickLen) * sin(angle)).toFloat()
                    val endX = center.x + (radius * cos(angle)).toFloat()
                    val endY = center.y + (radius * sin(angle)).toFloat()

                    drawLine(
                        color = if (i % 6 == 0) primaryColor else primaryColor.copy(alpha = 0.4f),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. Segmented Arc Rings (Arc Reactor triangular / chevron segments)
            val segmentRadius = radius * 0.78f
            rotate(innerRotation, pivot = center) {
                for (i in 0 until 8) {
                    val startAngle = i * 45f + 6f
                    drawArc(
                        color = primaryColor.copy(alpha = 0.85f),
                        startAngle = startAngle,
                        sweepAngle = 33f,
                        useCenter = false,
                        topLeft = Offset(center.x - segmentRadius, center.y - segmentRadius),
                        size = androidx.compose.ui.geometry.Size(segmentRadius * 2, segmentRadius * 2),
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                }
            }

            // 3. Radar Sweep Line (Scan animation)
            rotate(radarAngle, pivot = center) {
                drawLine(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.8f), Color.Transparent),
                        center = center,
                        radius = radius * 0.75f
                    ),
                    start = center,
                    end = Offset(center.x + (radius * 0.75f), center.y),
                    strokeWidth = 2.5f,
                    cap = StrokeCap.Round
                )
            }

            // 4. Middle energy perimeter
            val midRadius = radius * 0.52f
            drawCircle(
                color = secondaryColor.copy(alpha = 0.4f),
                radius = midRadius,
                center = center,
                style = Stroke(width = 2f)
            )

            // Dynamic expansion based on microphone amplitude
            val ampBoost = (amplitude * 20f).coerceIn(0f, 25f)

            // 5. Central glowing reactor orb
            val coreRadius = (radius * 0.32f * corePulse) + ampBoost
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White,
                        primaryColor,
                        secondaryColor.copy(alpha = 0.6f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = coreRadius
                ),
                radius = coreRadius,
                center = center
            )

            // 6. Core pupil ring
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = coreRadius * 0.35f,
                center = center,
                style = Stroke(width = 2.5f)
            )

            // 7. Micro energy points
            val dots = mutableListOf<Offset>()
            for (i in 0 until 12) {
                val angle = (i * 30) * (Math.PI / 180.0)
                val dotR = radius * 0.42f
                dots.add(
                    Offset(
                        center.x + (dotR * cos(angle)).toFloat(),
                        center.y + (dotR * sin(angle)).toFloat()
                    )
                )
            }
            drawPoints(
                points = dots,
                pointMode = PointMode.Points,
                color = primaryColor.copy(alpha = 0.9f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
    }
}
