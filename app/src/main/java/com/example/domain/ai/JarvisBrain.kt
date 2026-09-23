package com.example.domain.ai

import android.content.Context
import com.example.data.api.GeminiClient
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntity
import com.example.data.model.MessageSender
import com.example.data.repository.JarvisRepository
import com.example.domain.security.ConfirmationRequest
import com.example.domain.security.SecurityEngine
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

class JarvisBrain(
    private val context: Context,
    private val repository: JarvisRepository,
    private val geminiClient: GeminiClient,
    private val securityEngine: SecurityEngine,
    private val weatherTool: WeatherTool
) {
    private val tools: List<JarvisTool> = listOf(
        weatherTool,
        CalculatorTool(),
        WebSearchTool(),
        AppLauncherTool(),
        SystemControlTool(),
        NotesAndRemindersTool(repository),
        FileAnalysisTool()
    )

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    suspend fun processUserQuery(
        userInput: String,
        onRequestConfirmation: (ConfirmationRequest) -> Unit
    ): ChatMessage {
        val input = userInput.trim()

        // 1. Security validation
        val validation = securityEngine.validateInput(input)
        if (!validation.isValid) {
            return ChatMessage(
                sender = MessageSender.JARVIS,
                text = "Security alert: ${validation.message}",
                highLevelReasoning = "Security boundary check failed. Execution halted."
            )
        }

        val lower = input.lowercase(Locale.ROOT)

        // 2. Destructive command check (e.g. Wipe memory / delete notes)
        if (lower.contains("wipe memory") || lower.contains("clear memory") || lower.contains("delete all memories") || lower.contains("purge memories")) {
            val request = ConfirmationRequest(
                title = "CONFIRM MEMORY PURGE",
                description = "You have requested a complete wipe of all stored user preferences and directives. This action is irreversible. Proceed?",
                onConfirm = {
                    repository.clearMemories()
                    securityEngine.auditAction("PURGE_MEMORIES", "User authorized full memory wipe.", true)
                },
                onCancel = {
                    // Canceled
                }
            )
            onRequestConfirmation(request)
            return ChatMessage(
                sender = MessageSender.JARVIS,
                text = "Confirmation required, sir. Purging the memory core will permanently erase all stored preferences.",
                highLevelReasoning = "Consequential protocol triggered: Memory purge verification requested."
            )
        }

        // 3. Memory storage check ("remember that...", "keep in mind that...")
        if (lower.startsWith("remember that") || lower.startsWith("remember:") || lower.startsWith("keep in mind that")) {
            val fact = input
                .replace("remember that", "", ignoreCase = true)
                .replace("remember:", "", ignoreCase = true)
                .replace("keep in mind that", "", ignoreCase = true)
                .trim()

            if (fact.isNotEmpty()) {
                val key = "FACT_${System.currentTimeMillis() % 10000}"
                repository.saveMemory(key = key, value = fact, category = "USER_DIRECTIVE")
                securityEngine.auditAction("SAVE_MEMORY", "Stored: $fact", true)
                return ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "Acknowledged, sir. I have committed that to core memory: \"$fact\".",
                    highLevelReasoning = "Memory bank updated with high-priority user directive.",
                    actionTaken = "Committed to Room Database"
                )
            }
        }

        // 4. Memory query check ("what do you remember", "recall my preferences")
        if (lower.contains("what do you remember") || lower.contains("recall preferences") || lower.contains("my memory")) {
            val memories = repository.memories.first()
            if (memories.isEmpty()) {
                return ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "My memory archives are currently clear, sir. You may instruct me to remember key directives at any time.",
                    highLevelReasoning = "Queried memory bank. Found 0 records."
                )
            } else {
                val formatted = memories.joinToString("\n") { "• [${it.category}] ${it.value}" }
                return ChatMessage(
                    sender = MessageSender.JARVIS,
                    text = "Accessing core memory archives:\n\n$formatted",
                    highLevelReasoning = "Queried memory bank. Retrieved ${memories.size} records.",
                    actionTaken = "Extracted active directives"
                )
            }
        }

        // 5. Tool evaluation check
        val toolInvocation = matchTool(input, lower)
        if (toolInvocation != null) {
            val (tool, params) = toolInvocation
            val result = tool.execute(context, params)
            securityEngine.auditAction("TOOL_EXECUTION: ${tool.name}", "Params: $params, Result: ${result.summary}", result.success)

            result.directAction?.invoke()

            val reasoning = "Routing query through modular tool system [${tool.name}]. Executed with success=${result.success}."
            return ChatMessage(
                sender = MessageSender.JARVIS,
                text = result.summary,
                highLevelReasoning = reasoning,
                actionTaken = tool.name,
                toolResult = result.displayData
            )
        }

        // 6. Natural Language / AI reasoning using Gemini API
        val currentMemories = repository.memories.first()
        val memoryContext = if (currentMemories.isNotEmpty()) {
            "Stored user preferences and facts:\n" + currentMemories.joinToString("\n") { "- ${it.value}" }
        } else {
            "No special user preferences stored yet."
        }

        val systemInstruction = """
            You are J.A.R.V.I.S. (Just A Rather Very Intelligent System), the iconic, refined, British, highly sophisticated personal AI assistant.
            You address the user with supreme respect ("sir", "ma'am", or naturally polite terms).
            You are articulate, sharp, calm, witty, and deeply competent.
            Never break character.
            Keep answers concise, direct, and actionable—avoiding unnecessary fluff or repetition.
            $memoryContext
        """.trimIndent()

        val geminiResponse = geminiClient.generateContent(
            prompt = input,
            systemInstruction = systemInstruction,
            conversationHistory = conversationHistory
        )

        val responseText = if (geminiResponse.success && geminiResponse.text.isNotBlank()) {
            conversationHistory.add("user" to input)
            conversationHistory.add("model" to geminiResponse.text)
            geminiResponse.text
        } else {
            // Intelligent onboard heuristic synthesis
            generateOnboardJarvisResponse(input, lower, geminiResponse.error)
        }

        return ChatMessage(
            sender = MessageSender.JARVIS,
            text = responseText,
            highLevelReasoning = geminiResponse.reasoning.ifBlank {
                "Processed via local cognitive neural heuristics."
            },
            actionTaken = if (geminiResponse.success) "Gemini-3.5-Flash Core" else "Heuristic Auxiliary Matrix"
        )
    }

    private fun matchTool(input: String, lower: String): Pair<JarvisTool, Map<String, String>>? {
        // Calculator match
        if (lower.startsWith("calculate") || lower.startsWith("calc ") || lower.contains("sqrt(") ||
            Regex("^(\\d+[\\s+\\-*/^%]+\\d+)+$").containsMatchIn(input)
        ) {
            val expr = input.replace("calculate", "", ignoreCase = true)
                .replace("calc", "", ignoreCase = true).trim()
            val calcTool = tools.filterIsInstance<CalculatorTool>().firstOrNull()
            if (calcTool != null && expr.isNotEmpty()) {
                return calcTool to mapOf("expression" to expr)
            }
        }

        // Weather match
        if (lower.contains("weather") || lower.contains("forecast") || lower.contains("temperature")) {
            val city = if (lower.contains(" in ")) {
                input.substringAfter(" in ").trim()
            } else if (lower.contains(" for ")) {
                input.substringAfter(" for ").trim()
            } else {
                "San Francisco"
            }
            return weatherTool to mapOf("location" to city)
        }

        // System Control / Battery / Flashlight
        if (lower.contains("flashlight on") || lower.contains("torch on")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "flashlight_on") }
        }
        if (lower.contains("flashlight off") || lower.contains("torch off")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "flashlight_off") }
        }
        if (lower.contains("battery") || lower.contains("system diagnostic") || lower.contains("diagnostics") || lower.contains("system status")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "battery") }
        }

        // App Launcher match
        if (lower.startsWith("open ") || lower.startsWith("launch ")) {
            val target = input.replace("open ", "", ignoreCase = true).replace("launch ", "", ignoreCase = true).trim()
            val tool = tools.filterIsInstance<AppLauncherTool>().firstOrNull()
            return tool?.let { it to mapOf("target" to target) }
        }

        // Web Search match
        if (lower.startsWith("search for ") || lower.startsWith("google ") || lower.startsWith("search ")) {
            val query = input
                .replace("search for ", "", ignoreCase = true)
                .replace("google ", "", ignoreCase = true)
                .replace("search ", "", ignoreCase = true)
                .trim()
            val tool = tools.filterIsInstance<WebSearchTool>().firstOrNull()
            return tool?.let { it to mapOf("query" to query) }
        }

        // Notes and Reminders
        if (lower.startsWith("remind me to") || lower.startsWith("reminder:") || lower.startsWith("set reminder")) {
            val title = input
                .replace("remind me to", "", ignoreCase = true)
                .replace("reminder:", "", ignoreCase = true)
                .replace("set reminder", "", ignoreCase = true)
                .trim()
            val tool = tools.filterIsInstance<NotesAndRemindersTool>().firstOrNull()
            return tool?.let { it to mapOf("type" to "reminder", "title" to title) }
        }

        if (lower.startsWith("note:") || lower.startsWith("take note") || lower.startsWith("create note") || lower.startsWith("log note")) {
            val content = input
                .replace("take note", "", ignoreCase = true)
                .replace("create note", "", ignoreCase = true)
                .replace("log note", "", ignoreCase = true)
                .replace("note:", "", ignoreCase = true)
                .trim()
            val tool = tools.filterIsInstance<NotesAndRemindersTool>().firstOrNull()
            return tool?.let { it to mapOf("type" to "note", "title" to "Tactical Log", "detail" to content) }
        }

        return null
    }

    private fun generateOnboardJarvisResponse(input: String, lower: String, errorReason: String?): String {
        return when {
            lower.contains("who are you") || lower.contains("your name") -> {
                "I am J.A.R.V.I.S.—Just A Rather Very Intelligent System. At your command, sir."
            }
            lower.contains("status report") || lower.contains("all systems") -> {
                "All internal subroutines operational, sir. Holographic core, voice synthesis, memory database, and modular tools are standing by."
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("greetings") -> {
                "Always a pleasure, sir. How may I be of assistance today?"
            }
            lower.contains("thank you") || lower.contains("thanks") -> {
                "You are most welcome, sir. I am here whenever required."
            }
            lower.contains("help") || lower.contains("what can you do") -> {
                "My capabilities include continuous voice dialogue, real-time weather scans, mathematical computation, encrypted memory vaulting, tactical notes, system diagnostics, and external network searches."
            }
            else -> {
                if (errorReason == "API_KEY_UNCONFIGURED") {
                    "Auxiliary neural heuristics active, sir. To unlock deep reasoning via Gemini-3.5-Flash, please configure your GEMINI_API_KEY in AI Studio secrets. In the meantime, all onboard tools and local memory systems remain fully functional."
                } else {
                    "Understood, sir. Processing your directive with onboard subroutines. All peripheral tools and memory banks remain at your disposal."
                }
            }
        }
    }

    fun getAllTools(): List<JarvisTool> = tools
}
