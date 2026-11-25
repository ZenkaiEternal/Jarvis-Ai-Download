package com.example.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.domain.vision.FaceRecognitionOutput
import com.example.domain.vision.HandGestureType
import com.example.domain.vision.HandTrackingManager
import com.example.domain.vision.HandTrackingOutput
import com.example.domain.vision.NormalizedLandmarkPoint
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisCyanBright
import com.example.ui.theme.JarvisElectricBlue
import com.example.ui.theme.JarvisGold
import com.example.ui.theme.JarvisGreen
import com.example.ui.theme.JarvisHoloWhite
import com.example.ui.theme.JarvisRedAlert
import com.example.ui.theme.JarvisSurface
import java.util.Locale

private val HAND_BONE_PAIRS = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20, 0 to 17
)

private val FINGERTIP_INDICES = setOf(4, 8, 12, 16, 20)

@Composable
fun HandTrackerPreviewOverlay(
    manager: HandTrackingManager,
    trackingState: HandTrackingOutput,
    faceState: FaceRecognitionOutput = FaceRecognitionOutput(),
    errorText: String?,
    isTrackingEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, isTrackingEnabled) {
        if (isTrackingEnabled) {
            manager.start(lifecycleOwner, previewView)
        } else {
            manager.stop()
        }
        onDispose {
            manager.stop()
        }
    }

    val borderColor = when (trackingState.gesture) {
        HandGestureType.OPEN_PALM, HandGestureType.POINTING -> JarvisCyanBright
        HandGestureType.ZOOM -> JarvisElectricBlue
        HandGestureType.PINCH -> JarvisGreen
        HandGestureType.CLOSED_FIST -> JarvisGold
        HandGestureType.TRACKING -> JarvisCyan
        HandGestureType.NO_HAND -> JarvisCyan.copy(alpha = 0.45f)
    }

    val faceBoxColor = when (faceState.status) {
        "VERIFIED" -> JarvisGreen
        "UNRECOGNIZED" -> JarvisRedAlert
        else -> JarvisCyan
    }

    Box(
        modifier = modifier
            .width(132.dp)
            .height(172.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(JarvisSurface.copy(alpha = 0.85f))
            .border(1.2.dp, borderColor, RoundedCornerShape(14.dp))
            .testTag("hand_tracking_preview")
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Holographic dual-hand skeleton + facial recognition bounding frame
        Canvas(modifier = Modifier.fillMaxSize()) {
            val box = faceState.normalizedBox
            if (faceState.faceDetected && box != null) {
                val leftPx = box.left * size.width
                val topPx = box.top * size.height
                val wPx = ((box.right - box.left) * size.width).coerceAtLeast(12f)
                val hPx = ((box.bottom - box.top) * size.height).coerceAtLeast(12f)
                drawRect(
                    color = faceBoxColor.copy(alpha = 0.88f),
                    topLeft = Offset(leftPx, topPx),
                    size = Size(wPx, hPx),
                    style = Stroke(width = 2.2f)
                )
            }

            fun drawHandSkeleton(lms: List<NormalizedLandmarkPoint>, boneColor: Color) {
                if (lms.size < 21) return
                for ((a, b) in HAND_BONE_PAIRS) {
                    val p1 = lms[a]
                    val p2 = lms[b]
                    drawLine(
                        color = boneColor.copy(alpha = 0.85f),
                        start = Offset(p1.x * size.width, p1.y * size.height),
                        end = Offset(p2.x * size.width, p2.y * size.height),
                        strokeWidth = 2.5f,
                        cap = StrokeCap.Round
                    )
                }
                lms.forEachIndexed { idx, lm ->
                    val isTip = idx in FINGERTIP_INDICES
                    drawCircle(
                        color = if (isTip) JarvisCyanBright else JarvisHoloWhite,
                        radius = if (isTip) 5.5f else 3f,
                        center = Offset(lm.x * size.width, lm.y * size.height)
                    )
                }
            }

            drawHandSkeleton(trackingState.landmarks, borderColor)
            drawHandSkeleton(trackingState.secondaryLandmarks, JarvisElectricBlue)
        }

        // Top visibility & zoom status indicator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (trackingState.handDetected || faceState.faceDetected) JarvisGreen else JarvisCyan)
                )
                Text(
                    text = "VISION",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisHoloWhite,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = String.format(Locale.US, "%.1fx", trackingState.targetZoom),
                style = MaterialTheme.typography.labelSmall,
                color = JarvisCyanBright,
                fontWeight = FontWeight.Bold
            )
        }

        // Bottom gesture + face ID status bar
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (faceState.faceDetected) {
                Text(
                    text = "ID: ${faceState.recognizedLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    color = faceBoxColor,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = errorText?.take(16) ?: trackingState.gesture.label,
                style = MaterialTheme.typography.labelSmall,
                color = borderColor,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
