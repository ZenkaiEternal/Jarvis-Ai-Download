package com.example.domain.vision

import kotlin.math.abs
import kotlin.math.hypot

enum class HandGestureType(val label: String) {
    NO_HAND("NO HAND"),
    OPEN_PALM("OPEN PALM"),
    POINTING("FINGERTIP MOVE"),
    ZOOM("GESTURE ZOOM"),
    CLOSED_FIST("FIST PAUSE"),
    PINCH("PINCH"),
    TRACKING("TRACKING")
}

data class NormalizedLandmarkPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f
)

data class HandTrackingOutput(
    val handDetected: Boolean = false,
    val gesture: HandGestureType = HandGestureType.NO_HAND,
    val fingertipX: Float = 0.5f,
    val fingertipY: Float = 0.5f,
    val targetOffsetX: Float = 0f,
    val targetOffsetY: Float = 0f,
    val targetYawDeg: Float = 0f,
    val targetPitchDeg: Float = 0f,
    val targetZoom: Float = 1.0f,
    val rotationPaused: Boolean = false,
    val pinchTriggered: Boolean = false,
    val landmarks: List<NormalizedLandmarkPoint> = emptyList(),
    val secondaryLandmarks: List<NormalizedLandmarkPoint> = emptyList()
)

/**
 * Pure-Kotlin fingertip, multi-hand zoom, and gesture processor for 21-point MediaPipe landmarks.
 * Supports:
 * - Fingertip movement tracking (index/open-hand fingertips) -> 3D translation + smooth tilt/rotation
 * - 2-hand fingertip spread -> Zoom In / Zoom Out (0.55x .. 2.15x)
 * - 1-hand two-finger aperture (thumb + index spread while index+middle raised) -> Zoom In / Zoom Out
 * - Closed fist -> Freeze/pause current position, rotation & zoom
 * - Quick pinch -> Configurable trigger with hysteresis & cooldown
 */
class HandGestureProcessor(
    private val smoothingAlpha: Float = 0.28f,
    private val deadbandNormalized: Float = 0.008f,
    private val maxStepDegPerFrame: Float = 5.5f,
    private val maxOffsetStepPerFrame: Float = 0.14f,
    private val maxYawDeg: Float = 65f,
    private val maxPitchDeg: Float = 42f,
    private val maxOffsetX: Float = 1.35f,
    private val maxOffsetY: Float = 1.05f,
    private val minZoom: Float = 0.55f,
    private val maxZoom: Float = 2.15f,
    private val pinchEnterThreshold: Float = 0.34f,
    private val pinchExitThreshold: Float = 0.45f,
    private val pinchCooldownMs: Long = 900L,
    private val handLossTimeoutMs: Long = 850L
) {
    private var smoothedTipX: Float = 0.5f
    private var smoothedTipY: Float = 0.5f
    private var currentOffsetX: Float = 0f
    private var currentOffsetY: Float = 0f
    private var currentYaw: Float = 0f
    private var currentPitch: Float = 0f
    private var currentZoom: Float = 1.0f
    private var isInitialized = false

    private var rotationPaused = false
    private var isPinchActive = false
    private var lastPinchTriggerMs = 0L
    private var lastHandSeenMs = 0L

    private var twoHandAnchorSpread: Float? = null
    private var twoHandAnchorZoom: Float = 1.0f
    private var singleZoomAnchorRatio: Float? = null
    private var singleZoomAnchorZoom: Float = 1.0f

    fun reset() {
        smoothedTipX = 0.5f
        smoothedTipY = 0.5f
        currentOffsetX = 0f
        currentOffsetY = 0f
        currentYaw = 0f
        currentPitch = 0f
        currentZoom = 1.0f
        isInitialized = false
        rotationPaused = false
        isPinchActive = false
        twoHandAnchorSpread = null
        singleZoomAnchorRatio = null
    }

    fun processLandmarks(
        landmarks: List<NormalizedLandmarkPoint>,
        nowMs: Long = System.currentTimeMillis(),
        mirrorX: Boolean = true
    ): HandTrackingOutput {
        val hands = if (landmarks.size >= 21) listOf(landmarks) else emptyList()
        return processMultiHandLandmarks(hands, nowMs, mirrorX)
    }

    fun processMultiHandLandmarks(
        hands: List<List<NormalizedLandmarkPoint>>,
        nowMs: Long = System.currentTimeMillis(),
        mirrorX: Boolean = true
    ): HandTrackingOutput {
        val validHands = hands.filter { it.size >= 21 }
        if (validHands.isEmpty()) {
            twoHandAnchorSpread = null
            singleZoomAnchorRatio = null
            val lostElapsed = nowMs - lastHandSeenMs
            if (lastHandSeenMs == 0L || lostElapsed >= handLossTimeoutMs) {
                currentYaw *= 0.88f
                currentPitch *= 0.88f
                currentOffsetX *= 0.88f
                currentOffsetY *= 0.88f
                if (abs(currentYaw) < 0.2f) currentYaw = 0f
                if (abs(currentPitch) < 0.2f) currentPitch = 0f
                if (abs(currentOffsetX) < 0.01f) currentOffsetX = 0f
                if (abs(currentOffsetY) < 0.01f) currentOffsetY = 0f
                isPinchActive = false
            }
            return HandTrackingOutput(
                handDetected = false,
                gesture = HandGestureType.NO_HAND,
                fingertipX = smoothedTipX,
                fingertipY = smoothedTipY,
                targetOffsetX = currentOffsetX,
                targetOffsetY = currentOffsetY,
                targetYawDeg = currentYaw,
                targetPitchDeg = currentPitch,
                targetZoom = currentZoom,
                rotationPaused = rotationPaused,
                pinchTriggered = false,
                landmarks = emptyList(),
                secondaryLandmarks = emptyList()
            )
        }

        lastHandSeenMs = nowMs
        val primary = validHands[0]
        val secondary = validHands.getOrNull(1)

        // Two-hand fingertip spread zoom (left & right index fingertips moving apart/together)
        if (secondary != null) {
            singleZoomAnchorRatio = null
            isPinchActive = false

            val tip1 = primary[8]
            val tip2 = secondary[8]
            val x1 = if (mirrorX) 1f - tip1.x else tip1.x
            val x2 = if (mirrorX) 1f - tip2.x else tip2.x
            val spread = hypot(x1 - x2, tip1.y - tip2.y).coerceAtLeast(0.04f)

            val anchor = twoHandAnchorSpread
            if (anchor == null) {
                twoHandAnchorSpread = spread
                twoHandAnchorZoom = currentZoom
            } else {
                val desiredZoom = (twoHandAnchorZoom * (spread / anchor)).coerceIn(minZoom, maxZoom)
                currentZoom += (desiredZoom - currentZoom) * 0.35f
            }

            val midX = (x1 + x2) * 0.5f
            val midY = (tip1.y + tip2.y) * 0.5f
            updateSmoothedFingertip(midX, midY)
            applyOffsetAndTiltFromTip()

            return HandTrackingOutput(
                handDetected = true,
                gesture = HandGestureType.ZOOM,
                fingertipX = smoothedTipX,
                fingertipY = smoothedTipY,
                targetOffsetX = currentOffsetX,
                targetOffsetY = currentOffsetY,
                targetYawDeg = currentYaw,
                targetPitchDeg = currentPitch,
                targetZoom = currentZoom,
                rotationPaused = false,
                pinchTriggered = false,
                landmarks = mirrorIfNeeded(primary, mirrorX),
                secondaryLandmarks = mirrorIfNeeded(secondary, mirrorX)
            )
        }

        twoHandAnchorSpread = null

        val wrist = primary[0]
        val thumbTip = primary[4]
        val indexPip = primary[6]
        val indexTip = primary[8]
        val middleMcp = primary[9]
        val middlePip = primary[10]
        val middleTip = primary[12]
        val ringPip = primary[14]
        val ringTip = primary[16]
        val pinkyPip = primary[18]
        val pinkyTip = primary[20]

        val palmScale = distance(wrist, middleMcp).coerceAtLeast(0.05f)
        val pinchRatio = distance(thumbTip, indexTip) / palmScale

        val indexExtended = distance(wrist, indexTip) > distance(wrist, indexPip) * 1.13f
        val middleExtended = distance(wrist, middleTip) > distance(wrist, middlePip) * 1.13f
        val ringExtended = distance(wrist, ringTip) > distance(wrist, ringPip) * 1.13f
        val pinkyExtended = distance(wrist, pinkyTip) > distance(wrist, pinkyPip) * 1.13f
        val extendedCount = listOf(indexExtended, middleExtended, ringExtended, pinkyExtended).count { it }

        // 1-Hand Two-Finger Zoom Pose: index + middle raised, ring + pinky curled -> thumb-index spread zooms
        val isSingleHandZoomPose = indexExtended && middleExtended && !ringExtended && !pinkyExtended
        if (isSingleHandZoomPose) {
            isPinchActive = false
            val anchor = singleZoomAnchorRatio
            if (anchor == null) {
                singleZoomAnchorRatio = pinchRatio
                singleZoomAnchorZoom = currentZoom
            } else {
                val deltaRatio = pinchRatio - anchor
                val desiredZoom = (singleZoomAnchorZoom + deltaRatio * 2.15f).coerceIn(minZoom, maxZoom)
                currentZoom += (desiredZoom - currentZoom) * 0.32f
            }

            val tipX = if (mirrorX) 1f - indexTip.x else indexTip.x
            updateSmoothedFingertip(tipX, indexTip.y)

            return HandTrackingOutput(
                handDetected = true,
                gesture = HandGestureType.ZOOM,
                fingertipX = smoothedTipX,
                fingertipY = smoothedTipY,
                targetOffsetX = currentOffsetX,
                targetOffsetY = currentOffsetY,
                targetYawDeg = currentYaw,
                targetPitchDeg = currentPitch,
                targetZoom = currentZoom,
                rotationPaused = rotationPaused,
                pinchTriggered = false,
                landmarks = mirrorIfNeeded(primary, mirrorX)
            )
        }

        singleZoomAnchorRatio = null

        // Hysteresis pinch tap detection (when not in zoom aperture pose)
        var pinchTriggered = false
        if (!isPinchActive && pinchRatio < pinchEnterThreshold) {
            isPinchActive = true
            if (nowMs - lastPinchTriggerMs >= pinchCooldownMs) {
                lastPinchTriggerMs = nowMs
                pinchTriggered = true
            }
        } else if (isPinchActive && pinchRatio > pinchExitThreshold) {
            isPinchActive = false
        }

        val gesture = when {
            isPinchActive -> HandGestureType.PINCH
            extendedCount == 0 || (extendedCount == 1 && !indexExtended) -> {
                rotationPaused = true
                HandGestureType.CLOSED_FIST
            }
            indexExtended && !middleExtended && !ringExtended && !pinkyExtended -> {
                rotationPaused = false
                HandGestureType.POINTING
            }
            extendedCount >= 3 -> {
                rotationPaused = false
                HandGestureType.OPEN_PALM
            }
            else -> {
                rotationPaused = false
                HandGestureType.TRACKING
            }
        }

        // Track active fingertips: precision indexTip (#8) when pointing, weighted fingertips when open palm
        val rawTipX: Float
        val rawTipY: Float
        if (gesture == HandGestureType.POINTING || !middleExtended) {
            rawTipX = indexTip.x
            rawTipY = indexTip.y
        } else {
            rawTipX = indexTip.x * 0.55f + middleTip.x * 0.25f + thumbTip.x * 0.20f
            rawTipY = indexTip.y * 0.55f + middleTip.y * 0.25f + thumbTip.y * 0.20f
        }
        val effectiveTipX = if (mirrorX) 1f - rawTipX else rawTipX
        updateSmoothedFingertip(effectiveTipX, rawTipY)

        if (!rotationPaused && gesture != HandGestureType.PINCH) {
            applyOffsetAndTiltFromTip()
        }

        return HandTrackingOutput(
            handDetected = true,
            gesture = gesture,
            fingertipX = smoothedTipX,
            fingertipY = smoothedTipY,
            targetOffsetX = currentOffsetX,
            targetOffsetY = currentOffsetY,
            targetYawDeg = currentYaw,
            targetPitchDeg = currentPitch,
            targetZoom = currentZoom,
            rotationPaused = rotationPaused,
            pinchTriggered = pinchTriggered,
            landmarks = mirrorIfNeeded(primary, mirrorX)
        )
    }

    private fun updateSmoothedFingertip(x: Float, y: Float) {
        val clampedX = x.coerceIn(0f, 1f)
        val clampedY = y.coerceIn(0f, 1f)
        if (!isInitialized) {
            smoothedTipX = clampedX
            smoothedTipY = clampedY
            isInitialized = true
        } else {
            val dx = clampedX - smoothedTipX
            val dy = clampedY - smoothedTipY
            if (abs(dx) > deadbandNormalized) {
                smoothedTipX += dx * smoothingAlpha
            }
            if (abs(dy) > deadbandNormalized) {
                smoothedTipY += dy * smoothingAlpha
            }
        }
    }

    private fun applyOffsetAndTiltFromTip() {
        val desiredOffsetX = ((smoothedTipX - 0.5f) * 2f * maxOffsetX).coerceIn(-maxOffsetX, maxOffsetX)
        // Screen Y grows downward; Three.js Y grows upward
        val desiredOffsetY = (-(smoothedTipY - 0.5f) * 2f * maxOffsetY).coerceIn(-maxOffsetY, maxOffsetY)
        currentOffsetX += (desiredOffsetX - currentOffsetX).coerceIn(-maxOffsetStepPerFrame, maxOffsetStepPerFrame)
        currentOffsetY += (desiredOffsetY - currentOffsetY).coerceIn(-maxOffsetStepPerFrame, maxOffsetStepPerFrame)

        val desiredYaw = ((smoothedTipX - 0.5f) * 2f * maxYawDeg).coerceIn(-maxYawDeg, maxYawDeg)
        val desiredPitch = ((smoothedTipY - 0.5f) * 2f * maxPitchDeg).coerceIn(-maxPitchDeg, maxPitchDeg)
        currentYaw += (desiredYaw - currentYaw).coerceIn(-maxStepDegPerFrame, maxStepDegPerFrame)
        currentPitch += (desiredPitch - currentPitch).coerceIn(-maxStepDegPerFrame, maxStepDegPerFrame)
    }

    private fun mirrorIfNeeded(
        landmarks: List<NormalizedLandmarkPoint>,
        mirrorX: Boolean
    ): List<NormalizedLandmarkPoint> {
        return if (mirrorX) landmarks.map { it.copy(x = 1f - it.x) } else landmarks
    }

    private fun distance(a: NormalizedLandmarkPoint, b: NormalizedLandmarkPoint): Float {
        return hypot(a.x - b.x, a.y - b.y)
    }
}
