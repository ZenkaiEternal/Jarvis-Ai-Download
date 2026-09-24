package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExampleUnitTest {
    @Test
    fun testWakeWordDetection() {
        val sample1 = "Hey Jarvis what is the weather today"
        val sample2 = "Jarvis calculate 25 * 4"
        val sample3 = "Hello Jarvis"
        val sample4 = "Just checking system status"

        assertTrue(containsWakeWord(sample1))
        assertTrue(containsWakeWord(sample2))
        assertTrue(containsWakeWord(sample3))
        assertFalse(containsWakeWord(sample4))
    }

    @Test
    fun testCommandExtraction() {
        val command = extractCommand("Hey Jarvis what is the weather today")
        assertEquals("what is the weather today", command)

        val mathCommand = extractCommand("Jarvis calculate 25 * 4")
        assertEquals("calculate 25 * 4", mathCommand)

        val blankWake = extractCommand("Jarvis")
        assertEquals("", blankWake)
    }

    @Test
    fun testPcmRmsCalculation() {
        val samplePcm = shortArrayOf(0, 500, 1000, 1500, 2000, 1500, 1000, 500, 0)
        var sum = 0.0
        for (sample in samplePcm) {
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / samplePcm.size)
        val normalized = (rms / 2500.0).toFloat().coerceIn(0.08f, 1.0f)
        assertTrue(normalized in 0.08f..1.0f)
        assertTrue(rms > 0.0)
    }

    private fun containsWakeWord(text: String): Boolean {
        val lower = text.lowercase(Locale.ROOT)
        return lower.contains("hey jarvis") ||
                lower.contains("jarvis") ||
                lower.contains("hello jarvis") ||
                lower.contains("hi jarvis") ||
                lower.contains("ok jarvis") ||
                lower.contains("okay jarvis") ||
                lower.contains("yo jarvis") ||
                lower.contains("wake up")
    }

    private fun extractCommand(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        return lower
            .replace("hey jarvis", "")
            .replace("hello jarvis", "")
            .replace("hi jarvis", "")
            .replace("okay jarvis", "")
            .replace("ok jarvis", "")
            .replace("yo jarvis", "")
            .replace("wake up jarvis", "")
            .replace("wake up", "")
            .replace("jarvis", "")
            .trim()
            .trimStart(',', ':', '-', ' ')
    }
}
