package com.ethnym.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ethnym.R

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
    Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    Font(R.font.jetbrains_mono_semibold, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_bold, FontWeight.Bold),
)

private val Default = Typography()

/** Material 3's type scale, set in JetBrains Mono throughout. */
val Typography = Typography(
    displayLarge = Default.displayLarge.copy(fontFamily = JetBrainsMono),
    displayMedium = Default.displayMedium.copy(fontFamily = JetBrainsMono),
    displaySmall = Default.displaySmall.copy(fontFamily = JetBrainsMono),
    headlineLarge = Default.headlineLarge.copy(fontFamily = JetBrainsMono),
    headlineMedium = Default.headlineMedium.copy(fontFamily = JetBrainsMono),
    headlineSmall = Default.headlineSmall.copy(fontFamily = JetBrainsMono),
    titleLarge = Default.titleLarge.copy(fontFamily = JetBrainsMono),
    titleMedium = Default.titleMedium.copy(fontFamily = JetBrainsMono),
    titleSmall = Default.titleSmall.copy(fontFamily = JetBrainsMono),
    bodyLarge = Default.bodyLarge.copy(fontFamily = JetBrainsMono),
    bodyMedium = Default.bodyMedium.copy(fontFamily = JetBrainsMono),
    bodySmall = Default.bodySmall.copy(fontFamily = JetBrainsMono),
    labelLarge = Default.labelLarge.copy(fontFamily = JetBrainsMono),
    labelMedium = Default.labelMedium.copy(fontFamily = JetBrainsMono),
    labelSmall = Default.labelSmall.copy(fontFamily = JetBrainsMono),
)
