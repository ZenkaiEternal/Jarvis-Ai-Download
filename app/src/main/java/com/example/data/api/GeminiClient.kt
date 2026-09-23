package com.example.data.api

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateContent(
        prompt: String,
        systemInstruction: String,
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): GeminiResponse = withContext(Dispatchers.IO) {
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext GeminiResponse(
                success = false,
                text = "",
                error = "API_KEY_UNCONFIGURED",
                reasoning = "Auxiliary neural engine active. Real-time Gemini uplink awaiting API key."
            )
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        try {
            val root = JSONObject()

            // System instruction
            val sysInstructionObj = JSONObject()
            val sysParts = JSONArray()
            sysParts.put(JSONObject().put("text", systemInstruction))
            sysInstructionObj.put("parts", sysParts)
            root.put("systemInstruction", sysInstructionObj)

            // Contents (history + current)
            val contentsArray = JSONArray()

            // Add recent history (up to last 6 turns)
            for ((role, text) in conversationHistory.takeLast(6)) {
                val turnObj = JSONObject()
                turnObj.put("role", if (role.equals("user", ignoreCase = true)) "user" else "model")
                val partsArray = JSONArray()
                partsArray.put(JSONObject().put("text", text))
                turnObj.put("parts", partsArray)
                contentsArray.put(turnObj)
            }

            // Current prompt
            val currentTurn = JSONObject()
            currentTurn.put("role", "user")
            val currentParts = JSONArray()
            currentParts.put(JSONObject().put("text", prompt))
            currentTurn.put("parts", currentParts)
            contentsArray.put(currentTurn)

            root.put("contents", contentsArray)

            // Config
            val config = JSONObject()
            config.put("temperature", 0.7)
            config.put("maxOutputTokens", 1024)
            root.put("generationConfig", config)

            val requestBody = root.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e("GeminiClient", "API Error ${response.code}: $responseBody")
                return@withContext GeminiResponse(
                    success = false,
                    text = "",
                    error = "HTTP ${response.code}: $responseBody",
                    reasoning = "Diagnostics report uplink error code ${response.code}."
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textBuilder = StringBuilder()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val p = parts.getJSONObject(i)
                    textBuilder.append(p.optString("text"))
                }
            }

            val resultText = textBuilder.toString().trim()
            GeminiResponse(
                success = resultText.isNotEmpty(),
                text = resultText,
                reasoning = "Synthesized via Gemini-3.5-Flash neural model with multi-turn context."
            )
        } catch (e: Exception) {
            Log.e("GeminiClient", "Network exception", e)
            GeminiResponse(
                success = false,
                text = "",
                error = e.localizedMessage ?: "Network anomaly detected",
                reasoning = "Network uplink interrupted. Fallback to local cognitive routines."
            )
        }
    }
}

data class GeminiResponse(
    val success: Boolean,
    val text: String,
    val reasoning: String = "",
    val error: String? = null
)
