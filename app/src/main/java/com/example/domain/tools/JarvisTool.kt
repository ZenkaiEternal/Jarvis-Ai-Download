package com.example.domain.tools

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector

data class ToolParameter(
    val name: String,
    val description: String,
    val isRequired: Boolean = true,
    val defaultValue: String? = null
)

data class ToolResult(
    val success: Boolean,
    val summary: String,
    val displayData: String? = null,
    val directAction: (() -> Unit)? = null,
    val requiresUserConfirmation: Boolean = false,
    val confirmationPrompt: String? = null
)

interface JarvisTool {
    val id: String
    val name: String
    val description: String
    val category: String
    val parameters: List<ToolParameter>
    val isConsequential: Boolean // Whether this tool performs an action that might alter state or require confirmation

    suspend fun execute(
        context: Context,
        params: Map<String, String>
    ): ToolResult
}
