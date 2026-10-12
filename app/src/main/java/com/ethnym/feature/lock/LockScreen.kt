package com.ethnym.feature.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ethnym.R
import com.ethnym.ui.theme.LocalDarkTheme
import kotlinx.coroutines.launch

/**
 * The app behind the lock. While locked it stays composed, so open forms keep what's typed in
 * them, but it isn't drawn or read out, and the lock screen sits in a window of its own above it.
 * A window, because sheets, dialogs and menus are windows too, and it has to cover them.
 */
@Composable
fun AppLockGate(lock: AppLock, content: @Composable () -> Unit) {
    // The setting is read in the first few milliseconds after launch; show nothing rather than the wallets.
    if (lock.enabled == null) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    val locked = lock.locked
    Box(if (locked) Modifier.clearAndSetSemantics {}.drawWithContent {} else Modifier) {
        content()
    }
    if (locked) LockWindow(lock)
}

/**
 * Prompts by itself once each time the app comes to the front locked; cancelling that leaves the
 * Unlock button. Back leaves the app, as it would from its first screen.
 */
@Composable
private fun LockWindow(lock: AppLock) {
    val activity = LocalActivity.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    val title = stringResource(R.string.unlock_ethnym)
    val cancel = stringResource(R.string.cancel)
    val authenticate: Authenticate = { biometricsOnly -> activity.authenticate(title, cancel, biometricsOnly) }

    // The prompt runs in this window's scope, not the effect's, so pausing for the screen lock's
    // own screen on older Android doesn't cancel it.
    LifecycleResumeEffect(Unit) {
        scope.launch { lock.unlockAutomatically(authenticate)?.let { error = it } }
        onPauseOrDispose {}
    }

    Dialog(
        onDismissRequest = { activity.moveTaskToBack(true) },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        val darkTheme = LocalDarkTheme.current
        SideEffect {
            val window = (view.parent as DialogWindowProvider).window
            // Up at once, with nothing dimmed or faded in behind it.
            window.setWindowAnimations(0)
            window.setDimAmount(0f)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        LockScreen(
            error = error,
            authenticating = lock.authenticating,
            onUnlock = { scope.launch { error = lock.unlock(authenticate) } },
        )
    }
}

/** The app's background with the biometric icon, and Unlock at the bottom, within thumb's reach. */
@Composable
private fun LockScreen(error: String?, authenticating: Boolean, onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Icon(
                painterResource(R.drawable.ic_fingerprint),
                contentDescription = stringResource(R.string.app_locked),
                modifier = Modifier.size(72.dp),
            )
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onUnlock,
                enabled = !authenticating,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
            ) {
                Text(stringResource(R.string.unlock))
            }
        }
    }
}
