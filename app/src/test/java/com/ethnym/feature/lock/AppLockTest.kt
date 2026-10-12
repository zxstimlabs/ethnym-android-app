package com.ethnym.feature.lock

import com.ethnym.data.settings.ThemeMode
import com.ethnym.data.settings.UserPreferences
import com.ethnym.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLockTest {

    private var clock = 0L
    private val prompts = mutableListOf<Boolean>()

    private fun appLock(enabled: Boolean, preferences: FakePreferences = FakePreferences(enabled)) =
        AppLock(preferences, FakeDevice()).also { it.now = { clock } }

    private fun answering(result: AuthResult): Authenticate = { biometricsOnly ->
        prompts += biometricsOnly
        result
    }

    @Test
    fun launch_isLockedOnlyWithTheLockOn() = runTest {
        val on = appLock(enabled = true).apply { load() }
        assertTrue(on.locked)
        val off = appLock(enabled = false).apply { load() }
        assertFalse(off.locked)
    }

    @Test
    fun launch_promptsByItselfOnceThenLeavesItToTheButton() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlockAutomatically(answering(AuthResult.Canceled))
        lock.unlockAutomatically(answering(AuthResult.Success))
        assertEquals(1, prompts.size)
        assertTrue(lock.locked)

        lock.unlock(answering(AuthResult.Success))
        assertFalse(lock.locked)
    }

    @Test
    fun unlock_offersTheScreenLockToo() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlock(answering(AuthResult.Success))
        assertEquals(listOf(false), prompts)
    }

    @Test
    fun leaving_locksStraightAwayAndPromptsAgainOnReturn() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlock(answering(AuthResult.Success))

        lock.didEnterBackground()
        assertTrue(lock.locked)
        lock.willEnterForeground()
        lock.unlockAutomatically(answering(AuthResult.Success))
        assertFalse(lock.locked)
    }

    @Test
    fun cancelledPrompt_comesBackNextTimeTheAppDoes() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlockAutomatically(answering(AuthResult.Canceled))
        lock.didEnterBackground()
        lock.willEnterForeground()
        lock.unlockAutomatically(answering(AuthResult.Success))
        assertFalse(lock.locked)
    }

    @Test
    fun ownScreen_staysUnlockedWithinTheGracePeriod() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlock(answering(AuthResult.Success))

        lock.willOpenOwnScreen()
        lock.didEnterBackground()
        clock += AppLock.OWN_SCREEN_GRACE_MS - 1
        lock.willEnterForeground()
        assertFalse(lock.locked)

        lock.willOpenOwnScreen()
        lock.didEnterBackground()
        clock += AppLock.OWN_SCREEN_GRACE_MS
        lock.willEnterForeground()
        assertTrue(lock.locked)
    }

    @Test
    fun ownScreen_thatNeverCoveredTheAppDoesNotExcuseTheNextLeave() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlock(answering(AuthResult.Success))
        lock.willOpenOwnScreen()
        lock.didResume()
        lock.didEnterBackground()
        assertTrue(lock.locked)
    }

    @Test
    fun noScreenLock_unlocksRatherThanStrandingTheUser() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        lock.unlock(answering(AuthResult.NoScreenLock))
        assertFalse(lock.locked)
    }

    @Test
    fun error_staysLockedAndSaysWhy() = runTest {
        val lock = appLock(enabled = true).apply { load() }
        val message = lock.unlock(answering(AuthResult.Error("Too many attempts")))
        assertEquals("Too many attempts", message)
        assertTrue(lock.locked)
    }

    @Test
    fun turningOn_takesBiometricsAndIsSaved() = runTest {
        val preferences = FakePreferences(enabled = false)
        val lock = appLock(enabled = false, preferences).apply { load() }

        assertNull(lock.setEnabled(true, answering(AuthResult.Canceled)))
        assertEquals(false, lock.enabled)

        lock.setEnabled(true, answering(AuthResult.Success))
        assertEquals(listOf(true, true), prompts)
        assertEquals(true, lock.enabled)
        assertTrue(preferences.state.value.appLockEnabled)
        assertFalse("turning it on doesn't lock the app there and then", lock.locked)
    }

    @Test
    fun turningOff_alsoTakesTheScreenLock() = runTest {
        val preferences = FakePreferences(enabled = true)
        val lock = appLock(enabled = true, preferences).apply { load() }
        lock.unlock(answering(AuthResult.Success))
        prompts.clear()

        lock.setEnabled(false, answering(AuthResult.Success))
        assertEquals(listOf(false), prompts)
        assertFalse(preferences.state.value.appLockEnabled)
        lock.didEnterBackground()
        assertFalse(lock.locked)
    }

    private class FakeDevice : DeviceAuthenticator {
        override fun biometricStatus() = BiometricStatus.Available
    }

    private class FakePreferences(enabled: Boolean) : UserPreferencesRepository {
        val state = MutableStateFlow(UserPreferences(appLockEnabled = enabled))
        override val userPreferences: Flow<UserPreferences> = state

        override suspend fun setThemeMode(mode: ThemeMode) = state.update { it.copy(themeMode = mode) }

        override suspend fun setAppLockEnabled(enabled: Boolean) = state.update { it.copy(appLockEnabled = enabled) }
    }
}
