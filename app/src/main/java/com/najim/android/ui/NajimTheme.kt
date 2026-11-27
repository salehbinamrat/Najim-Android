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
    primary = Color(0xFF761C40),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF8DCE6),
    onPrimaryContainer = Color(0xFF491027),
    secondary = Color(0xFF745460),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF5E3E9),
    onSecondaryContainer = Color(0xFF442A34),
    tertiary = Color(0xFF38665D),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD4EEE7),
    onTertiaryContainer = Color(0xFF153F37),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF261C20),
    surface = Color(0xFFFFFBFC),
    onSurface = Color(0xFF261C20),
    surfaceVariant = Color(0xFFF4E9ED),
    onSurfaceVariant = Color(0xFF57434B),
    outline = Color(0xFF826E76),
    outlineVariant = Color(0xFFD8C5CD),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    inverseSurface = Color(0xFF372C31),
    inverseOnSurface = Color(0xFFF9EEF2),
    inversePrimary = Color(0xFFFFB2CA),
    surfaceTint = Color(0xFF761C40),
    surfaceDim = Color(0xFFE8DCE1),
    surfaceBright = Color(0xFFFFFBFC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF3F6),
    surfaceContainer = Color(0xFFF5EBEF),
    surfaceContainerHigh = Color(0xFFF0E4E9),
    surfaceContainerHighest = Color(0xFFEADDE3)
)

private val DarkPalette = darkColorScheme(
    primary = Color(0xFFFFB2CA),
    onPrimary = Color(0xFF52132E),
    primaryContainer = Color(0xFF71334D),
    onPrimaryContainer = Color(0xFFFFDAE6),
    secondary = Color(0xFFDDBCC8),
    onSecondary = Color(0xFF402A34),
    secondaryContainer = Color(0xFF503640),
    onSecondaryContainer = Color(0xFFF8DDE7),
    tertiary = Color(0xFFA5D8CB),
    onTertiary = Color(0xFF163F36),
    tertiaryContainer = Color(0xFF29554B),
    onTertiaryContainer = Color(0xFFD4EEE7),
    background = Color(0xFF161216),
    onBackground = Color(0xFFF4E9ED),
    surface = Color(0xFF1C171B),
    onSurface = Color(0xFFF4E9ED),
    surfaceVariant = Color(0xFF372B32),
    onSurfaceVariant = Color(0xFFE1CCD4),
    outline = Color(0xFFAE98A1),
    outlineVariant = Color(0xFF51414A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    inverseSurface = Color(0xFFF4E9ED),
    inverseOnSurface = Color(0xFF352B30),
    inversePrimary = Color(0xFF761C40),
    surfaceTint = Color(0xFFFFB2CA),
    surfaceDim = Color(0xFF161216),
    surfaceBright = Color(0xFF41363C),
    surfaceContainerLowest = Color(0xFF110E11),
    surfaceContainerLow = Color(0xFF211B20),
    surfaceContainer = Color(0xFF282126),
    surfaceContainerHigh = Color(0xFF30282E),
    surfaceContainerHighest = Color(0xFF3A3036)
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
