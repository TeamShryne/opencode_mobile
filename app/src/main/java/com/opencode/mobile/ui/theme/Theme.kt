package com.opencode.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9E8CFC),
    onPrimary = Color(0xFF1B1145),
    primaryContainer = Color(0xFF2E2270),
    onPrimaryContainer = Color(0xFFE4DEFF),
    secondary = Color(0xFF4DD6C1),
    tertiary = Color(0xFFFFB86B),
    background = Color(0xFF0B0B12),
    surface = Color(0xFF131320),
    surfaceVariant = Color(0xFF1C1C2C),
    onBackground = Color(0xFFF2F0FF),
    onSurface = Color(0xFFF2F0FF)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF5B4BC4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DEFF),
    onPrimaryContainer = Color(0xFF1B1145),
    secondary = Color(0xFF0E7C6B),
    tertiary = Color(0xFF9A5B00),
    background = Color(0xFFF7F5FF),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEAFF)
)

@Composable
fun OpencodeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
