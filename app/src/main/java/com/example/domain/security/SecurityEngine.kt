package com.example.domain.security

import com.example.data.repository.JarvisRepository

data class ConfirmationRequest(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val description: String,
    val onConfirm: suspend () -> Unit,
    val onCancel: (() -> Unit)? = null
)

class SecurityEngine(private val repository: JarvisRepository) {

    private val destructiveKeywords = listOf(
        "delete all", "wipe", "clear memory", "purge", "format", "reset all", "erase"
    )

    fun requiresConfirmation(actionName: String, params: Map<String, String>): Boolean {
        val combined = "$actionName ${params.values.joinToString(" ")}".lowercase()
        return destructiveKeywords.any { combined.contains(it) }
    }

    fun validateInput(input: String): ValidationResult {
        if (input.isBlank()) {
            return ValidationResult(false, "Input stream empty.")
        }
        if (input.length > 10000) {
            return ValidationResult(false, "Input exceeds maximum payload size.")
        }
        return ValidationResult(true, "Input validated.")
    }

    fun maskSecrets(text: String, secretCode: String? = null): String {
        var masked = text
            .replace(Regex("(?i)(code\\s*(is|:)?\\s*)([a-z0-9\\-]{3,})"), "$1[REDACTED]")
            .replace(Regex("(?i)(AIza[0-9A-Za-z\\-_]{20,})"), "[API_KEY_REDACTED]")
        if (!secretCode.isNullOrBlank() && secretCode.length >= 3) {
            masked = masked.replace(secretCode, "[SECRET_REDACTED]", ignoreCase = true)
        }
        return masked
    }

    suspend fun auditAction(action: String, details: String, isApproved: Boolean, secretToMask: String? = null) {
        repository.logAction(
            action = action,
            details = maskSecrets(details, secretToMask),
            status = if (isApproved) "APPROVED_AND_EXECUTED" else "ABORTED_OR_DENIED"
        )
    }
}

data class ValidationResult(
    val isValid: Boolean,
    val message: String
)
