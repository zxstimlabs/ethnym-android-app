package com.ethnym.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

// Navigation 3 destinations. Keys are @Serializable so the back stack survives process death.

@Serializable
data object Home : NavKey

@Serializable
data object Settings : NavKey
