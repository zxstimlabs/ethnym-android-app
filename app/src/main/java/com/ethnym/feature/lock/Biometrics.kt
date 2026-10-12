package com.ethnym.feature.lock

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume

/** Whether the device can take biometrics for the app lock. */
enum class BiometricStatus { Available, NotEnrolled, NoHardware, Unavailable }

sealed interface AuthResult {
    data object Success : AuthResult

    /** The user backed out; nothing to report. */
    data object Canceled : AuthResult

    /** The device has no screen lock at all, so there is nothing to ask for. */
    data object NoScreenLock : AuthResult

    /** Why it failed, in the system's words, such as too many attempts. */
    data class Error(val message: String) : AuthResult
}

/** What the device offers, behind an interface so [AppLock] runs in unit tests. */
interface DeviceAuthenticator {
    fun biometricStatus(): BiometricStatus
}

class SystemDeviceAuthenticator @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : DeviceAuthenticator {
    // Weak biometrics include most phones' face unlock. Enough here: the lock only hides the app, and
    // nothing it guards is decrypted with them.
    override fun biometricStatus(): BiometricStatus = when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK)) {
        BiometricManager.BIOMETRIC_SUCCESS -> BiometricStatus.Available
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricStatus.NotEnrolled
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> BiometricStatus.NoHardware
        else -> BiometricStatus.Unavailable
    }
}

/**
 * Shows the system biometric prompt. Unless [biometricsOnly], it also offers the screen lock (PIN,
 * pattern or password), as the iOS app falls back to the passcode.
 */
suspend fun FragmentActivity.authenticate(title: String, cancel: String, biometricsOnly: Boolean): AuthResult {
    if (!biometricsOnly && !getSystemService(KeyguardManager::class.java).isDeviceSecure) return AuthResult.NoScreenLock
    // A prompt started after onSaveInstanceState never shows, and would never answer.
    if (supportFragmentManager.isStateSaved) return AuthResult.Canceled
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setConfirmationRequired(false)
        .apply {
            if (biometricsOnly) {
                setAllowedAuthenticators(BIOMETRIC_WEAK)
                setNegativeButtonText(cancel)
            } else {
                setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
            }
        }
        .build()
    return suspendCancellableCoroutine { continuation ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                if (continuation.isActive) continuation.resume(AuthResult.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (!continuation.isActive) return
                val canceled = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                continuation.resume(if (canceled) AuthResult.Canceled else AuthResult.Error(errString.toString()))
            }

            // A single unrecognized attempt: the prompt says so itself and stays up for another.
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback)
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
        prompt.authenticate(info)
    }
}
