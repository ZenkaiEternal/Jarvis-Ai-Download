package com.example.domain.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.BatteryManager
import android.provider.Settings
import com.example.data.api.WeatherClient
import com.example.data.repository.JarvisRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WebSearchTool : JarvisTool {
    override val id = "web_search"
    override val name = "Tactical Web Search"
    override val description = "Conducts a secure query across public information networks and synthesizes intelligence."
    override val category = "INTELLIGENCE"
    override val parameters = listOf(
        ToolParameter("query", "Search terms or question to look up")
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val query = params["query"]?.trim() ?: ""
        if (query.isEmpty()) {
            return ToolResult(false, "Search query parameter missing.")
        }

        val searchUrl = "https://www.google.com/search?q=" + Uri.encode(query)
        val action = {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }

        return ToolResult(
            success = true,
            summary = "Web search initiated for '$query'. Uplink to external browser available.",
            displayData = "Target: Google Search\nQuery: $query\nURL: $searchUrl",
            directAction = action
        )
    }
}

class CalculatorTool : JarvisTool {
    override val id = "calculator"
    override val name = "Computational Math Core"
    override val description = "Performs precision arithmetic, algebraic calculations, and scientific conversions."
    override val category = "COMPUTATION"
    override val parameters = listOf(
        ToolParameter("expression", "Mathematical expression to evaluate, e.g. '125 * 8.5' or 'sqrt(144)'")
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val expr = params["expression"]?.trim() ?: ""
        if (expr.isEmpty()) {
            return ToolResult(false, "No mathematical expression provided.")
        }

        try {
            val result = evaluateMath(expr)
            return ToolResult(
                success = true,
                summary = "Computation complete: $expr = $result",
                displayData = "INPUT: $expr\nOUTPUT: $result\nSTATUS: Computed with 64-bit floating point precision"
            )
        } catch (e: Exception) {
            return ToolResult(
                false,
                "Unable to evaluate formula '$expr'. Please verify expression syntax."
            )
        }
    }

    private fun evaluateMath(str: String): Double {
        val cleaned = str.replace("x", "*").replace("X", "*").replace(" ", "")
        return object : Any() {
            var pos = -1
            var ch = 0

            fun nextChar() {
                ch = if (++pos < cleaned.length) cleaned[pos].code else -1
            }

            fun eat(charToEat: Int): Boolean {
                while (ch == ' '.code) nextChar()
                if (ch == charToEat) {
                    nextChar()
                    return true
                }
                return false
            }

            fun parse(): Double {
                nextChar()
                val x = parseExpression()
                if (pos < cleaned.length) throw RuntimeException("Unexpected: " + ch.toChar())
                return x
            }

            fun parseExpression(): Double {
                var x = parseTerm()
                while (true) {
                    when {
                        eat('+'.code) -> x += parseTerm()
                        eat('-'.code) -> x -= parseTerm()
                        else -> return x
                    }
                }
            }

            fun parseTerm(): Double {
                var x = parseFactor()
                while (true) {
                    when {
                        eat('*'.code) -> x *= parseFactor()
                        eat('/'.code) -> x /= parseFactor()
                        eat('%'.code) -> x %= parseFactor()
                        else -> return x
                    }
                }
            }

            fun parseFactor(): Double {
                if (eat('+'.code)) return +parseFactor()
                if (eat('-'.code)) return -parseFactor()

                var x: Double
                val startPos = pos
                if (eat('('.code)) {
                    x = parseExpression()
                    eat(')'.code)
                } else if ((ch in '0'.code..'9'.code) || ch == '.'.code) {
                    while ((ch in '0'.code..'9'.code) || ch == '.'.code) nextChar()
                    x = cleaned.substring(startPos, pos).toDouble()
                } else if (ch in 'a'.code..'z'.code) {
                    while (ch in 'a'.code..'z'.code) nextChar()
                    val func = cleaned.substring(startPos, pos)
                    x = parseFactor()
                    x = when (func) {
                        "sqrt" -> Math.sqrt(x)
                        "sin" -> Math.sin(Math.toRadians(x))
                        "cos" -> Math.cos(Math.toRadians(x))
                        "tan" -> Math.tan(Math.toRadians(x))
                        "log" -> Math.log10(x)
                        "ln" -> Math.log(x)
                        else -> throw RuntimeException("Unknown function: $func")
                    }
                } else {
                    throw RuntimeException("Unexpected: " + ch.toChar())
                }

                if (eat('^'.code)) x = Math.pow(x, parseFactor())
                return x
            }
        }.parse()
    }
}

class WeatherTool(private val weatherClient: WeatherClient) : JarvisTool {
    override val id = "weather"
    override val name = "Meteorological Telemetry"
    override val description = "Retrieves live atmospheric readings, temperature, wind, and forecast conditions for any sector."
    override val category = "TELEMETRY"
    override val parameters = listOf(
        ToolParameter("location", "City or coordinates, e.g. 'New York' or 'London'", isRequired = false, defaultValue = "San Francisco")
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val loc = params["location"]?.ifBlank { "San Francisco" } ?: "San Francisco"
        val weather = weatherClient.fetchWeather(loc)

        val summary = "Atmospheric scan for ${weather.location}: ${weather.condition}, ${String.format(Locale.US, "%.1f", weather.temperatureC)}°C (${String.format(Locale.US, "%.1f", weather.temperatureF)}°F), humidity at ${weather.humidityPercent}%."
        val display = """
            SECTOR: ${weather.location}
            CONDITIONS: ${weather.condition}
            TEMPERATURE: ${String.format(Locale.US, "%.1f", weather.temperatureC)}°C / ${String.format(Locale.US, "%.1f", weather.temperatureF)}°F
            HUMIDITY: ${weather.humidityPercent}%
            WIND SPEED: ${String.format(Locale.US, "%.1f", weather.windSpeedKmh)} km/h
            SENSOR STATUS: Online (Open-Meteo Satellite Feed)
        """.trimIndent()

        return ToolResult(
            success = true,
            summary = summary,
            displayData = display
        )
    }
}

class AppLauncherTool : JarvisTool {
    override val id = "app_launcher"
    override val name = "Application & Link Nexus"
    override val description = "Launches approved applications, websites, and system utilities."
    override val category = "SYSTEM"
    override val parameters = listOf(
        ToolParameter("target", "App or web destination: 'camera', 'browser', 'settings', 'maps', 'calculator', 'youtube', 'github', 'wikipedia'")
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val target = params["target"]?.lowercase(Locale.ROOT)?.trim() ?: ""

        val (intent, targetLabel) = when {
            target.contains("camera") -> {
                Intent("android.media.action.IMAGE_CAPTURE") to "Optical Camera Sensor"
            }
            target.contains("setting") -> {
                Intent(Settings.ACTION_SETTINGS) to "System Settings"
            }
            target.contains("map") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=current+location")) to "Navigation Grid (Maps)"
            }
            target.contains("calc") -> {
                Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                } to "Native Calculator"
            }
            target.contains("youtube") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")) to "YouTube Media Feed"
            }
            target.contains("github") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com")) to "GitHub Code Matrix"
            }
            target.contains("wiki") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://en.wikipedia.org")) to "Wikipedia Archives"
            }
            target.startsWith("http://") || target.startsWith("https://") -> {
                Intent(Intent.ACTION_VIEW, Uri.parse(target)) to "Secure Web URL: $target"
            }
            else -> {
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(target))) to "Web Target: $target"
            }
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

        val action = {
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                // Fallback to browser if native target not installed
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(target))).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            }
        }

        return ToolResult(
            success = true,
            summary = "Opening $targetLabel as instructed, sir.",
            displayData = "SUBSYSTEM: Application Nexus\nTARGET: $targetLabel\nSTATUS: Intent Dispatched",
            directAction = action
        )
    }
}

class SystemControlTool : JarvisTool {
    override val id = "system_control"
    override val name = "Diagnostics & System Diagnostics"
    override val description = "Monitors hardware telemetry (battery, memory, power) and toggles auxiliary hardware."
    override val category = "HARDWARE"
    override val parameters = listOf(
        ToolParameter("action", "Action: 'battery', 'specs', 'flashlight_on', 'flashlight_off'")
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val action = params["action"]?.lowercase(Locale.ROOT)?.trim() ?: "battery"

        when (action) {
            "flashlight_on" -> {
                try {
                    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = camManager.cameraIdList.firstOrNull() ?: ""
                    camManager.setTorchMode(cameraId, true)
                    return ToolResult(true, "Flashlight optical emitter activated, sir.")
                } catch (e: Exception) {
                    return ToolResult(false, "Unable to toggle optical emitter: ${e.message}")
                }
            }
            "flashlight_off" -> {
                try {
                    val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    val cameraId = camManager.cameraIdList.firstOrNull() ?: ""
                    camManager.setTorchMode(cameraId, false)
                    return ToolResult(true, "Flashlight optical emitter deactivated.")
                } catch (e: Exception) {
                    return ToolResult(false, "Unable to toggle optical emitter: ${e.message}")
                }
            }
            else -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val batteryLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val runtime = Runtime.getRuntime()
                val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
                val maxMemoryMb = runtime.maxMemory() / (1024 * 1024)

                val summary = "System status nominal. Battery core at $batteryLevel%, memory allocation at $usedMemoryMb MB of $maxMemoryMb MB."
                val display = """
                    POWER CELL: $batteryLevel% Capacity
                    MEMORY CORE: $usedMemoryMb MB / $maxMemoryMb MB
                    PROCESSORS: ${runtime.availableProcessors()} Active Cores
                    SYSTEM TIME: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).format(Date())}
                    OS: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})
                    STATUS: Operational / Systems 100% Nominal
                """.trimIndent()

                return ToolResult(true, summary, display)
            }
        }
    }
}

class NotesAndRemindersTool(private val repository: JarvisRepository) : JarvisTool {
    override val id = "notes_reminders"
    override val name = "Mission Log & Reminders"
    override val description = "Records tactical field notes, instructions, and time-stamped reminders."
    override val category = "PRODUCTIVITY"
    override val parameters = listOf(
        ToolParameter("type", "Either 'note' or 'reminder'"),
        ToolParameter("title", "Headline or title"),
        ToolParameter("detail", "Content or timestamp note", isRequired = false)
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val type = params["type"]?.lowercase(Locale.ROOT)?.trim() ?: "note"
        val title = params["title"]?.trim() ?: "Untitled Log"
        val detail = params["detail"]?.trim() ?: ""

        if (type == "reminder") {
            repository.addReminder(title, System.currentTimeMillis() + 3600000L)
            return ToolResult(
                success = true,
                summary = "Tactical reminder logged: '$title'.",
                displayData = "TYPE: Reminder\nTITLE: $title\nSCHEDULE: Active"
            )
        } else {
            repository.addNote(title, detail.ifBlank { "Recorded by JARVIS system." })
            return ToolResult(
                success = true,
                summary = "Mission note stored in memory banks: '$title'.",
                displayData = "TYPE: Tactical Note\nTITLE: $title\nDETAILS: $detail"
            )
        }
    }
}

class FileAnalysisTool : JarvisTool {
    override val id = "file_analysis"
    override val name = "Neural Document Analyzer"
    override val description = "Performs natural language decomposition and tactical summarization of documents and code."
    override val category = "INTELLIGENCE"
    override val parameters = listOf(
        ToolParameter("text", "Document text or code snippet to analyze")
    )
    override val isConsequential = false

    override suspend fun execute(context: Context, params: Map<String, String>): ToolResult {
        val text = params["text"]?.trim() ?: ""
        if (text.isEmpty()) {
            return ToolResult(false, "No document payload provided for analysis.")
        }

        val wordCount = text.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val charCount = text.length
        val lines = text.lines().size

        // Generate intelligent summary bullet points
        val sentences = text.split(Regex("[.!?\n]")).map { it.trim() }.filter { it.length > 15 }
        val keyPoints = sentences.take(3).map { "• " + it }.joinToString("\n")

        val summary = "Document analysis complete. Payload spans $wordCount words across $lines lines."
        val display = """
            DOCUMENT TELEMETRY:
            - Word Count: $wordCount
            - Character Count: $charCount
            - Total Lines: $lines
            
            KEY EXTRACTS:
            $keyPoints
            
            INTELLIGENCE SYNTHESIS:
            Payload indexed into active working cache.
        """.trimIndent()

        return ToolResult(true, summary, display)
    }
}
