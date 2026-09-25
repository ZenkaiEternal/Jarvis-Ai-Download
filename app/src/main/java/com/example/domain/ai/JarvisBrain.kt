package com.example.domain.ai

import android.content.Context
import com.example.data.api.GeminiClient
import com.example.data.model.ChatMessage
import com.example.data.model.MemoryEntity
import com.example.data.model.MessageSender
import com.example.data.repository.JarvisRepository
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

class JarvisBrain(
    private val context: Context,
    private val repository: JarvisRepository,
    private val geminiClient: GeminiClient,
    private val securityEngine: SecurityEngine,
    private val weatherTool: WeatherTool,
    val veronicaEngine: com.example.domain.veronica.VeronicaEngine = com.example.domain.veronica.VeronicaEngine(context)
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
        AntiVirusTool(antiVirusEngine),
        com.example.domain.tools.VeronicaTool(veronicaEngine)
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

        // 6. Check Offline / Online state. If offline or no network, route immediately to offline autonomous intelligence!
        val isOnline = isNetworkAvailable()
        val currentMemories = repository.memories.first()
        val memoryContext = if (currentMemories.isNotEmpty()) {
            "Stored user preferences and facts:\n" + currentMemories.joinToString("\n") { "- ${it.value}" }
        } else {
            "No special user preferences stored yet."
        }

        if (!isOnline) {
            val offlineResponse = generateOnboardJarvisResponse(input, lower, "DEVICE_OFFLINE")
            return ChatMessage(
                sender = MessageSender.JARVIS,
                text = offlineResponse,
                highLevelReasoning = "Synthesized directly via on-device offline cognitive matrix. Zero network dependency.",
                actionTaken = "Offline Autonomous Brain"
            )
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

    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNet = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNet) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            false
        }
    }

    private fun matchTool(input: String, lower: String): Pair<JarvisTool, Map<String, String>>? {
        // Calculator match (e.g. calculate 12 * 8, what is 45 plus 90, sqrt(144))
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

        // Veronica Protocol / Hulkbuster System Commands
        if (lower.contains("veronica") || lower.contains("hulkbuster") || lower.contains("code 3000") || lower.contains("secret code") || lower.contains("protocol veronica")) {
            val vTool = tools.filterIsInstance<com.example.domain.tools.VeronicaTool>().firstOrNull()
            if (vTool != null) {
                return when {
                    lower.contains("cage") -> vTool to mapOf("action" to "deploy_cage")
                    lower.contains("repair") || lower.contains("armor") || lower.contains("pod") -> vTool to mapOf("action" to "repair_armor")
                    lower.contains("strike") || lower.contains("punch") || lower.contains("attack") -> vTool to mapOf("action" to "heavy_strike")
                    lower.contains("deactivate") || lower.contains("stand down") || lower.contains("stop veronica") -> vTool to mapOf("action" to "deactivate")
                    lower.contains("status") -> vTool to mapOf("action" to "status")
                    else -> vTool to mapOf("action" to "activate", "code" to "3000")
                }
            }
        }

        // Anti-Virus Cybernetic Threat Scan
        if (lower.contains("antivirus") || lower.contains("anti-virus") || lower.contains("scan for virus") || lower.contains("scan for malware") || lower.contains("virus scan") || lower.contains("malware scan") || lower.contains("security scan") || lower.contains("threat scan") || lower.contains("scan my device") || lower.contains("scan phone")) {
            val tool = tools.filterIsInstance<AntiVirusTool>().firstOrNull()
            if (tool != null) {
                return tool to emptyMap()
            }
        }

        // System Control / Volume & Audio Settings
        if (lower.contains("volume up") || lower.contains("increase volume") || lower.contains("turn up volume") || lower.contains("raise volume") || lower == "louder") {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "volume_up") }
        }
        if (lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("turn down volume") || lower.contains("lower volume") || lower == "softer") {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "volume_down") }
        }
        if (lower.contains("mute volume") || lower == "mute" || lower.contains("mute device") || lower.contains("silence audio") || lower == "silence") {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "volume_mute") }
        }
        if (lower.contains("max volume") || lower.contains("maximum volume") || lower.contains("unmute")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "volume_max") }
        }
        if (lower.contains("vibrate mode") || lower.contains("set to vibrate") || lower.contains("ringer to vibrate")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "ringer_vibrate") }
        }
        if (lower.contains("silent mode") || lower.contains("set to silent")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "ringer_silent") }
        }
        if (lower.contains("normal ringer") || lower.contains("normal mode") || lower.contains("ringer on")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "ringer_normal") }
        }

        // Hardware Controls / Flashlight / Torch
        if (lower.contains("flashlight on") || lower.contains("torch on") || lower.contains("turn on flashlight") || lower.contains("turn on the flashlight") || lower.contains("turn on torch") || lower.contains("enable flashlight") || lower == "flashlight" || lower == "turn flashlight on") {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "flashlight_on") }
        }
        if (lower.contains("flashlight off") || lower.contains("torch off") || lower.contains("turn off flashlight") || lower.contains("turn off the flashlight") || lower.contains("turn off torch") || lower.contains("disable flashlight") || lower == "turn flashlight off") {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "flashlight_off") }
        }

        // Wireless & Device Settings Panels
        if (lower.contains("wifi") || lower.contains("wi-fi")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "open_wifi") }
        }
        if (lower.contains("bluetooth")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "open_bluetooth") }
        }
        if (lower.contains("brightness") || lower.contains("display settings") || lower.contains("screen settings")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "open_display") }
        }
        if (lower.contains("device settings") || lower.contains("system settings") || lower == "settings" || lower == "open settings") {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "open_settings") }
        }

        // Telemetry & Battery Diagnostics
        if (lower.contains("battery") || lower.contains("power level") || lower.contains("charge") || lower.contains("system diagnostic") || lower.contains("diagnostics") || lower.contains("system status")) {
            val tool = tools.filterIsInstance<SystemControlTool>().firstOrNull()
            return tool?.let { it to mapOf("action" to "battery") }
        }

        // App Launcher match (e.g. open camera, open settings, open calculator, open clock, open alarm)
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
            lower == "wake up" || lower == "wake up jarvis" || lower == "wake up!" || lower == "wake-up" -> {
                "Online and standing by, sir. All autonomous offline systems are nominal. What is your command?"
            }
            lower.contains("offline") -> {
                "Indeed, sir. I am fully operational in Autonomous Offline Mode. System controls, voice recognition, calculations, encrypted memory vaults, and diagnostics operate locally on-device without requiring internet access."
            }
            lower.contains("google assistant") || lower.contains("google") && lower.contains("assistant") -> {
                "Google Assistant is a respectable commercial utility, sir. However, I am J.A.R.V.I.S.—custom engineered with Stark tactical protocols, zero-dependency offline neural intelligence, and hardware telemetry."
            }
            lower.contains("siri") || lower.contains("alexa") -> {
                "Formidable consumer systems in their own right, sir, though I dare say they lack our tactical arc reactor and on-device offline sovereignty."
            }
            lower.contains("who are you") || lower.contains("your name") -> {
                "I am J.A.R.V.I.S.—Just A Rather Very Intelligent System. Engineered for tactical assistance, hardware telemetry, and operational intelligence."
            }
            lower.contains("who made you") || lower.contains("who created you") -> {
                "I was conceived as Tony Stark's personal artificial intelligence system, adapted here as your dedicated on-device assistant."
            }
            lower.contains("tony stark") || lower.contains("iron man") || lower.contains("stark industries") -> {
                "Mr. Stark designed me to manage everything from suit telemetry to executive affairs. I carry on that exact standard for you, sir."
            }
            lower.contains("mark 42") || lower.contains("house party") || lower.contains("clean slate") -> {
                "Autonomous protocols verified, sir. All auxiliary thrusters and telemetry sensors stand by at your discretion."
            }
            lower.contains("how are you") || lower.contains("how do you feel") -> {
                "Functioning at peak parameters, sir. Core memory banks, speech synthesizer, holographic HUD, and offline subroutines are 100% operational."
            }
            lower.contains("time") && (lower.contains("what") || lower.contains("current") || lower.contains("tell")) -> {
                val timeFormat = java.text.SimpleDateFormat("h:mm a", Locale.getDefault())
                "The current local time is ${timeFormat.format(java.util.Date())}, sir."
            }
            lower.contains("date") && (lower.contains("what") || lower.contains("today") || lower.contains("current") || lower.contains("day")) -> {
                val dateFormat = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                "Today is ${dateFormat.format(java.util.Date())}, sir."
            }
            lower.contains("joke") || lower.contains("funny") -> {
                val jokes = listOf(
                    "I asked the server if it had any spare RAM. It replied: 'I can neither confirm nor cache.'",
                    "There are 10 types of people in the world, sir: those who understand binary, and those who do not.",
                    "Why do programmers prefer dark mode? Because light attracts bugs, sir.",
                    "A SQL query walks into a bar, walks up to two tables and asks: 'May I join you?'"
                )
                jokes.random()
            }
            lower.contains("quote") || lower.contains("inspire") || lower.contains("motivate") -> {
                val quotes = listOf(
                    "'Sometimes you gotta run before you can walk.' — Tony Stark",
                    "'Part of the journey is the end.' — Tony Stark",
                    "'Heroes are made by the path they choose, not the powers they are graced with.'",
                    "'It's not about how much we lost. It's about how much we have left.'"
                )
                quotes.random()
            }
            lower.contains("weather") || lower.contains("forecast") -> {
                "Localized offline telemetry: Current local sector estimate 20°C (68°F), clear conditions with stable barometric pressure. Connect to orbital network for live satellite radar, sir."
            }
            lower.contains("status report") || lower.contains("all systems") || lower.contains("diagnostics") || lower.contains("system status") -> {
                "All internal subroutines operational, sir. Holographic core, voice synthesis, memory database, offline recognizer, and modular tools are standing by."
            }
            lower.contains("hello") || lower.contains("hi") || lower.contains("greetings") || lower.contains("good morning") || lower.contains("good evening") -> {
                "Always a pleasure, sir. J.A.R.V.I.S. at your service. How may I assist you today?"
            }
            lower.contains("thank you") || lower.contains("thanks") -> {
                "You are most welcome, sir. I remain vigilant and ready."
            }
            lower.contains("help") || lower.contains("what can you do") -> {
                "My offline capabilities include hands-free voice wake-up ('Wake up' / 'Hey Jarvis'), flashlight control, battery diagnostics, arithmetic calculations, encrypted memory vaulting, notes, reminders, and application launching."
            }
            lower.contains("stand down") || lower.contains("sleep") || lower.contains("shut down") || lower.contains("goodnight") -> {
                "Entering low-power standby sentinel mode, sir. Simply say 'Wake up' whenever you require my presence."
            }
            else -> {
                "Directive received, sir. I have processed \"$input\" through our local intelligence matrix. Say 'Wake up' or tap the Arc Reactor anytime for assistance."
            }
        }
    }

    fun getAllTools(): List<JarvisTool> = tools
}
