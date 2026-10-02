package com.ethnym.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethnym.data.settings.UserPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = userPreferencesRepository.userPreferences
        .map { prefs -> HomeUiState.Ready(hideBalances = prefs.hideBalances) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState.Loading,
        )
}

sealed interface HomeUiState {
    data object Loading : HomeUiState

    data class Ready(val hideBalances: Boolean) : HomeUiState
}
