package com.example.domain.ai

import android.content.Context
import com.example.data.api.GeminiClient
import com.example.data.model.ChatMessage
import com.example.data.model.MessageSender
import com.example.data.repository.JarvisRepository
import com.example.domain.openjarvis.OpenJarvisBridge
import com.example.domain.openjarvis.OpenJarvisConfig
import com.example.domain.openjarvis.OpenJarvisConnectionState
import com.example.domain.openjarvis.OpenJarvisInferenceMode
import com.example.domain.openjarvis.OpenJarvisRuntime
import com.example.domain.openjarvis.OpenJarvisStreamEvent
import com.example.domain.openjarvis.OpenJarvisTrace
import com.example.domain.security.AntiVirusEngine
import com.example.domain.security.ConfirmationRequest
import com.example.domain.security.SecurityEngine
import com.example.domain.tools.AntiVirusTool
import com.example.domain.tools.AppLauncherTool
import com.example.domain.tools.CalculatorTool
import com.example.domain.tools.FileAnalysisTool
import com.example.domain.tools.JarvisTool
import com.example.domain.tools.NotesAndRemindersTool
import com.example.domain.tools.SystemControlTool
import com.example.domain.tools.WeatherTool
import com.example.domain.tools.WebSearchTool
import kotlinx.coroutines.flow.first
import java.util.Locale

data class BrainExecutionResult(
    val message: ChatMessage,
    val emotion: JarvisEmotion,
    val connectionState: OpenJarvisConnectionState
)

class JarvisBrain(
    private val context: Context,
    private val repository: JarvisRepository,
    private val geminiClient: GeminiClient,
    private val securityEngine: SecurityEngine,
    private val weatherTool: WeatherTool,
    val openJarvisBridge: OpenJarvisBridge = OpenJarvisBridge()
) {
    val antiVirusEngine = AntiVirusEngine(context)

    private val tools: List<JarvisTool> = listOf(
        weatherTool,
        CalculatorTool(),
        WebSearchTool(),
        AppLauncherTool(),
        SystemControlTool(),
        NotesAndRemindersTool(repository),
        FileAnalysisTool(),
        AntiVirusTool(antiVirusEngine)
    )

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    suspend fun processUserQuery(
        userInput: String,
        config: OpenJarvisConfig = OpenJarvisConfig(),
        currentEmotion: JarvisEmotion = JarvisEmotion.CALMNESS,
        onStreamDelta: (String) -> Unit = {},
        onRequestConfirmation: (ConfirmationRequest) -> Unit
    ): ChatMessage {
        return processUserQueryWithTelemetry(
            userInput = userInput,
            config = config,
            currentEmotion = currentEmotion,
            onStreamDelta = onStreamDelta,
            onRequestConfirmation = onRequestConfirmation
        ).message
    }

    suspend fun processUserQueryWithTelemetry(
        userInput: String,
        config: OpenJarvisConfig = OpenJarvisConfig(),
        currentEmotion: JarvisEmotion = JarvisEmotion.CALMNESS,
        onStreamDelta: (String) -> Unit = {},
        onRequestConfirmation: (ConfirmationRequest) -> Unit
    ): BrainExecutionResult {
        val startMs = System.currentTimeMillis()
        val input = userInput.trim()

        // 1. Security validation
        val validation = securityEngine.validateInput(input)
        if (!validation.isValid) {
            return BrainExecutionResult(
                message = ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "Security alert: ${validation.message}",
                    highLevelReasoning = "Security boundary check failed. Execution halted."
                ),
                emotion = JarvisEmotion.CONCERN,
                connectionState = OpenJarvisConnectionState.ON_DEVICE
            )
        }

        val normalizedInput = stripConversationalFillers(input)
        val lower = normalizedInput.lowercase(Locale.ROOT)
        val rawLower = input.lowercase(Locale.ROOT)

        // 2. Destructive memory purge confirmation gate
        if (lower.contains("wipe memory") || lower.contains("clear memory") || lower.contains("delete all memories") || lower.contains("purge memories")) {
            val request = ConfirmationRequest(
                title = "CONFIRM MEMORY PURGE",
                description = "You have requested a complete wipe of all stored directives. This action is irreversible. Proceed?",
                onConfirm = {
                    repository.clearMemories()
                    securityEngine.auditAction("PURGE_MEMORIES", "User authorized full memory wipe.", true)
                },
                onCancel = { }
            )
            onRequestConfirmation(request)
            return BrainExecutionResult(
                message = ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "Confirmation required, sir. Purging core memory will permanently erase stored directives.",
                    highLevelReasoning = "Consequential protocol triggered: Memory purge verification requested."
                ),
                emotion = JarvisEmotion.CONCERN,
                connectionState = OpenJarvisConnectionState.ON_DEVICE
            )
        }

        // 3. Local & OpenJarvis Memory Storage
        if (lower.startsWith("remember that") || lower.startsWith("remember:") || lower.startsWith("keep in mind that") || lower.startsWith("remember ")) {
            val fact = normalizedInput
                .replace("remember that", "", ignoreCase = true)
                .replace("remember:", "", ignoreCase = true)
                .replace("keep in mind that", "", ignoreCase = true)
                .replaceFirst(Regex("^remember\\s+", RegexOption.IGNORE_CASE), "")
                .trim()

            if (fact.isNotEmpty()) {
                val key = "FACT_${System.currentTimeMillis() % 10000}"
                repository.saveMemory(key = key, value = fact, category = "USER_DIRECTIVE")
                if (config.inferenceMode != OpenJarvisInferenceMode.ON_DEVICE_ONLY) {
                    openJarvisBridge.syncMemoryToServer(config, key, fact)
                }
                securityEngine.auditAction("SAVE_MEMORY", "Stored: $fact", true)
                recordTrace("LOCAL_TOOL", input, "Save Memory", startMs, true)
                return BrainExecutionResult(
                    message = ChatMessage(
                        sender = MessageSender.JARVIS,
                        text = "Acknowledged, sir. Committed to core memory: \"$fact\".",
                        highLevelReasoning = "Memory bank updated with user directive.",
                        actionTaken = "Committed to Memory Vault"
                    ),
                    emotion = JarvisEmotion.SATISFACTION,
                    connectionState = OpenJarvisConnectionState.ON_DEVICE
                )
            }
        }

        // 4. Memory Query Check
        if (lower.contains("what do you remember") || lower.contains("recall preferences") || lower.contains("my memory")) {
            val memories = repository.memories.first()
            recordTrace("LOCAL_TOOL", input, "Query Memory", startMs, true)
            return if (memories.isEmpty()) {
                BrainExecutionResult(
                    message = ChatMessage(
                        sender = MessageSender.JARVIS,
                        text = "My memory archives are currently clear, sir.",
                        highLevelReasoning = "Queried memory bank. Found 0 records."
                    ),
                    emotion = JarvisEmotion.CALMNESS,
                    connectionState = OpenJarvisConnectionState.ON_DEVICE
                )
            } else {
                val formatted = memories.take(4).joinToString("; ") { it.value }
                BrainExecutionResult(
                    message = ChatMessage(
                        sender = MessageSender.JARVIS,
                        text = "Recalling ${memories.size} directives, sir: $formatted.",
                        highLevelReasoning = "Queried memory bank. Retrieved ${memories.size} records.",
                        actionTaken = "Extracted active directives"
                    ),
                    emotion = JarvisEmotion.SATISFACTION,
                    connectionState = OpenJarvisConnectionState.ON_DEVICE
                )
            }
        }

        // 4.5 SQL Database Telemetry Query Check
        if (lower.contains("sql") || lower.contains("log report") || lower.contains("log reports") ||
            lower.contains("fingertip log") || lower.contains("facial recognition") ||
            lower.contains("face recognition") || lower.contains("database summary")
        ) {
            val summary = repository.sqlSummary.first()
            val recentFace = repository.faceEvents.first().firstOrNull()
            val faceNote = if (recentFace != null) {
                "Latest face scan: ${recentFace.recognizedLabel} (${recentFace.status})."
            } else {
                "No facial recognition scans logged yet."
            }
            val msg = "SQL archive operational, sir: ${summary.fingertipCount} fingertip coordinates, ${summary.faceEventCount} facial events (${summary.enrolledFaceCount} enrolled), and ${summary.logReportCount} log reports. $faceNote"
            recordTrace("LOCAL_SQL", input, "SQL Telemetry Query", startMs, true)
            return BrainExecutionResult(
                message = ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = msg,
                    highLevelReasoning = "Queried local Room SQL database tables.",
                    actionTaken = "SQL Telemetry Report"
                ),
                emotion = JarvisEmotion.SATISFACTION,
                connectionState = OpenJarvisConnectionState.ON_DEVICE
            )
        }

        // 4.6 Holographic 3D Workshop Control Check
        if (lower.contains("endurance") || lower.contains("interstellar") || lower.contains("spaceship") ||
            lower.contains("jarvis core") || lower.contains("neural sphere") || lower.contains("neural matrix") ||
            lower.contains("wireframe") || lower.contains("solid mode") ||
            lower.contains("explode") || lower.contains("disassemble") || lower.contains("assemble")
        ) {
            val reply = when {
                lower.contains("endurance") || lower.contains("interstellar") || lower.contains("spaceship") ->
                    "Deploying the Interstellar Endurance centrifuge with Ranger shuttle and harmonic resonance, sir."
                lower.contains("jarvis core") || lower.contains("neural sphere") || lower.contains("neural matrix") ->
                    "Switching projection to the procedural circuit JARVIS neural core, sir."
                lower.contains("wireframe") ->
                    "Engaging tactical wireframe schematic overlay, sir."
                lower.contains("solid mode") ->
                    "Restoring solid holographic surface shading, sir."
                lower.contains("explode") || lower.contains("disassemble") ->
                    "Expanding structural assemblies into exploded inspection view, sir."
                else ->
                    "Re-docking holographic modules into nominal assembly, sir."
            }
            recordTrace("LOCAL_HOLO", input, "3D Workshop Projection", startMs, true)
            return BrainExecutionResult(
                message = ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = reply,
                    highLevelReasoning = "Executed local holographic 3D workshop projection command.",
                    actionTaken = "3D Holo-Workshop"
                ),
                emotion = JarvisEmotion.EXCITEMENT,
                connectionState = OpenJarvisConnectionState.ON_DEVICE
            )
        }

        // 5. Device Hardware & Modular Local Tool Router (instant execution)
        val toolInvocation = matchTool(normalizedInput, lower) ?: matchTool(input, rawLower)
        if (toolInvocation != null) {
            val (tool, params) = toolInvocation
            val result = tool.execute(context, params)
            securityEngine.auditAction(
                action = "TOOL_EXECUTION: ${tool.name}",
                details = "Params: $params, Result: ${result.summary}",
                isApproved = result.success
            )
            recordTrace("LOCAL_TOOL", input, tool.name, startMs, result.success)
            result.directAction?.invoke()

            val inferred = JarvisEmotionEngine.inferEmotion(
                userInput = input,
                responseText = result.summary,
                isError = !result.success
            )
            return BrainExecutionResult(
                message = ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = result.summary,
                    highLevelReasoning = "OpenJarvis Local-First Tool [${tool.name}] executed.",
                    actionTaken = tool.name,
                    toolResult = result.displayData
                ),
                emotion = inferred,
                connectionState = OpenJarvisConnectionState.ON_DEVICE
            )
        }

        // 6. Gather context memory & personality prompt
        val currentMemories = repository.memories.first()
        val memoryContext = if (currentMemories.isNotEmpty()) {
            currentMemories.joinToString("\n") { "- ${it.value}" }
        } else {
            "No special user directives stored yet."
        }
        val systemInstruction = JarvisEmotionEngine.buildPersonalitySystemPrompt(
            currentEmotion = currentEmotion,
            memoryContext = memoryContext
        )

        // 7. Primary Intelligence Engine: OpenJarvis Server (`jarvis serve` /v1/chat/completions)
        if (config.inferenceMode != OpenJarvisInferenceMode.ON_DEVICE_ONLY && isNetworkAvailable()) {
            var openJarvisText = ""
            var openJarvisModel = config.selectedModel
            var openJarvisError: String? = null

            openJarvisBridge.streamChatCompletion(
                config = config,
                userPrompt = input,
                systemInstruction = systemInstruction,
                conversationHistory = conversationHistory
            ).collect { event ->
                when (event) {
                    is OpenJarvisStreamEvent.Token -> {
                        val (_, cleanAccum) = JarvisEmotionEngine.extractEmotionTag(event.accumulated)
                        if (cleanAccum.isNotEmpty()) {
                            onStreamDelta(cleanAccum)
                        }
                    }
                    is OpenJarvisStreamEvent.Completed -> {
                        openJarvisText = event.fullText
                        openJarvisModel = event.modelUsed
                    }
                    is OpenJarvisStreamEvent.Failure -> {
                        openJarvisError = event.reason
                    }
                }
            }

            if (openJarvisText.isNotBlank()) {
                val (taggedEmotion, cleanedText) = JarvisEmotionEngine.extractEmotionTag(openJarvisText)
                val finalEmotion = taggedEmotion ?: JarvisEmotionEngine.inferEmotion(input, cleanedText)
                conversationHistory.add("user" to input)
                conversationHistory.add("assistant" to cleanedText)
                if (conversationHistory.size > 16) {
                    conversationHistory.removeAt(0)
                    conversationHistory.removeAt(0)
                }
                recordTrace("OPENJARVIS_SERVER", input, "Model: $openJarvisModel", startMs, true)
                return BrainExecutionResult(
                    message = ChatMessage(
                        sender = MessageSender.JARVIS,
                        text = cleanedText,
                        highLevelReasoning = "OpenJarvis backend ($openJarvisModel) streamed response.",
                        actionTaken = "OpenJarvis: $openJarvisModel"
                    ),
                    emotion = finalEmotion,
                    connectionState = OpenJarvisConnectionState.CONNECTED
                )
            } else if (config.inferenceMode == OpenJarvisInferenceMode.OPENJARVIS_SERVER_ONLY) {
                val errText = "OpenJarvis server unreachable (${openJarvisError ?: "offline"}). Verify `jarvis serve` is running at ${config.serverUrl}."
                recordTrace("OPENJARVIS_SERVER", input, "Server Unreachable", startMs, false)
                return BrainExecutionResult(
                    message = ChatMessage(
                        sender = MessageSender.JARVIS,
                        text = errText,
                        highLevelReasoning = "OpenJarvis Server-Only mode active; connection failed."
                    ),
                    emotion = JarvisEmotion.CONCERN,
                    connectionState = OpenJarvisConnectionState.OFFLINE
                )
            }
        }

        // 8. Optional Cloud Fallback (Gemini via Secrets Panel key, if configured and online)
        if (config.inferenceMode == OpenJarvisInferenceMode.AUTO_LOCAL_FIRST && isNetworkAvailable()) {
            val geminiResponse = geminiClient.generateContent(
                prompt = input,
                systemInstruction = systemInstruction,
                conversationHistory = conversationHistory
            )
            if (geminiResponse.success && geminiResponse.text.isNotBlank()) {
                val (taggedEmotion, cleanedText) = JarvisEmotionEngine.extractEmotionTag(geminiResponse.text)
                val finalEmotion = taggedEmotion ?: JarvisEmotionEngine.inferEmotion(input, cleanedText)
                conversationHistory.add("user" to input)
                conversationHistory.add("model" to cleanedText)
                recordTrace("GEMINI_CLOUD_AUX", input, "Cloud Auxiliary Inference", startMs, true)
                return BrainExecutionResult(
                    message = ChatMessage(
                        sender = MessageSender.JARVIS,
                        text = cleanedText,
                        highLevelReasoning = geminiResponse.reasoning,
                        actionTaken = "Cloud Auxiliary Core"
                    ),
                    emotion = finalEmotion,
                    connectionState = OpenJarvisConnectionState.CLOUD_AUX
                )
            }
        }

        // 9. Autonomous On-Device Offline Matrix Fallback
        val offlineResponse = generateOnboardJarvisResponse(normalizedInput, lower)
        val offlineEmotion = JarvisEmotionEngine.inferEmotion(input, offlineResponse)
        recordTrace("OFFLINE_MATRIX", input, "On-Device Autonomous Brain", startMs, true)
        return BrainExecutionResult(
            message = ChatMessage(
                sender = MessageSender.JARVIS,
                text = offlineResponse,
                highLevelReasoning = "Synthesized via OpenJarvis on-device autonomous cognitive matrix.",
                actionTaken = "On-Device Matrix"
            ),
            emotion = offlineEmotion,
            connectionState = OpenJarvisConnectionState.ON_DEVICE
        )
    }

    private suspend fun recordTrace(engine: String, rawInput: String, action: String, startMs: Long, success: Boolean) {
        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        val masked = securityEngine.maskSecrets(rawInput)
        OpenJarvisRuntime.recordTrace(
            OpenJarvisTrace(
                engine = engine,
                maskedInput = masked,
                action = action,
                latencyMs = elapsed,
                success = success
            )
        )
        val category = when {
            engine.contains("OPENJARVIS") || engine.contains("GEMINI") -> "OPENJARVIS_AI"
            engine == "LOCAL_SQL" -> "SYSTEM"
            else -> "VOICE_CMD"
        }
        val metrics = org.json.JSONObject()
            .put("engine", engine)
            .put("latencyMs", elapsed)
            .put("success", success)
            .toString()
        repository.recordLogReport(
            category = category,
            severity = if (success) "INFO" else "WARN",
            title = action,
            summary = "Input: \"$masked\" (${elapsed}ms via $engine)",
            metricsJson = metrics
        )
    }

    private fun stripConversationalFillers(input: String): String {
        var cleaned = input.trim()
        val prefixes = listOf(
            "hey jarvis", "ok jarvis", "okay jarvis", "hi jarvis", "hello jarvis", "jarvis",
            "can you please", "could you please", "would you please",
            "can you", "could you", "would you", "will you", "please"
        )
        var changed = true
        while (changed) {
            changed = false
            val lower = cleaned.lowercase(Locale.ROOT)
            for (prefix in prefixes) {
                if (lower.startsWith("$prefix ") || lower.startsWith("$prefix,")) {
                    cleaned = cleaned.substring(prefix.length).trimStart(',', ':', ' ', '!')
                    changed = true
                    break
                }
            }
        }
        return cleaned.ifBlank { input.trim() }
    }

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    private fun matchTool(input: String, lower: String): Pair<JarvisTool, Map<String, String>>? {
        if (lower.startsWith("calculate") || lower.startsWith("calc ") || lower.contains("sqrt(") ||
            lower.startsWith("what is ") && (lower.contains("+") || lower.contains("-") || lower.contains("*") || lower.contains("/") || lower.contains("plus") || lower.contains("minus") || lower.contains("times") || lower.contains("divided by")) ||
            Regex("^(\\d+[\\s+\\-*/^%]+\\d+)+$").containsMatchIn(input)
        ) {
            val expr = input
                .replace("calculate", "", ignoreCase = true)
                .replace("calc", "", ignoreCase = true)
                .replace("what is", "", ignoreCase = true)
                .replace("plus", "+", ignoreCase = true)
                .replace("minus", "-", ignoreCase = true)
                .replace("times", "*", ignoreCase = true)
                .replace("divided by", "/", ignoreCase = true)
                .replace("?", "")
                .trim()
            val calcTool = tools.filterIsInstance<CalculatorTool>().firstOrNull()
            if (calcTool != null && expr.isNotEmpty()) {
                return calcTool to mapOf("expression" to expr)
            }
        }

        if (lower.contains("weather") || lower.contains("forecast") || lower.contains("temperature")) {
            val city = if (lower.contains(" in ")) {
                input.substringAfter(" in ").trim().trimEnd('?')
            } else if (lower.contains(" for ")) {
                input.substringAfter(" for ").trim().trimEnd('?')
            } else {
                "San Francisco"
            }
            return weatherTool to mapOf("location" to city)
        }

        if (lower.contains("antivirus") || lower.contains("anti-virus") || lower.contains("scan for virus") || lower.contains("scan for malware") || lower.contains("virus scan") || lower.contains("malware scan") || lower.contains("security scan") || lower.contains("threat scan") || lower.contains("scan my device") || lower.contains("scan phone")) {
            val tool = tools.filterIsInstance<AntiVirusTool>().firstOrNull()
            if (tool != null) return tool to emptyMap()
        }

        if (lower.contains("volume up") || lower.contains("increase volume") || lower.contains("increase the volume") || lower.contains("turn up volume") || lower.contains("turn up the volume") || lower.contains("raise volume") || lower.contains("raise the volume") || lower.contains("louder")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "volume_up") }
        }
        if (lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("decrease the volume") || lower.contains("turn down volume") || lower.contains("turn down the volume") || lower.contains("lower volume") || lower.contains("lower the volume") || lower.contains("softer") || lower.contains("quieter")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "volume_down") }
        }
        if (lower.contains("mute volume") || lower == "mute" || lower.contains("mute device") || lower.contains("mute my phone") || lower.contains("mute the phone") || lower.contains("silence audio") || lower == "silence") {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "volume_mute") }
        }
        if (lower.contains("max volume") || lower.contains("maximum volume") || lower.contains("volume to max") || lower.contains("full volume") || lower.contains("unmute")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "volume_max") }
        }
        if (lower.contains("vibrate mode") || lower.contains("set to vibrate") || lower.contains("ringer to vibrate")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "ringer_vibrate") }
        }
        if (lower.contains("silent mode") || lower.contains("set to silent")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "ringer_silent") }
        }
        if (lower.contains("normal ringer") || lower.contains("normal mode") || lower.contains("ringer on")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "ringer_normal") }
        }

        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("turn on flashlight") || lower.contains("turn on the flashlight") || lower.contains("switch on flashlight") || lower.contains("turn on torch") || lower.contains("enable flashlight") || lower == "flashlight" || lower == "turn flashlight on") {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "flashlight_on") }
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("turn off flashlight") || lower.contains("turn off the flashlight") || lower.contains("switch off flashlight") || lower.contains("turn off torch") || lower.contains("disable flashlight") || lower == "turn flashlight off") {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "flashlight_off") }
        }

        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "open_wifi") }
        }
        if (lower.contains("bluetooth")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "open_bluetooth") }
        }
        if (lower.contains("brightness") || lower.contains("display settings") || lower.contains("screen settings")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "open_display") }
        }
        if (lower.contains("device settings") || lower.contains("system settings") || lower == "settings" || lower == "open settings") {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "open_settings") }
        }

        if (lower.contains("battery") || lower.contains("power level") || lower.contains("charge") || lower.contains("system diagnostic") || lower.contains("diagnostics") || lower.contains("system status")) {
            return tools.filterIsInstance<SystemControlTool>().firstOrNull()?.let { it to mapOf("action" to "battery") }
        }

        if (lower.startsWith("open ") || lower.startsWith("launch ") || lower.startsWith("start ")) {
            val target = input.replaceFirst(Regex("^(open|launch|start)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (target.isNotEmpty()) {
                return tools.filterIsInstance<AppLauncherTool>().firstOrNull()?.let { it to mapOf("target" to target) }
            }
        }

        if (lower.startsWith("search for ") || lower.startsWith("google ") || lower.startsWith("search ") || lower.startsWith("look up ")) {
            val query = input.replaceFirst(Regex("^(search for|look up|google|search)\\s+", RegexOption.IGNORE_CASE), "").trim()
            if (query.isNotEmpty()) {
                return tools.filterIsInstance<WebSearchTool>().firstOrNull()?.let { it to mapOf("query" to query) }
            }
        }

        if (lower.startsWith("remind me to") || lower.startsWith("reminder:") || lower.startsWith("set reminder") || lower.startsWith("set a reminder to") || lower.startsWith("set a reminder for")) {
            val title = input.replaceFirst(Regex("^(remind me to|set a reminder to|set a reminder for|set reminder|reminder:)\\s*", RegexOption.IGNORE_CASE), "").trim()
            return tools.filterIsInstance<NotesAndRemindersTool>().firstOrNull()?.let { it to mapOf("type" to "reminder", "title" to title) }
        }

        if (lower.startsWith("note:") || lower.startsWith("take note") || lower.startsWith("take a note") || lower.startsWith("create note") || lower.startsWith("log note")) {
            val content = input.replaceFirst(Regex("^(take a note|take note|create note|log note|note:)\\s*", RegexOption.IGNORE_CASE), "").trim()
            return tools.filterIsInstance<NotesAndRemindersTool>().firstOrNull()?.let { it to mapOf("type" to "note", "title" to "Tactical Log", "detail" to content) }
        }

        return null
    }

    private fun generateOnboardJarvisResponse(input: String, lower: String): String {
        return when {
            lower == "wake up" || lower == "wake up jarvis" || lower == "wake up!" || lower == "wake-up" ->
                "Online and calibrated, sir. All autonomous core systems are nominal. Awaiting your command."
            lower.contains("offline") ->
                "On-device OpenJarvis routines are active, sir. Voice synthesis, local tools, hand tracking, and memory remain operational without internet."
            lower.contains("who are you") || lower.contains("your name") ->
                "I am JARVIS X, running on an OpenJarvis local-first neural architecture."
            lower.contains("how are you") || lower.contains("how do you feel") ->
                "Operating at peak parameters, sir. Holographic projection, reactor core, and acoustic sensors are aligned."
            lower.contains("time") && (lower.contains("what") || lower.contains("current") || lower.contains("tell")) -> {
                val timeFormat = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                "The current local time is ${timeFormat.format(java.util.Date())}, sir."
            }
            lower.contains("date") && (lower.contains("what") || lower.contains("today") || lower.contains("current") || lower.contains("day")) -> {
                val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                "Today is ${dateFormat.format(java.util.Date())}, sir."
            }
            lower.contains("joke") || lower.contains("funny") -> listOf(
                "I asked the neural cache for a joke, sir, but it returned a miss.",
                "Why do programmers prefer dark interfaces? Light attracts bugs, sir.",
                "There are 10 types of people in the world: those who understand binary, and those who do not."
            ).random()
            lower.contains("status report") || lower.contains("all systems") ->
                "All holographic systems, local wake-word listener, and OpenJarvis bridge subsystems are operational, sir."
            lower.contains("hello") || lower.contains("hi") || lower.contains("greetings") || lower.contains("good morning") || lower.contains("good evening") ->
                "Good to hear your voice, sir. How may I assist?"
            lower.contains("thank you") || lower.contains("thanks") ->
                "At your service as always, sir."
            lower.contains("help") || lower.contains("what can you do") ->
                "Say 'Jarvis', pinch with your hand, or tap the core to command: flashlight, volume, app launch, calculations, reminders, diagnostics, or OpenJarvis AI queries."
            lower.contains("stand down") || lower.contains("sleep") || lower.contains("shut down") || lower.contains("goodnight") ->
                "Entering standby holographic sentinel mode, sir. Say 'Jarvis' whenever you require assistance."
            else ->
                "Directive noted, sir: \"$input\". Connect an OpenJarvis backend in Engine Config for full LLM reasoning, or ask for device tools."
        }
    }

    fun getAllTools(): List<JarvisTool> = tools
}
