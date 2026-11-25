package com.example

import com.example.domain.ai.JarvisEmotion
import com.example.domain.ai.JarvisEmotionEngine
import com.example.domain.openjarvis.OpenJarvisBridge
import com.example.domain.vision.FacePoint2D
import com.example.domain.vision.FaceProfileSnapshot
import com.example.domain.vision.FaceSignatureMatcher
import com.example.domain.vision.HandGestureProcessor
import com.example.domain.vision.HandGestureType
import com.example.domain.vision.NormalizedLandmarkPoint
import com.example.domain.vision.SquareRoiTransform
import com.example.domain.voice.WakeWordMatcher
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {

    @Test
    fun testWakeWordMatcherAndCooldown() {
        assertTrue(WakeWordMatcher.containsJarvisTrigger("Hey Jarvis check status"))
        assertTrue(WakeWordMatcher.containsJarvisTrigger("Jarvis"))
        assertFalse(WakeWordMatcher.containsJarvisTrigger("Good morning system"))

        assertEquals("check status", WakeWordMatcher.extractRemainingCommand("Hey Jarvis check status"))
        assertEquals("", WakeWordMatcher.extractRemainingCommand("Jarvis"))

        assertTrue(WakeWordMatcher.canActivateWake(nowMs = 5000L, lastActivationMs = 1000L))
        assertFalse(WakeWordMatcher.canActivateWake(nowMs = 2500L, lastActivationMs = 1000L))

        val highSens = WakeWordMatcher.sensitivityToRmsThreshold(0.9f)
        val lowSens = WakeWordMatcher.sensitivityToRmsThreshold(0.2f)
        assertTrue(highSens < lowSens)
    }

    @Test
    fun testOpenJarvisBridgeParsingAndPayload() {
        assertEquals("http://192.168.1.10:8000", OpenJarvisBridge.normalizeBaseUrl("192.168.1.10:8000/"))
        assertEquals("https://jarvis.local:8443", OpenJarvisBridge.normalizeBaseUrl("https://jarvis.local:8443/"))
        assertNull(OpenJarvisBridge.normalizeBaseUrl("   "))

        val payload = OpenJarvisBridge.buildChatCompletionPayload(
            model = "qwen3:8b",
            systemInstruction = "Be concise",
            conversationHistory = listOf("user" to "hi", "assistant" to "hello"),
            userPrompt = "status",
            stream = true
        )
        assertEquals("qwen3:8b", payload.getString("model"))
        assertTrue(payload.getBoolean("stream"))
        assertEquals(4, payload.getJSONArray("messages").length())

        val modelsJson = JSONObject("""{"data":[{"id":"qwen3:8b"},{"id":"llama3.2:3b"}]}""")
        assertEquals(listOf("qwen3:8b", "llama3.2:3b"), OpenJarvisBridge.parseModelsResponse(modelsJson))

        val chunk = OpenJarvisBridge.parseSseChunk("""{"model":"qwen3:8b","choices":[{"delta":{"content":"All systems "}}]}""")
        assertEquals("All systems ", chunk.delta)
        assertEquals("qwen3:8b", chunk.model)
        assertNull(chunk.error)
    }

    @Test
    fun testEmotionEngineTagAndInference() {
        val (tag, clean) = JarvisEmotionEngine.extractEmotionTag("[EMOTION:EXCITEMENT] Core reactor nominal, sir.")
        assertEquals(JarvisEmotion.EXCITEMENT, tag)
        assertEquals("Core reactor nominal, sir.", clean)

        assertEquals(
            JarvisEmotion.CONCERN,
            JarvisEmotionEngine.inferEmotion("check threats", "Security warning found", isSecurityWarning = true)
        )
        assertEquals(
            JarvisEmotion.CURIOSITY,
            JarvisEmotionEngine.inferEmotion("explain quantum tunneling", "Here is how it works")
        )
        assertEquals(
            JarvisEmotion.CONCENTRATION,
            JarvisEmotionEngine.inferEmotion("calculate 128 * 42", "Result is 5376")
        )
    }

    @Test
    fun testHandGestureClassificationSmoothingAndHandLoss() {
        val processor = HandGestureProcessor()

        // Open palm shifted right -> mirrored left/yaw target smoothly interpolated
        val openPalm = createMockHand(
            palmCenterX = 0.25f,
            palmCenterY = 0.5f,
            fingersExtended = true,
            pinchDistance = 0.22f
        )
        val openOut1 = processor.processLandmarks(openPalm, nowMs = 1000L, mirrorX = true)
        assertTrue(openOut1.handDetected)
        assertEquals(HandGestureType.OPEN_PALM, openOut1.gesture)
        assertFalse(openOut1.rotationPaused)

        // Second frame verifies step clamping prevents abrupt rotation jumps
        val openOut2 = processor.processLandmarks(openPalm, nowMs = 1033L, mirrorX = true)
        assertTrue(abs(openOut2.targetYawDeg - openOut1.targetYawDeg) <= 5.6f)

        // Closed fist pauses rotation
        val fist = createMockHand(
            palmCenterX = 0.25f,
            palmCenterY = 0.5f,
            fingersExtended = false,
            pinchDistance = 0.14f
        )
        val fistOut = processor.processLandmarks(fist, nowMs = 1100L, mirrorX = true)
        assertEquals(HandGestureType.CLOSED_FIST, fistOut.gesture)
        assertTrue(fistOut.rotationPaused)

        // Pinch triggers once per cooldown
        val pinch = createMockHand(
            palmCenterX = 0.5f,
            palmCenterY = 0.5f,
            fingersExtended = true,
            pinchDistance = 0.02f
        )
        val pinchOut1 = processor.processLandmarks(pinch, nowMs = 2000L, mirrorX = true)
        assertEquals(HandGestureType.PINCH, pinchOut1.gesture)
        assertTrue(pinchOut1.pinchTriggered)

        val pinchOut2 = processor.processLandmarks(pinch, nowMs = 2200L, mirrorX = true)
        assertFalse(pinchOut2.pinchTriggered)

        // Hand disappears -> resets detection after timeout
        val lostOut = processor.processLandmarks(emptyList(), nowMs = 3500L, mirrorX = true)
        assertFalse(lostOut.handDetected)
        assertEquals(HandGestureType.NO_HAND, lostOut.gesture)
    }

    @Test
    fun testFingertipTranslationAndGestureZoomInOut() {
        val processor = HandGestureProcessor()

        // Pointing with index finger shifted far right (in camera space left x=0.15 -> mirrored x=0.85)
        val pointingRight = createPointingHand(tipX = 0.15f, tipY = 0.25f)
        var out = processor.processLandmarks(pointingRight, nowMs = 100L, mirrorX = true)
        assertEquals(HandGestureType.POINTING, out.gesture)
        repeat(6) { idx ->
            out = processor.processLandmarks(pointingRight, nowMs = 140L + idx * 33L, mirrorX = true)
        }
        assertTrue("Fingertip right should translate reactor +X", out.targetOffsetX > 0.25f)
        assertTrue("Fingertip upper should translate reactor +Y", out.targetOffsetY > 0.15f)
        assertTrue("Fingertip reticle X tracks mirrored coordinate", out.fingertipX > 0.7f)

        // 1-hand two-finger zoom pose: index+middle raised, ring+pinky curled
        val zoomBase = createZoomPoseHand(centerX = 0.5f, centerY = 0.5f, thumbIndexGap = 0.10f)
        val zoomSpread = createZoomPoseHand(centerX = 0.5f, centerY = 0.5f, thumbIndexGap = 0.24f)
        val zoomPinched = createZoomPoseHand(centerX = 0.5f, centerY = 0.5f, thumbIndexGap = 0.04f)

        val anchorOut = processor.processLandmarks(zoomBase, nowMs = 400L, mirrorX = true)
        assertEquals(HandGestureType.ZOOM, anchorOut.gesture)

        val zoomInOut = processor.processLandmarks(zoomSpread, nowMs = 450L, mirrorX = true)
        assertEquals(HandGestureType.ZOOM, zoomInOut.gesture)
        assertTrue("Spreading thumb+index zooms in (>1.0)", zoomInOut.targetZoom > 1.05f)

        // Re-anchor then test zoom out
        processor.reset()
        processor.processLandmarks(zoomSpread, nowMs = 600L, mirrorX = true)
        val zoomedOut = processor.processLandmarks(zoomPinched, nowMs = 650L, mirrorX = true)
        assertEquals(HandGestureType.ZOOM, zoomedOut.gesture)
        assertTrue("Narrowing thumb+index zooms out (<1.0)", zoomedOut.targetZoom < 0.95f)

        // 2-hand fingertip spread zoom in and out
        processor.reset()
        val handA1 = createMockHand(0.42f, 0.5f, fingersExtended = true, pinchDistance = 0.15f)
        val handB1 = createMockHand(0.58f, 0.5f, fingersExtended = true, pinchDistance = 0.15f)
        val twoHandAnchor = processor.processMultiHandLandmarks(listOf(handA1, handB1), nowMs = 1000L, mirrorX = true)
        assertEquals(HandGestureType.ZOOM, twoHandAnchor.gesture)
        assertEquals(21, twoHandAnchor.secondaryLandmarks.size)

        val handA2 = createMockHand(0.20f, 0.5f, fingersExtended = true, pinchDistance = 0.15f)
        val handB2 = createMockHand(0.80f, 0.5f, fingersExtended = true, pinchDistance = 0.15f)
        val twoHandZoomIn = processor.processMultiHandLandmarks(listOf(handA2, handB2), nowMs = 1040L, mirrorX = true)
        assertTrue("2-hand spread apart zooms in", twoHandZoomIn.targetZoom > 1.15f)

        val handA3 = createMockHand(0.48f, 0.5f, fingersExtended = true, pinchDistance = 0.15f)
        val handB3 = createMockHand(0.52f, 0.5f, fingersExtended = true, pinchDistance = 0.15f)
        val twoHandZoomOut = processor.processMultiHandLandmarks(listOf(handA3, handB3), nowMs = 1080L, mirrorX = true)
        assertTrue("2-hand moving together zooms out", twoHandZoomOut.targetZoom < twoHandZoomIn.targetZoom)
    }

    private fun createPointingHand(tipX: Float, tipY: Float): List<NormalizedLandmarkPoint> {
        val pts = createMockHand(tipX, tipY + 0.12f, fingersExtended = false, pinchDistance = 0.12f).toMutableList()
        // Only index extended
        pts[6] = NormalizedLandmarkPoint(tipX, tipY + 0.07f)
        pts[8] = NormalizedLandmarkPoint(tipX, tipY)
        pts[4] = NormalizedLandmarkPoint(tipX + 0.12f, tipY + 0.10f)
        return pts
    }

    private fun createZoomPoseHand(
        centerX: Float,
        centerY: Float,
        thumbIndexGap: Float
    ): List<NormalizedLandmarkPoint> {
        val pts = createMockHand(centerX, centerY, fingersExtended = false, pinchDistance = 0.15f).toMutableList()
        // Extend index (#8) and middle (#12), keep ring (#16) and pinky (#20) curled
        pts[6] = NormalizedLandmarkPoint(centerX - 0.05f, centerY - 0.06f)
        pts[8] = NormalizedLandmarkPoint(centerX - 0.05f, centerY - 0.16f)
        pts[10] = NormalizedLandmarkPoint(centerX + 0.01f, centerY - 0.06f)
        pts[12] = NormalizedLandmarkPoint(centerX + 0.01f, centerY - 0.17f)
        pts[4] = NormalizedLandmarkPoint(pts[8].x - thumbIndexGap, pts[8].y)
        return pts
    }

    private fun createMockHand(
        palmCenterX: Float,
        palmCenterY: Float,
        fingersExtended: Boolean,
        pinchDistance: Float
    ): List<NormalizedLandmarkPoint> {
        val pts = MutableList(21) { NormalizedLandmarkPoint(palmCenterX, palmCenterY, 0f) }
        // Wrist (0) and MCPs (5, 9, 13, 17)
        pts[0] = NormalizedLandmarkPoint(palmCenterX, palmCenterY + 0.12f)
        pts[5] = NormalizedLandmarkPoint(palmCenterX - 0.06f, palmCenterY)
        pts[9] = NormalizedLandmarkPoint(palmCenterX, palmCenterY - 0.02f) // palmScale = 0.14
        pts[13] = NormalizedLandmarkPoint(palmCenterX + 0.04f, palmCenterY)
        pts[17] = NormalizedLandmarkPoint(palmCenterX + 0.08f, palmCenterY + 0.02f)

        // PIPs
        val pipOffset = -0.05f
        pts[6] = NormalizedLandmarkPoint(palmCenterX - 0.06f, palmCenterY + pipOffset)
        pts[10] = NormalizedLandmarkPoint(palmCenterX, palmCenterY + pipOffset - 0.01f)
        pts[14] = NormalizedLandmarkPoint(palmCenterX + 0.04f, palmCenterY + pipOffset)
        pts[18] = NormalizedLandmarkPoint(palmCenterX + 0.08f, palmCenterY + pipOffset + 0.01f)

        // Tips
        val tipOffset = if (fingersExtended) -0.14f else 0.04f
        pts[8] = NormalizedLandmarkPoint(palmCenterX - 0.06f, palmCenterY + tipOffset)
        pts[12] = NormalizedLandmarkPoint(palmCenterX, palmCenterY + tipOffset - 0.01f)
        pts[16] = NormalizedLandmarkPoint(palmCenterX + 0.04f, palmCenterY + tipOffset)
        pts[20] = NormalizedLandmarkPoint(palmCenterX + 0.08f, palmCenterY + tipOffset + 0.01f)

        // Thumb tip relative to index tip
        pts[4] = NormalizedLandmarkPoint(pts[8].x - pinchDistance, pts[8].y)
        return pts
    }

    @Test
    fun testFaceSignatureMatcherDescriptorAndRecognition() {
        val operatorDescriptor = FaceSignatureMatcher.buildDescriptor(
            leftEye = FacePoint2D(140f, 120f),
            rightEye = FacePoint2D(220f, 120f),
            noseBase = FacePoint2D(180f, 165f),
            mouthLeft = FacePoint2D(150f, 205f),
            mouthRight = FacePoint2D(210f, 205f),
            mouthBottom = FacePoint2D(180f, 222f),
            leftCheek = FacePoint2D(125f, 165f),
            rightCheek = FacePoint2D(235f, 165f),
            boxWidth = 160f,
            boxHeight = 200f
        )
        assertEquals(12, operatorDescriptor.size)

        // Serialization round-trip preserves descriptor
        val json = FaceSignatureMatcher.serializeDescriptor(operatorDescriptor)
        val parsed = FaceSignatureMatcher.parseDescriptor(json)
        assertEquals(12, parsed.size)
        assertTrue(FaceSignatureMatcher.cosineSimilarity(operatorDescriptor, parsed) > 0.999f)

        // Uniformly scaled & translated same face matches enrolled profile cleanly
        val scaledOperator = FaceSignatureMatcher.buildDescriptor(
            leftEye = FacePoint2D(340f, 280f),
            rightEye = FacePoint2D(460f, 280f),
            noseBase = FacePoint2D(400f, 347.5f),
            mouthLeft = FacePoint2D(355f, 407.5f),
            mouthRight = FacePoint2D(445f, 407.5f),
            mouthBottom = FacePoint2D(400f, 433f),
            leftCheek = FacePoint2D(317.5f, 347.5f),
            rightCheek = FacePoint2D(482.5f, 347.5f),
            boxWidth = 240f,
            boxHeight = 300f
        )
        val profiles = listOf(
            FaceProfileSnapshot(
                id = 7L,
                personName = "Operator",
                role = "ADMIN",
                descriptor = parsed
            )
        )
        val matched = FaceSignatureMatcher.matchProfile(scaledOperator, profiles)
        assertEquals("Operator", matched?.personName)
        assertEquals(7L, matched?.profileId)
        assertTrue((matched?.confidence ?: 0f) > 0.99f)

        // Distinct face proportions do not falsely match
        val strangerDescriptor = FaceSignatureMatcher.buildDescriptor(
            leftEye = FacePoint2D(155f, 110f),
            rightEye = FacePoint2D(205f, 110f),
            noseBase = FacePoint2D(180f, 210f),
            mouthLeft = FacePoint2D(130f, 250f),
            mouthRight = FacePoint2D(230f, 250f),
            mouthBottom = FacePoint2D(180f, 285f),
            leftCheek = FacePoint2D(110f, 175f),
            rightCheek = FacePoint2D(250f, 175f),
            boxWidth = 175f,
            boxHeight = 250f
        )
        val strangerMatch = FaceSignatureMatcher.matchProfile(strangerDescriptor, profiles)
        assertNull(strangerMatch)
    }

    @Test
    fun testSquareRoiCoordinateUnmapping() {
        // Portrait 480x640 padded into 640x640 square -> padX = 80, padY = 0
        val portrait = SquareRoiTransform.fromDimensions(480, 640)
        assertEquals(640, portrait.squareSize)
        assertEquals(80f, portrait.padX, 0.01f)
        assertEquals(0f, portrait.padY, 0.01f)
        assertEquals(0.5f, portrait.unmapX(0.5f), 0.001f)
        assertEquals(0f, portrait.unmapX(80f / 640f), 0.001f)
        assertEquals(1f, portrait.unmapX(560f / 640f), 0.001f)
        assertEquals(0.25f, portrait.unmapY(0.25f), 0.001f)

        // Landscape 640x480 padded into 640x640 square -> padX = 0, padY = 80
        val landscape = SquareRoiTransform.fromDimensions(640, 480)
        assertEquals(640, landscape.squareSize)
        assertEquals(0f, landscape.padX, 0.01f)
        assertEquals(80f, landscape.padY, 0.01f)
        assertEquals(0.5f, landscape.unmapY(0.5f), 0.001f)
        assertEquals(0f, landscape.unmapY(80f / 640f), 0.001f)
        assertEquals(1f, landscape.unmapY(560f / 640f), 0.001f)
    }
}
