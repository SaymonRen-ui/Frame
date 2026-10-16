package com.saymon.frame.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF4A5DF0),
    onPrimary = Color.White,
    secondary = Color(0xFF5B5D72),
    surface = Color(0xFFFBF8FF),
    surfaceContainer = Color(0xFFF0EDF5),
    surfaceContainerHigh = Color(0xFFEAE7EF),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    onPrimary = Color(0xFF0A154D),
    secondary = Color(0xFFC5C6DD),
    surface = Color(0xFF131318),
    surfaceContainer = Color(0xFF1D1D24),
    surfaceContainerHigh = Color(0xFF282832),
)

@Composable
fun FrameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
