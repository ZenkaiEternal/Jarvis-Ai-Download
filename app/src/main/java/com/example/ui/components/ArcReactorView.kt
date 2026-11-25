package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.domain.ai.JarvisEmotion
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisEmber
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGoldBright
import com.example.ui.theme.JarvisHoloWhite
import kotlin.math.cos
import kotlin.math.sin

private data class EnergyParticle(
    val baseAngleDeg: Float,
    val radiusFactor: Float,
    val speedScale: Float,
    val strokeWidth: Float
)

@Composable
fun ArcReactorView(
    voiceState: JarvisVoiceState,
    amplitude: Float,
    emotion: JarvisEmotion = JarvisEmotion.CALMNESS,
    wakeBurstNonce: Int = 0,
    modifier: Modifier = Modifier,
    size: Dp = 320.dp,
    onClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "ArcReactorTransition")

    val smoothAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0.06f, 1.0f),
        animationSpec = tween(durationMillis = 85),
        label = "smoothAmplitude"
    )

    val targetPrimary = when (voiceState) {
        JarvisVoiceState.LISTENING -> JarvisCyanBright
        JarvisVoiceState.THINKING -> JarvisGoldBright
        JarvisVoiceState.SPEAKING -> JarvisGold
        JarvisVoiceState.IDLE -> JarvisGold
    }
    val primaryColor by animateColorAsState(
        targetValue = targetPrimary,
        animationSpec = tween(durationMillis = 420),
        label = "reactorPrimaryColor"
    )

    val secondaryColor by animateColorAsState(
        targetValue = when (voiceState) {
            JarvisVoiceState.THINKING -> JarvisElectricBlue
            JarvisVoiceState.LISTENING -> JarvisHoloWhite
            else -> JarvisEmber
        },
        animationSpec = tween(durationMillis = 420),
        label = "reactorSecondaryColor"
    )

    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == JarvisVoiceState.THINKING) 3600 else 13000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerRotation"
    )

    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (voiceState == JarvisVoiceState.THINKING) 2600 else 8200,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "innerRotation"
    )

    val particleOrbit by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "particleOrbit"
    )

    val corePulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (voiceState) {
                    JarvisVoiceState.LISTENING -> 420
                    JarvisVoiceState.THINKING -> 580
                    JarvisVoiceState.SPEAKING -> 460
                    JarvisVoiceState.IDLE -> 2100
                },
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "corePulse"
    )

    val wakeShockwave = remember { Animatable(0f) }
    LaunchedEffect(wakeBurstNonce) {
        if (wakeBurstNonce > 0) {
            wakeShockwave.snapTo(0.01f)
            wakeShockwave.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing)
            )
            wakeShockwave.snapTo(0f)
        }
    }

    val particles = remember {
        List(28) { idx ->
            EnergyParticle(
                baseAngleDeg = idx * (360f / 28f),
                radiusFactor = 0.62f + ((idx * 7) % 10) * 0.032f,
                speedScale = if (idx % 2 == 0) 1.0f else -1.35f,
                strokeWidth = if (idx % 3 == 0) 5.5f else 3.2f
            )
        }
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
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = this.size.minDimension
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = (minDim / 2f) - 14f

            // 1. Ambient holographic halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        primaryColor.copy(alpha = 0.16f + smoothAmp * 0.14f),
                        secondaryColor.copy(alpha = 0.06f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 2. Outer calibrated holographic dial with precision ticks
            rotate(outerRotation, pivot = center) {
                drawCircle(
                    color = primaryColor.copy(alpha = 0.28f),
                    radius = radius * 0.96f,
                    center = center,
                    style = Stroke(width = 2f)
                )

                for (i in 0 until 36) {
                    val angleRad = Math.toRadians((i * 10).toDouble())
                    val isMajor = i % 6 == 0
                    val tickLen = if (isMajor) 16f else 7f
                    val rOuter = radius * 0.96f
                    val rInner = rOuter - tickLen
                    drawLine(
                        color = if (isMajor) JarvisHoloWhite.copy(alpha = 0.85f) else primaryColor.copy(alpha = 0.42f),
                        start = Offset(
                            center.x + (rInner * cos(angleRad)).toFloat(),
                            center.y + (rInner * sin(angleRad)).toFloat()
                        ),
                        end = Offset(
                            center.x + (rOuter * cos(angleRad)).toFloat(),
                            center.y + (rOuter * sin(angleRad)).toFloat()
                        ),
                        strokeWidth = if (isMajor) 3f else 1.5f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 3. Real-audio circular holographic waveform ring (48 radial bars driven by live PCM / TTS amplitude)
            val spectrumBaseR = radius * 0.78f
            val bars = 48
            for (i in 0 until bars) {
                val angleDeg = i * (360f / bars) + outerRotation * 0.25f
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val harmonic = sin(Math.toRadians((i * 30.0 + particleOrbit * 3.0))).toFloat()
                val dynamicAmplitude = when (voiceState) {
                    JarvisVoiceState.LISTENING -> smoothAmp * (0.65f + 0.35f * kotlin.math.abs(harmonic))
                    JarvisVoiceState.SPEAKING -> smoothAmp * (0.70f + 0.30f * kotlin.math.abs(harmonic))
                    JarvisVoiceState.THINKING -> 0.25f + 0.28f * kotlin.math.abs(harmonic)
                    JarvisVoiceState.IDLE -> 0.08f + 0.07f * kotlin.math.abs(harmonic)
                }
                val barLen = (8f + dynamicAmplitude * (radius * 0.16f)).coerceIn(5f, radius * 0.19f)
                val startR = spectrumBaseR
                val endR = spectrumBaseR + barLen
                drawLine(
                    color = if (dynamicAmplitude > 0.55f) JarvisHoloWhite.copy(alpha = 0.9f)
                    else primaryColor.copy(alpha = 0.55f + dynamicAmplitude * 0.35f),
                    start = Offset(
                        center.x + (startR * cos(angleRad)).toFloat(),
                        center.y + (startR * sin(angleRad)).toFloat()
                    ),
                    end = Offset(
                        center.x + (endR * cos(angleRad)).toFloat(),
                        center.y + (endR * sin(angleRad)).toFloat()
                    ),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            // 4. Counter-rotating segmented energy arc ring
            val segRadius = radius * 0.69f
            rotate(innerRotation, pivot = center) {
                for (i in 0 until 8) {
                    drawArc(
                        color = primaryColor.copy(alpha = 0.72f),
                        startAngle = i * 45f + 7f,
                        sweepAngle = 31f,
                        useCenter = false,
                        topLeft = Offset(center.x - segRadius, center.y - segRadius),
                        size = Size(segRadius * 2f, segRadius * 2f),
                        style = Stroke(width = 5.5f, cap = StrokeCap.Round)
                    )
                }
            }

            // 5. Inner holographic gyroscopic ring
            val innerGyroR = radius * 0.54f
            rotate(-outerRotation * 1.4f, pivot = center) {
                for (i in 0 until 4) {
                    drawArc(
                        color = secondaryColor.copy(alpha = 0.60f),
                        startAngle = i * 90f + 12f,
                        sweepAngle = 62f,
                        useCenter = false,
                        topLeft = Offset(center.x - innerGyroR, center.y - innerGyroR),
                        size = Size(innerGyroR * 2f, innerGyroR * 2f),
                        style = Stroke(width = 3f, cap = StrokeCap.Round)
                    )
                }
            }

            // 6. Glowing orbital energy particles
            val particlePoints = particles.map { p ->
                val angleRad = Math.toRadians((p.baseAngleDeg + particleOrbit * p.speedScale).toDouble())
                val pr = radius * p.radiusFactor * (1f + smoothAmp * 0.05f)
                Offset(
                    center.x + (pr * cos(angleRad)).toFloat(),
                    center.y + (pr * sin(angleRad)).toFloat()
                )
            }
            drawPoints(
                points = particlePoints,
                pointMode = PointMode.Points,
                color = JarvisHoloWhite.copy(alpha = 0.75f),
                strokeWidth = 4.5f,
                cap = StrokeCap.Round
            )

            // 7. Pulsating center energy sphere glow (softly illuminates behind the 3D Jarvis model)
            val sphereRadius = (radius * 0.34f * corePulse) + (smoothAmp * 18f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        JarvisHoloWhite.copy(alpha = 0.55f),
                        primaryColor.copy(alpha = 0.38f),
                        secondaryColor.copy(alpha = 0.14f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = sphereRadius
                ),
                radius = sphereRadius,
                center = center
            )

            // 8. Wake-word activation shockwave ring
            val shock = wakeShockwave.value
            if (shock > 0f) {
                val shockR = radius * (0.35f + shock * 0.65f)
                drawCircle(
                    color = JarvisHoloWhite.copy(alpha = (1f - shock) * 0.85f),
                    radius = shockR,
                    center = center,
                    style = Stroke(width = (1f - shock) * 8f + 2f)
                )
            }
        }
    }
}
