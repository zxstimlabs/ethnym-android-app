package com.ethnym.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethnym.data.settings.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = userPreferencesRepository.userPreferences
        .map { prefs -> SettingsUiState.Ready(hideBalances = prefs.hideBalances) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState.Loading,
        )

    fun setHideBalances(hide: Boolean) {
        viewModelScope.launch { userPreferencesRepository.setHideBalances(hide) }
    }
}

sealed interface SettingsUiState {
    data object Loading : SettingsUiState

    data class Ready(val hideBalances: Boolean) : SettingsUiState
}
