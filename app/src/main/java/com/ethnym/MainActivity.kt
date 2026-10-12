package com.ethnym

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.ethnym.data.settings.ThemeMode
import com.ethnym.data.settings.UserPreferencesRepository
import com.ethnym.feature.lock.AppLock
import com.ethnym.feature.lock.AppLockGate
import com.ethnym.ui.EthnymApp
import com.ethnym.ui.theme.EthnymTheme
import com.ethnym.ui.theme.LocalDarkTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A FragmentActivity because the app lock's biometric prompt needs one. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainActivityViewModel by viewModels()

    @Inject lateinit var appLock: AppLock

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        lifecycleScope.launch { appLock.load() }
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> appLock.willEnterForeground()
                    Lifecycle.Event.ON_RESUME -> appLock.didResume()
                    // Rotating recreates the activity; that isn't leaving the app.
                    Lifecycle.Event.ON_STOP -> if (!isChangingConfigurations) appLock.didEnterBackground()
                    else -> Unit
                }
            },
        )
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
                // With the lock on, Recents shows a blank card rather than the wallets (Android 13 and up).
                val lockOn = appLock.enabled == true
                DisposableEffect(lockOn) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) setRecentsScreenshotEnabled(!lockOn)
                    onDispose {}
                }
                AppLockGate(appLock) { EthnymApp() }
            }
        }
    }

    // Every screen the app opens itself comes through here: activity-result launchers (the file
    // picker) and links alike. The lock lets the app leave for those without locking straight away.
    @Deprecated("Deprecated in Java")
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        appLock.willOpenOwnScreen()
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
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
