package com.example.domain.openjarvis

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

enum class OpenJarvisConnectionState(val badgeLabel: String) {
    CONNECTED("OPENJARVIS LINK"),
    ON_DEVICE("LOCAL CORE"),
    CLOUD_AUX("CLOUD AUX"),
    CHECKING("SYNCING"),
    OFFLINE("STANDBY")
}

data class OpenJarvisServerInfo(
    val reachable: Boolean,
    val status: String = "offline",
    val version: String = "",
    val activeEngine: String = "",
    val activeModel: String = "",
    val availableModels: List<String> = emptyList(),
    val availableAgents: List<String> = emptyList(),
    val availableSkills: List<String> = emptyList(),
    val error: String? = null
)

sealed class OpenJarvisStreamEvent {
    data class Token(val delta: String, val accumulated: String) : OpenJarvisStreamEvent()
    data class Completed(val fullText: String, val modelUsed: String, val latencyMs: Long) : OpenJarvisStreamEvent()
    data class Failure(val reason: String) : OpenJarvisStreamEvent()
}

/**
 * Android-compatible OpenJarvis HTTP/SSE bridge targeting `jarvis serve` FastAPI routes:
 * - GET  /health
 * - GET  /v1/info
 * - GET  /v1/models
 * - GET  /v1/agents
 * - GET  /v1/skills
 * - POST /v1/chat/completions (streaming & non-streaming OpenAI-compatible interface)
 * - POST /v1/memory/store
 */
class OpenJarvisBridge(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun probeServer(config: OpenJarvisConfig): OpenJarvisServerInfo = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(config.serverUrl) ?: return@withContext OpenJarvisServerInfo(
            reachable = false,
            error = "Invalid OpenJarvis server URL"
        )

        try {
            val healthReq = buildRequest("$baseUrl/health", config.apiToken).get().build()
            httpClient.newCall(healthReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    return@withContext OpenJarvisServerInfo(
                        reachable = false,
                        error = "Health check HTTP ${resp.code}"
                    )
                }
            }

            val infoJson = executeGetJson("$baseUrl/v1/info", config.apiToken)
            val modelsJson = executeGetJson("$baseUrl/v1/models", config.apiToken)
            val agentsJson = executeGetJson("$baseUrl/v1/agents", config.apiToken)
            val skillsJson = executeGetJson("$baseUrl/v1/skills", config.apiToken)

            val models = parseModelsResponse(modelsJson)
            val agents = parseNameList(agentsJson, arrayKey = "agents", fallbackKeys = listOf("id", "name", "agent_id"))
            val skills = parseNameList(skillsJson, arrayKey = "skills", fallbackKeys = listOf("name", "id"))

            val activeEngine = infoJson?.optString("engine", "").orEmpty()
                .ifBlank { infoJson?.optString("backend", "").orEmpty() }
            val activeModel = infoJson?.optString("model", "").orEmpty()
                .ifBlank { models.firstOrNull().orEmpty() }
            val version = infoJson?.optString("version", "").orEmpty()

            OpenJarvisServerInfo(
                reachable = true,
                status = "online",
                version = version,
                activeEngine = activeEngine,
                activeModel = activeModel,
                availableModels = models,
                availableAgents = agents,
                availableSkills = skills
            )
        } catch (e: Exception) {
            OpenJarvisServerInfo(
                reachable = false,
                error = e.localizedMessage ?: "Connection unreachable"
            )
        }
    }

    fun streamChatCompletion(
        config: OpenJarvisConfig,
        userPrompt: String,
        systemInstruction: String,
        conversationHistory: List<Pair<String, String>>,
        resolvedModelOverride: String? = null
    ): Flow<OpenJarvisStreamEvent> = flow {
        val baseUrl = normalizeBaseUrl(config.serverUrl)
        if (baseUrl == null) {
            emit(OpenJarvisStreamEvent.Failure("Invalid OpenJarvis server URL"))
            return@flow
        }

        val startMs = System.currentTimeMillis()
        val chosenModel = when {
            !resolvedModelOverride.isNullOrBlank() && resolvedModelOverride != "auto" -> resolvedModelOverride
            config.selectedModel.isNotBlank() && config.selectedModel != "auto" -> config.selectedModel
            else -> "default"
        }

        val payload = buildChatCompletionPayload(
            model = chosenModel,
            systemInstruction = systemInstruction,
            conversationHistory = conversationHistory,
            userPrompt = userPrompt,
            stream = true
        )

        val request = buildRequest("$baseUrl/v1/chat/completions", config.apiToken)
            .header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val err = response.body?.string()?.take(200) ?: "HTTP ${response.code}"
                    emit(OpenJarvisStreamEvent.Failure("OpenJarvis error (${response.code}): $err"))
                    return@flow
                }

                val body = response.body
                if (body == null) {
                    emit(OpenJarvisStreamEvent.Failure("Empty response stream from OpenJarvis"))
                    return@flow
                }

                val contentType = response.header("Content-Type").orEmpty()
                if (contentType.contains("application/json")) {
                    val raw = body.string()
                    val parsed = parseNonStreamingCompletion(raw)
                    if (parsed.isNotBlank()) {
                        emit(
                            OpenJarvisStreamEvent.Completed(
                                fullText = parsed,
                                modelUsed = chosenModel,
                                latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                            )
                        )
                    } else {
                        emit(OpenJarvisStreamEvent.Failure("Empty JSON completion from OpenJarvis"))
                    }
                    return@flow
                }

                val accumulated = StringBuilder()
                var actualModel = chosenModel
                BufferedReader(InputStreamReader(body.byteStream())).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val trimmed = line.trim()
                        if (trimmed.isEmpty() || trimmed.startsWith(":")) continue
                        if (!trimmed.startsWith("data:")) continue

                        val dataPayload = trimmed.removePrefix("data:").trim()
                        if (dataPayload == "[DONE]") break

                        val chunk = parseSseChunk(dataPayload)
                        if (chunk.model != null) {
                            actualModel = chunk.model
                        }
                        if (chunk.error != null) {
                            emit(OpenJarvisStreamEvent.Failure(chunk.error))
                            return@flow
                        }
                        if (!chunk.delta.isNullOrEmpty()) {
                            accumulated.append(chunk.delta)
                            emit(OpenJarvisStreamEvent.Token(chunk.delta, accumulated.toString()))
                        }
                    }
                }

                val finalText = accumulated.toString().trim()
                if (finalText.isNotEmpty()) {
                    emit(
                        OpenJarvisStreamEvent.Completed(
                            fullText = finalText,
                            modelUsed = actualModel,
                            latencyMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                        )
                    )
                } else {
                    emit(OpenJarvisStreamEvent.Failure("OpenJarvis stream closed without content"))
                }
            }
        } catch (e: Exception) {
            emit(OpenJarvisStreamEvent.Failure(e.localizedMessage ?: "OpenJarvis request failed"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun syncMemoryToServer(
        config: OpenJarvisConfig,
        key: String,
        value: String
    ): Boolean = withContext(Dispatchers.IO) {
        val baseUrl = normalizeBaseUrl(config.serverUrl) ?: return@withContext false
        return@withContext try {
            val payload = JSONObject().apply {
                put("key", key)
                put("content", value)
                put("source", "android_jarvis_x")
            }
            val req = buildRequest("$baseUrl/v1/memory/store", config.apiToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()
            httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    data class SqlSyncResult(
        val success: Boolean,
        val syncedCount: Int = 0,
        val message: String = ""
    )

    suspend fun syncSqlTelemetry(
        config: OpenJarvisConfig,
        fingertipMovements: List<com.example.data.model.FingertipMovementEntity>,
        faceEvents: List<com.example.data.model.FaceRecognitionEventEntity>,
        logReports: List<com.example.data.model.LogReportEntity>
    ): SqlSyncResult = withContext(Dispatchers.IO) {
        val total = fingertipMovements.size + faceEvents.size + logReports.size
        if (total == 0) {
            return@withContext SqlSyncResult(success = true, syncedCount = 0, message = "All SQL records already synced")
        }
        val baseUrl = normalizeBaseUrl(config.serverUrl)
            ?: return@withContext SqlSyncResult(success = false, message = "Invalid OpenJarvis server URL")

        return@withContext try {
            val payload = JSONObject().apply {
                put("source", "android_jarvis_x")
                put("schema", "postgresql_compatible_v1")
                put("fingertip_movements", JSONArray().apply {
                    fingertipMovements.forEach { m ->
                        put(JSONObject().apply {
                            put("local_id", m.id)
                            put("session_id", m.sessionId)
                            put("gesture", m.gesture)
                            put("fingertip_x", m.fingertipX.toDouble())
                            put("fingertip_y", m.fingertipY.toDouble())
                            put("offset_x", m.offsetX.toDouble())
                            put("offset_y", m.offsetY.toDouble())
                            put("yaw_deg", m.yawDeg.toDouble())
                            put("pitch_deg", m.pitchDeg.toDouble())
                            put("zoom_scale", m.zoomScale.toDouble())
                            put("hand_count", m.handCount)
                            put("timestamp_ms", m.timestamp)
                        })
                    }
                })
                put("facial_recognition_events", JSONArray().apply {
                    faceEvents.forEach { f ->
                        put(JSONObject().apply {
                            put("local_id", f.id)
                            put("matched_profile_id", f.matchedProfileId)
                            put("recognized_label", f.recognizedLabel)
                            put("confidence", f.confidence.toDouble())
                            put("smiling_prob", f.smilingProbability?.toDouble())
                            put("left_eye_open_prob", f.leftEyeOpenProbability?.toDouble())
                            put("right_eye_open_prob", f.rightEyeOpenProbability?.toDouble())
                            put("head_yaw_deg", f.headYawDeg.toDouble())
                            put("head_pitch_deg", f.headPitchDeg.toDouble())
                            put("head_roll_deg", f.headRollDeg.toDouble())
                            put("bounding_box_summary", f.boundingBoxSummary)
                            put("status", f.status)
                            put("timestamp_ms", f.timestamp)
                        })
                    }
                })
                put("log_reports", JSONArray().apply {
                    logReports.forEach { r ->
                        put(JSONObject().apply {
                            put("local_id", r.id)
                            put("category", r.category)
                            put("severity", r.severity)
                            put("title", r.title)
                            put("summary", r.summary)
                            put("metrics_json", r.metricsJson)
                            put("timestamp_ms", r.timestamp)
                        })
                    }
                })
            }

            val req = buildRequest("$baseUrl/v1/telemetry/sql", config.apiToken)
                .post(payload.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    SqlSyncResult(success = true, syncedCount = total, message = "Synced $total SQL records to $baseUrl")
                } else {
                    SqlSyncResult(success = false, message = "Server HTTP ${resp.code}")
                }
            }
        } catch (e: Exception) {
            SqlSyncResult(success = false, message = e.localizedMessage ?: "Companion SQL sync unreachable")
        }
    }

    private fun executeGetJson(url: String, token: String): JSONObject? {
        return try {
            val req = buildRequest(url, token).get().build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val body = resp.body?.string()?.trim() ?: return null
                if (body.startsWith("[")) {
                    JSONObject().put("items", JSONArray(body))
                } else {
                    JSONObject(body)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequest(url: String, apiToken: String): Request.Builder {
        val builder = Request.Builder().url(url)
        if (apiToken.isNotBlank()) {
            builder.header("Authorization", "Bearer ${apiToken.trim()}")
        }
        return builder
    }

    companion object {
        fun normalizeBaseUrl(raw: String): String? {
            val trimmed = raw.trim().trimEnd('/')
            if (trimmed.isBlank()) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                trimmed
            } else {
                "http://$trimmed"
            }
            return if (withScheme.length > 8) withScheme else null
        }

        fun buildChatCompletionPayload(
            model: String,
            systemInstruction: String,
            conversationHistory: List<Pair<String, String>>,
            userPrompt: String,
            stream: Boolean
        ): JSONObject {
            val messagesArray = JSONArray().apply {
                if (systemInstruction.isNotBlank()) {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                }
                for ((role, content) in conversationHistory.takeLast(8)) {
                    put(JSONObject().apply {
                        put("role", if (role.equals("user", ignoreCase = true)) "user" else "assistant")
                        put("content", content)
                    })
                }
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }

            return JSONObject().apply {
                if (model.isNotBlank() && model != "default" && model != "auto") {
                    put("model", model)
                }
                put("messages", messagesArray)
                put("stream", stream)
                put("temperature", 0.65)
            }
        }

        fun parseModelsResponse(json: JSONObject?): List<String> {
            if (json == null) return emptyList()
            val array = json.optJSONArray("data")
                ?: json.optJSONArray("models")
                ?: json.optJSONArray("items")
                ?: return emptyList()

            val result = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                val name = when (item) {
                    is JSONObject -> item.optString("id").ifBlank { item.optString("name") }
                    is String -> item
                    else -> ""
                }
                if (name.isNotBlank()) result.add(name)
            }
            return result.distinct()
        }

        private fun parseNameList(
            json: JSONObject?,
            arrayKey: String,
            fallbackKeys: List<String>
        ): List<String> {
            if (json == null) return emptyList()
            val array = json.optJSONArray(arrayKey)
                ?: json.optJSONArray("items")
                ?: json.optJSONArray("data")
                ?: return emptyList()
            val result = mutableListOf<String>()
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                val label = when (item) {
                    is JSONObject -> fallbackKeys.firstNotNullOfOrNull { k ->
                        item.optString(k).takeIf { it.isNotBlank() }
                    }.orEmpty()
                    is String -> item
                    else -> ""
                }
                if (label.isNotBlank()) result.add(label)
            }
            return result.distinct()
        }

        data class SseChunk(
            val delta: String? = null,
            val model: String? = null,
            val error: String? = null
        )

        fun parseSseChunk(dataJson: String): SseChunk {
            return try {
                val obj = JSONObject(dataJson)
                val errObj = obj.optJSONObject("error")
                if (errObj != null) {
                    return SseChunk(error = errObj.optString("message", "OpenJarvis inference error"))
                }
                val model = obj.optString("model").takeIf { it.isNotBlank() }
                val choices = obj.optJSONArray("choices")
                val first = choices?.optJSONObject(0)
                val deltaObj = first?.optJSONObject("delta")
                val messageObj = first?.optJSONObject("message")
                val deltaText = deltaObj?.optString("content").orEmpty()
                    .ifEmpty { messageObj?.optString("content").orEmpty() }
                    .ifEmpty { obj.optString("delta").orEmpty() }
                SseChunk(delta = deltaText.takeIf { it.isNotEmpty() }, model = model)
            } catch (_: Exception) {
                SseChunk()
            }
        }

        fun parseNonStreamingCompletion(rawJson: String): String {
            return try {
                val obj = JSONObject(rawJson)
                val choices = obj.optJSONArray("choices")
                val first = choices?.optJSONObject(0)
                val msg = first?.optJSONObject("message")
                msg?.optString("content")?.trim()
                    ?: first?.optString("text")?.trim()
                    ?: obj.optString("response").trim()
            } catch (_: Exception) {
                ""
            }
        }
    }
}
