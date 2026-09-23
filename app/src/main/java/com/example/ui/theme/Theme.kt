package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JarvisColorScheme = darkColorScheme(
    primary = JarvisCyan,
    onPrimary = JarvisBackground,
    primaryContainer = JarvisSurfaceHighlight,
    onPrimaryContainer = JarvisCyanBright,
    secondary = JarvisGold,
    onSecondary = JarvisBackground,
    secondaryContainer = JarvisGoldDark,
    onSecondaryContainer = JarvisGoldBright,
    tertiary = JarvisGreen,
    onTertiary = JarvisBackground,
    error = JarvisRedAlert,
    onError = JarvisBackground,
    background = JarvisBackground,
    onBackground = JarvisTextPrimary,
    surface = JarvisSurface,
    onSurface = JarvisTextPrimary,
    surfaceVariant = JarvisSurfaceVariant,
    onSurfaceVariant = JarvisTextSecondary,
    outline = JarvisBorder,
    outlineVariant = JarvisSurfaceHighlight
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisColorScheme,
        typography = Typography,
        content = content
    )
}
