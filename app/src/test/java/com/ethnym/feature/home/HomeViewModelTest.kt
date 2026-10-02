package com.ethnym.feature.home

import com.ethnym.data.settings.UserPreferences
import com.ethnym.data.settings.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val preferences = FakeUserPreferencesRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun uiState_isLoading_beforeCollection() {
        val viewModel = HomeViewModel(preferences)

        assertEquals(HomeUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun uiState_followsHideBalancesPreference() = runTest {
        val viewModel = HomeViewModel(preferences)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }

        assertEquals(HomeUiState.Ready(hideBalances = false), viewModel.uiState.value)

        preferences.setHideBalances(true)

        assertEquals(HomeUiState.Ready(hideBalances = true), viewModel.uiState.value)
    }
}

private class FakeUserPreferencesRepository : UserPreferencesRepository {
    private val state = MutableStateFlow(UserPreferences())

    override val userPreferences: Flow<UserPreferences> = state

    override suspend fun setHideBalances(hide: Boolean) {
        state.update { it.copy(hideBalances = hide) }
    }
}
