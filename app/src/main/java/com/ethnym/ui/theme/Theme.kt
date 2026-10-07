package com.ethnym.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import com.ethnym.data.settings.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = Black,
    onPrimary = White,
    primaryContainer = Grey90,
    onPrimaryContainer = Black,
    inversePrimary = White,
    secondary = Grey20,
    onSecondary = White,
    secondaryContainer = Grey90,
    onSecondaryContainer = Black,
    tertiary = Grey20,
    onTertiary = White,
    tertiaryContainer = Grey90,
    onTertiaryContainer = Black,
    background = White,
    onBackground = Black,
    surface = White,
    onSurface = Black,
    surfaceVariant = Grey93,
    onSurfaceVariant = Grey30,
    surfaceTint = Black,
    inverseSurface = Grey10,
    inverseOnSurface = White,
    outline = Grey50,
    outlineVariant = Grey80,
    surfaceBright = White,
    surfaceDim = Grey90,
    surfaceContainerLowest = White,
    surfaceContainerLow = Grey96,
    surfaceContainer = Grey93,
    surfaceContainerHigh = Grey90,
    surfaceContainerHighest = Grey80,
)

private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    primaryContainer = Grey20,
    onPrimaryContainer = White,
    inversePrimary = Black,
    secondary = Grey80,
    onSecondary = Black,
    secondaryContainer = Grey20,
    onSecondaryContainer = White,
    tertiary = Grey80,
    onTertiary = Black,
    tertiaryContainer = Grey20,
    onTertiaryContainer = White,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Grey15,
    onSurfaceVariant = Grey70,
    surfaceTint = White,
    inverseSurface = Grey90,
    inverseOnSurface = Black,
    outline = Grey50,
    outlineVariant = Grey30,
    surfaceBright = Grey20,
    surfaceDim = Black,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Grey05,
    surfaceContainer = Grey10,
    surfaceContainerHigh = Grey15,
    surfaceContainerHighest = Grey20,
)

/**
 * Whether the app is drawn dark. Follows [ThemeMode], which can override the system setting, so
 * `-night` resources can't stand in for it.
 */
val LocalDarkTheme = staticCompositionLocalOf { false }

@Composable
fun EthnymTheme(
    themeMode: ThemeMode = ThemeMode.System,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
