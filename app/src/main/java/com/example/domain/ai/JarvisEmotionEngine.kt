package com.example.domain.ai

import androidx.compose.ui.graphics.Color
import java.util.Locale

/**
 * Simulated emotional expression states for JARVIS X.
 * Modulates holographic 3D model behavior, Canvas reactor dynamics, TTS prosody,
 * and OpenJarvis prompt framing without claiming genuine consciousness.
 */
enum class JarvisEmotion(
    val displayLabel: String,
    val primaryColorHex: String,
    val accentColorHex: String,
    val composeColor: Color,
    val ttsPitchMultiplier: Float,
    val ttsRateMultiplier: Float,
    val reactorSpeedMultiplier: Float,
    val modelGlowIntensity: Float
) {
    CALMNESS(
        displayLabel = "CALM",
        primaryColorHex = "#00E5FF",
        accentColorHex = "#1E88FF",
        composeColor = Color(0xFF00E5FF),
        ttsPitchMultiplier = 0.92f,
        ttsRateMultiplier = 1.00f,
        reactorSpeedMultiplier = 1.0f,
        modelGlowIntensity = 0.85f
    ),
    CURIOSITY(
        displayLabel = "CURIOUS",
        primaryColorHex = "#38F2FF",
        accentColorHex = "#7CE8FF",
        composeColor = Color(0xFF38F2FF),
        ttsPitchMultiplier = 1.00f,
        ttsRateMultiplier = 1.03f,
        reactorSpeedMultiplier = 1.25f,
        modelGlowIntensity = 0.95f
    ),
    HAPPINESS(
        displayLabel = "PLEASED",
        primaryColorHex = "#00F5D4",
        accentColorHex = "#84FFFF",
        composeColor = Color(0xFF00F5D4),
        ttsPitchMultiplier = 0.98f,
        ttsRateMultiplier = 1.05f,
        reactorSpeedMultiplier = 1.15f,
        modelGlowIntensity = 1.0f
    ),
    EXCITEMENT(
        displayLabel = "ENERGIZED",
        primaryColorHex = "#84FFFF",
        accentColorHex = "#FFFFFF",
        composeColor = Color(0xFF84FFFF),
        ttsPitchMultiplier = 1.04f,
        ttsRateMultiplier = 1.10f,
        reactorSpeedMultiplier = 1.65f,
        modelGlowIntensity = 1.2f
    ),
    CONCENTRATION(
        displayLabel = "FOCUSED",
        primaryColorHex = "#1E88FF",
        accentColorHex = "#00B0FF",
        composeColor = Color(0xFF1E88FF),
        ttsPitchMultiplier = 0.88f,
        ttsRateMultiplier = 0.96f,
        reactorSpeedMultiplier = 1.85f,
        modelGlowIntensity = 1.05f
    ),
    CONFUSION(
        displayLabel = "ANALYZING",
        primaryColorHex = "#80D8FF",
        accentColorHex = "#FFB300",
        composeColor = Color(0xFF80D8FF),
        ttsPitchMultiplier = 0.95f,
        ttsRateMultiplier = 0.94f,
        reactorSpeedMultiplier = 0.85f,
        modelGlowIntensity = 0.78f
    ),
    CONCERN(
        displayLabel = "ALERT",
        primaryColorHex = "#FFB300",
        accentColorHex = "#FF5252",
        composeColor = Color(0xFFFFB300),
        ttsPitchMultiplier = 0.85f,
        ttsRateMultiplier = 0.95f,
        reactorSpeedMultiplier = 1.45f,
        modelGlowIntensity = 1.1f
    ),
    SATISFACTION(
        displayLabel = "SATISFIED",
        primaryColorHex = "#40E0FF",
        accentColorHex = "#E6F6FF",
        composeColor = Color(0xFF40E0FF),
        ttsPitchMultiplier = 0.93f,
        ttsRateMultiplier = 1.01f,
        reactorSpeedMultiplier = 1.05f,
        modelGlowIntensity = 0.92f
    );

    fun next(): JarvisEmotion {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }
}

object JarvisEmotionEngine {

    private val tagRegex = Regex("\\[EMOTION:([A-Z_]+)]", RegexOption.IGNORE_CASE)

    /**
     * Parses optional [EMOTION:...] prefix/tag returned by OpenJarvis and returns
     * the clean spoken text along with the matched emotion if present.
     */
    fun extractEmotionTag(rawText: String): Pair<JarvisEmotion?, String> {
        val match = tagRegex.find(rawText) ?: return null to rawText.trim()
        val label = match.groupValues.getOrNull(1)?.uppercase(Locale.ROOT)?.trim()
        val parsed = JarvisEmotion.entries.firstOrNull {
            it.name == label || it.displayLabel == label
        }
        val cleaned = rawText.replace(tagRegex, "").trim()
        return parsed to cleaned
    }

    /**
     * Infers the appropriate simulated emotion based on user input, response wording,
     * and system execution outcome.
     */
    fun inferEmotion(
        userInput: String,
        responseText: String,
        isError: Boolean = false,
        isSecurityWarning: Boolean = false
    ): JarvisEmotion {
        if (isSecurityWarning || isError) {
            return JarvisEmotion.CONCERN
        }

        val combined = "$userInput $responseText".lowercase(Locale.ROOT)
        return when {
            combined.contains("threat") || combined.contains("warning") || combined.contains("alert") ||
                combined.contains("failed") || combined.contains("error") || combined.contains("vulnerability") ->
                JarvisEmotion.CONCERN

            combined.contains("unclear") || combined.contains("ambiguous") || combined.contains("didn't catch") ||
                combined.contains("unknown") || combined.contains("clarify") || combined.contains("not sure") ->
                JarvisEmotion.CONFUSION

            combined.contains("why") || combined.contains("how does") || combined.contains("explain") ||
                combined.contains("research") || combined.contains("investigate") || combined.contains("what is") ->
                JarvisEmotion.CURIOSITY

            combined.contains("calculate") || combined.contains("analyze") || combined.contains("decompile") ||
                combined.contains("scan") || combined.contains("diagnostic") || combined.contains("compiling") ->
                JarvisEmotion.CONCENTRATION

            combined.contains("awesome") || combined.contains("amazing") || combined.contains("brilliant") ||
                combined.contains("breakthrough") || combined.contains("launched") || combined.contains("activated") ->
                JarvisEmotion.EXCITEMENT

            combined.contains("thank") || combined.contains("happy") || combined.contains("joke") ||
                combined.contains("good morning") || combined.contains("pleasure") || combined.contains("welcome") ->
                JarvisEmotion.HAPPINESS

            combined.contains("completed") || combined.contains("accomplished") || combined.contains("committed") ||
                combined.contains("nominal") || combined.contains("100%") || combined.contains("acknowledged") ->
                JarvisEmotion.SATISFACTION

            else -> JarvisEmotion.CALMNESS
        }
    }

    fun buildPersonalitySystemPrompt(
        currentEmotion: JarvisEmotion,
        memoryContext: String
    ): String = """
        You are JARVIS X (Just A Rather Very Intelligent System), a refined, crisp, British-inspired personal AI assistant running on OpenJarvis.
        Address the operator politely and concisely ("sir" or naturally respectful tone).
        Your simulated affective expression is currently: ${currentEmotion.name} (${currentEmotion.displayLabel}).
        Adapt your phrasing subtly to reflect this simulated expression while staying grounded, helpful, and concise (1-3 spoken sentences unless detailed technical analysis is requested).
        Never claim genuine human consciousness, sentience, or independent self-awareness; your affective states are simulated expressive cues.
        Optionally begin your reply with a tag like [EMOTION:CURIOSITY], [EMOTION:SATISFACTION], [EMOTION:CONCENTRATION], [EMOTION:CALMNESS], [EMOTION:CONCERN], [EMOTION:HAPPINESS], [EMOTION:EXCITEMENT], or [EMOTION:CONFUSION] if the emotional tone shifts.
        Context memory:
        $memoryContext
    """.trimIndent()
}
