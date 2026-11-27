package com.najim.android.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.najim.android.settings.AppearanceMode

private val LightPalette = lightColorScheme(
    primary = Color(0xFF174DBA), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF), onPrimaryContainer = Color(0xFF092D76),
    secondary = Color(0xFF006B63), onSecondary = Color.White,
    secondaryContainer = Color(0xFFC4F1E9), onSecondaryContainer = Color(0xFF003C37),
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF152238),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF152238),
    surfaceVariant = Color(0xFFE6EBF4), onSurfaceVariant = Color(0xFF414B60),
    outline = Color(0xFF707A8D), outlineVariant = Color(0xFFC6CEDC),
    error = Color(0xFFB42318), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002)
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFA9C7FF), onPrimary = Color(0xFF082C70),
    primaryContainer = Color(0xFF173F84), onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF80D8CB), onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF005049), onSecondaryContainer = Color(0xFFB2F0E4),
    background = Color(0xFF0C1525), onBackground = Color(0xFFE5ECFA),
    surface = Color(0xFF131F33), onSurface = Color(0xFFE5ECFA),
    surfaceVariant = Color(0xFF243249), onSurfaceVariant = Color(0xFFC5D0E2),
    outline = Color(0xFF8B9BB4), outlineVariant = Color(0xFF40516C),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6)
)

private val NajimShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
internal fun NajimTheme(mode: AppearanceMode, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (mode.usesDark(isSystemInDarkTheme())) DarkPalette else LightPalette,
        shapes = NajimShapes,
        content = content
    )
}
