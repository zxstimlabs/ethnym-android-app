package com.ethnym

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.data.settings.ThemeMode
import com.ethnym.data.settings.UserPreferencesRepository
import com.ethnym.ui.EthnymApp
import com.ethnym.ui.theme.EthnymTheme
import com.ethnym.ui.theme.LocalDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            EthnymTheme(themeMode = themeMode) {
                // enableEdgeToEdge() colors the system bar icons for the system theme, which the
                // in-app theme can override, so say which one is showing.
                val darkTheme = LocalDarkTheme.current
                DisposableEffect(darkTheme) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { darkTheme },
                        navigationBarStyle = SystemBarStyle.auto(LightScrim, DarkScrim) { darkTheme },
                    )
                    onDispose {}
                }
                EthnymApp()
            }
        }
    }
}

// enableEdgeToEdge()'s own scrims for three-button navigation.
private val LightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val DarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = userPreferencesRepository.userPreferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)
}
