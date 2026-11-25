package com.example.domain.vision

import org.json.JSONArray
import kotlin.math.hypot
import kotlin.math.sqrt

data class FacePoint2D(
    val x: Float,
    val y: Float
)

data class FaceProfileSnapshot(
    val id: Long,
    val personName: String,
    val role: String,
    val descriptor: List<Float>
)

data class FaceMatchResult(
    val profileId: Long,
    val personName: String,
    val role: String,
    val confidence: Float
)

data class NormalizedFaceBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class FaceRecognitionOutput(
    val faceDetected: Boolean = false,
    val matchedProfileId: Long? = null,
    val recognizedLabel: String = "NO FACE",
    val confidence: Float = 0f,
    val smilingProbability: Float? = null,
    val leftEyeOpenProbability: Float? = null,
    val rightEyeOpenProbability: Float? = null,
    val headYawDeg: Float = 0f,
    val headPitchDeg: Float = 0f,
    val headRollDeg: Float = 0f,
    val normalizedBox: NormalizedFaceBox? = null,
    val descriptor: List<Float> = emptyList(),
    val status: String = "STANDBY"
)

/**
 * Pure-Kotlin scale- and translation-invariant geometric facial signature generator and matcher.
 * Computes L2-normalized facial proportion vectors from landmark geometry without storing raw face photos.
 */
object FaceSignatureMatcher {

    const val DEFAULT_MATCH_THRESHOLD = 0.85f

    private val CANONICAL_BASELINE = floatArrayOf(
        0.50f, // eyeSpan / safeW
        0.80f, // safeW / safeH
        0.75f, // mouthSpan / eyeSpan
        0.56f, // eyeToNose / eyeSpan
        0.50f, // noseToMouth / eyeSpan
        0.42f, // eyeToMouth / safeH
        1.00f, // leftEyeToNose / rightEyeToNose
        1.12f, // leftEyeToMouthLeft / eyeSpan
        1.12f, // rightEyeToMouthRight / eyeSpan
        0.22f, // mouthDepth / eyeSpan
        1.38f, // cheekSpan / eyeSpan
        0.45f  // avgEyeToMouth / safeH
    )

    fun buildDescriptor(
        leftEye: FacePoint2D,
        rightEye: FacePoint2D,
        noseBase: FacePoint2D,
        mouthLeft: FacePoint2D,
        mouthRight: FacePoint2D,
        mouthBottom: FacePoint2D?,
        leftCheek: FacePoint2D?,
        rightCheek: FacePoint2D?,
        boxWidth: Float,
        boxHeight: Float
    ): List<Float> {
        val eyeSpan = hypot(rightEye.x - leftEye.x, rightEye.y - leftEye.y).coerceAtLeast(1f)
        val safeW = boxWidth.coerceAtLeast(eyeSpan)
        val safeH = boxHeight.coerceAtLeast(eyeSpan)
        val eyeMidX = (leftEye.x + rightEye.x) * 0.5f
        val eyeMidY = (leftEye.y + rightEye.y) * 0.5f
        val mouthMidX = (mouthLeft.x + mouthRight.x) * 0.5f
        val mouthMidY = (mouthLeft.y + mouthRight.y) * 0.5f

        val mouthSpan = hypot(mouthRight.x - mouthLeft.x, mouthRight.y - mouthLeft.y)
        val eyeToNose = hypot(noseBase.x - eyeMidX, noseBase.y - eyeMidY)
        val noseToMouth = hypot(mouthMidX - noseBase.x, mouthMidY - noseBase.y)
        val eyeToMouth = hypot(mouthMidX - eyeMidX, mouthMidY - eyeMidY)
        val leftEyeToNose = hypot(noseBase.x - leftEye.x, noseBase.y - leftEye.y)
        val rightEyeToNose = hypot(noseBase.x - rightEye.x, noseBase.y - rightEye.y)
        val leftEyeToMouthLeft = hypot(mouthLeft.x - leftEye.x, mouthLeft.y - leftEye.y)
        val rightEyeToMouthRight = hypot(mouthRight.x - rightEye.x, mouthRight.y - rightEye.y)
        val mouthDepth = if (mouthBottom != null) {
            hypot(mouthBottom.x - mouthMidX, mouthBottom.y - mouthMidY)
        } else {
            noseToMouth * 0.45f
        }
        val cheekSpan = if (leftCheek != null && rightCheek != null) {
            hypot(rightCheek.x - leftCheek.x, rightCheek.y - leftCheek.y)
        } else {
            safeW * 0.72f
        }

        val raw = listOf(
            eyeSpan / safeW,
            safeW / safeH,
            mouthSpan / eyeSpan,
            eyeToNose / eyeSpan,
            noseToMouth / eyeSpan,
            eyeToMouth / safeH,
            leftEyeToNose / rightEyeToNose.coerceAtLeast(1f),
            leftEyeToMouthLeft / eyeSpan,
            rightEyeToMouthRight / eyeSpan,
            mouthDepth / eyeSpan,
            cheekSpan / eyeSpan,
            (leftEyeToMouthLeft + rightEyeToMouthRight) / (2f * safeH)
        )
        val centered = raw.mapIndexed { idx, value -> value - CANONICAL_BASELINE[idx] }
        return l2Normalize(centered)
    }

    fun l2Normalize(values: List<Float>): List<Float> {
        if (values.isEmpty()) return emptyList()
        var sumSq = 0.0
        for (v in values) {
            sumSq += (v * v).toDouble()
        }
        val norm = sqrt(sumSq).toFloat()
        if (norm <= 1e-6f) return values.map { 0f }
        return values.map { it / norm }
    }

    fun cosineSimilarity(a: List<Float>, b: List<Float>): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in a.indices) {
            val va = a[i].toDouble()
            val vb = b[i].toDouble()
            dot += va * vb
            magA += va * va
            magB += vb * vb
        }
        val denom = sqrt(magA) * sqrt(magB)
        if (denom <= 1e-8) return 0f
        return (dot / denom).toFloat().coerceIn(-1f, 1f)
    }

    fun matchProfile(
        descriptor: List<Float>,
        profiles: List<FaceProfileSnapshot>,
        minSimilarity: Float = DEFAULT_MATCH_THRESHOLD
    ): FaceMatchResult? {
        if (descriptor.isEmpty() || profiles.isEmpty()) return null
        var best: FaceMatchResult? = null
        for (profile in profiles) {
            val sim = cosineSimilarity(descriptor, profile.descriptor)
            if (sim >= minSimilarity && (best == null || sim > best.confidence)) {
                best = FaceMatchResult(
                    profileId = profile.id,
                    personName = profile.personName,
                    role = profile.role,
                    confidence = sim
                )
            }
        }
        return best
    }

    fun serializeDescriptor(descriptor: List<Float>): String {
        val arr = JSONArray()
        for (v in descriptor) {
            arr.put(v.toDouble())
        }
        return arr.toString()
    }

    fun parseDescriptor(json: String): List<Float> {
        return try {
            val arr = JSONArray(json)
            List(arr.length()) { idx -> arr.optDouble(idx, 0.0).toFloat() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Companion PostgreSQL (Cloud SQL) DDL schema mirroring Android Room SQLite tables
     * for OpenJarvis /v1/telemetry/sql server-side ingestion.
     */
    val POSTGRES_DDL_SCHEMA = """
        CREATE TABLE IF NOT EXISTS fingertip_movements (
            id BIGSERIAL PRIMARY KEY,
            session_id TEXT NOT NULL,
            gesture TEXT NOT NULL,
            fingertip_x REAL NOT NULL,
            fingertip_y REAL NOT NULL,
            offset_x REAL NOT NULL,
            offset_y REAL NOT NULL,
            yaw_deg REAL NOT NULL,
            pitch_deg REAL NOT NULL,
            zoom_scale REAL NOT NULL,
            hand_count INTEGER NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS face_profiles (
            id BIGSERIAL PRIMARY KEY,
            person_name TEXT UNIQUE NOT NULL,
            role TEXT NOT NULL,
            signature_json JSONB NOT NULL,
            enrolled_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
            last_seen_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS facial_recognition_events (
            id BIGSERIAL PRIMARY KEY,
            matched_profile_id BIGINT REFERENCES face_profiles(id),
            recognized_label TEXT NOT NULL,
            confidence REAL NOT NULL,
            smiling_prob REAL,
            left_eye_open_prob REAL,
            right_eye_open_prob REAL,
            head_yaw_deg REAL NOT NULL,
            head_pitch_deg REAL NOT NULL,
            head_roll_deg REAL NOT NULL,
            bounding_box_summary TEXT NOT NULL,
            status TEXT NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );

        CREATE TABLE IF NOT EXISTS log_reports (
            id BIGSERIAL PRIMARY KEY,
            category TEXT NOT NULL,
            severity TEXT NOT NULL,
            title TEXT NOT NULL,
            summary TEXT NOT NULL,
            metrics_json JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
    """.trimIndent()
}
