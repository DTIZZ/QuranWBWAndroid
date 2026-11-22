package com.quranwbw.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF0F6B5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFF3EC),
    onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF4A635D),
    secondaryContainer = Color(0xFFCDE8E0),
    surface = Color(0xFFFAFDFB),
    surfaceVariant = Color(0xFFE6EEEB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7AD8C8),
    onPrimary = Color(0xFF003730),
    primaryContainer = Color(0xFF005046),
    onPrimaryContainer = Color(0xFFCFF3EC),
    secondary = Color(0xFFB1CCC4),
    surface = Color(0xFF0F1513),
    surfaceVariant = Color(0xFF232C29)
)

@Composable
fun QuranWBWTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
