package com.ethnym.feature.lock

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ethnym.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Asks the user to authenticate; [AppLock] passes whether only biometrics will do. */
typealias Authenticate = suspend (biometricsOnly: Boolean) -> AuthResult

/**
 * Keeps ETHnym behind biometrics, so someone holding the unlocked phone can't look through the
 * wallets. It only hides the app: keystores stay encrypted with their passwords either way, and
 * sending still asks for one. Works like the iOS app's lock.
 */
@Singleton
class AppLock @Inject constructor(
    private val preferences: UserPreferencesRepository,
    private val device: DeviceAuthenticator,
) {
    /** Whether the user turned the lock on. Null until it's read at launch; the app stays covered till then. */
    var enabled by mutableStateOf<Boolean?>(null)
        private set

    /** Whether the app is behind the lock screen. */
    var locked by mutableStateOf(false)
        private set

    var authenticating by mutableStateOf(false)
        private set

    var biometricStatus by mutableStateOf(device.biometricStatus())
        private set

    internal var now: () -> Long = SystemClock::elapsedRealtime

    /**
     * Set when the app locks or comes back locked, and cleared by the first prompt, so cancelling
     * it leaves the lock screen up instead of prompting again.
     */
    private var promptsAutomatically = false
    private var openingOwnScreen = false
    private var backgroundedAt: Long? = null

    /** Reads the setting once per process. With the lock on, the app starts locked. */
    suspend fun load() {
        if (enabled != null) return
        val on = preferences.userPreferences.first().appLockEnabled
        enabled = on
        locked = on
        promptsAutomatically = on
    }

    /** Re-reads the sensor, which can be set up or turned off while ETHnym is in the background. */
    fun refreshStatus() {
        biometricStatus = device.biometricStatus()
    }

    /**
     * Turns the lock on or off once the user authenticates. Turning it on takes biometrics, to show
     * they work; turning it off also takes the screen lock, in case they stopped working. Returns
     * why it didn't change.
     */
    suspend fun setEnabled(on: Boolean, authenticate: Authenticate): String? {
        if (on == enabled || authenticating) return null
        authenticating = true
        try {
            when (val result = authenticate(on)) {
                AuthResult.Success, AuthResult.NoScreenLock -> Unit
                AuthResult.Canceled -> return null
                is AuthResult.Error -> {
                    refreshStatus()
                    return result.message
                }
            }
            preferences.setAppLockEnabled(on)
            enabled = on
            return null
        } finally {
            authenticating = false
        }
    }

    /** Shows the lock screen, if the lock is on. */
    fun lock() {
        if (enabled != true) return
        locked = true
        promptsAutomatically = true
    }

    /** Asks for biometrics, or the screen lock, and unlocks on success. Returns why it didn't. */
    suspend fun unlock(authenticate: Authenticate): String? {
        if (!locked || authenticating) return null
        promptsAutomatically = false
        authenticating = true
        try {
            when (val result = authenticate(false)) {
                // Without a screen lock, anyone holding the phone can already get in. Don't strand them.
                AuthResult.Success, AuthResult.NoScreenLock -> locked = false
                AuthResult.Canceled -> Unit
                is AuthResult.Error -> return result.message
            }
            return null
        } finally {
            authenticating = false
        }
    }

    /** The prompt the app shows by itself when it comes to the front locked. After that, it's the lock screen's button. */
    suspend fun unlockAutomatically(authenticate: Authenticate): String? =
        if (promptsAutomatically) unlock(authenticate) else null

    /**
     * The app is about to open a screen of its own, such as the file picker or a link. Leaving for
     * it doesn't lock straight away, so saving a backup doesn't ask again on the way back.
     */
    fun willOpenOwnScreen() {
        openingOwnScreen = true
    }

    /** Back on screen without having left it, as after a screen that only covered part of it. */
    fun didResume() {
        openingOwnScreen = false
    }

    /**
     * The app left the screen. It locks straight away, unless it left for a screen it opened
     * itself: then only once it's been away [OWN_SCREEN_GRACE_MS].
     */
    fun didEnterBackground() {
        val ownScreen = openingOwnScreen
        openingOwnScreen = false
        if (enabled != true || locked) return
        if (ownScreen) backgroundedAt = now() else lock()
    }

    /** The app is back on screen. */
    fun willEnterForeground() {
        backgroundedAt?.let { if (now() - it >= OWN_SCREEN_GRACE_MS) lock() }
        backgroundedAt = null
        // A prompt still open (the screen lock's own screen on older Android) is the one that counts.
        if (locked && !authenticating) promptsAutomatically = true
        refreshStatus()
    }

    companion object {
        const val OWN_SCREEN_GRACE_MS = 60_000L
    }
}
