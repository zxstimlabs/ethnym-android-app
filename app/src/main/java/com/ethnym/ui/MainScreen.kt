package com.ethnym.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.R
import com.ethnym.data.settings.ThemeMode
import com.ethnym.data.settings.UserPreferencesRepository
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.feature.activity.ActivityTab
import com.ethnym.feature.addressbook.AddressBookTab
import com.ethnym.feature.backup.BackupTab
import com.ethnym.feature.send.SendTab
import com.ethnym.feature.settings.SettingsTab
import com.ethnym.feature.wallets.WalletsTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The web wallet's mobile tabs, in the same order. */
enum class MainTab(@param:StringRes val label: Int, @param:DrawableRes val icon: Int) {
    Wallets(R.string.tab_wallets, R.drawable.ic_account_balance_wallet),
    AddressBook(R.string.tab_address_book, R.drawable.ic_contacts),
    Send(R.string.tab_send, R.drawable.ic_arrow_outward),
    Activity(R.string.tab_activity, R.drawable.ic_receipt_long),
    Backup(R.string.tab_backup, R.drawable.ic_save),
    Settings(R.string.tab_settings, R.drawable.ic_settings),
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    data class UiState(val hasActiveWallet: Boolean = false, val themeMode: ThemeMode = ThemeMode.System)

    val uiState: StateFlow<UiState> = combine(
        walletRepository.activeWallet,
        userPreferencesRepository.userPreferences,
    ) { active, prefs -> UiState(hasActiveWallet = active != null, themeMode = prefs.themeMode) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    /** Deselects the wallet (the web wallet's log-out button). */
    fun logOut() {
        viewModelScope.launch { walletRepository.setActive(null) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onManageWallets: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.Wallets) }
    val dark = when (uiState.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(tab.label)) },
                actions = {
                    IconButton(onClick = { viewModel.setThemeMode(if (dark) ThemeMode.Light else ThemeMode.Dark) }) {
                        Icon(
                            painterResource(if (dark) R.drawable.ic_light_mode else R.drawable.ic_dark_mode),
                            contentDescription = stringResource(R.string.toggle_theme),
                        )
                    }
                    IconButton(onClick = viewModel::logOut, enabled = uiState.hasActiveWallet) {
                        Icon(painterResource(R.drawable.ic_logout), contentDescription = stringResource(R.string.log_out))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                MainTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = item == tab,
                        onClick = { tab = item },
                        // Icon only; the tab name stays as the content description for screen readers.
                        icon = { Icon(painterResource(item.icon), contentDescription = stringResource(item.label)) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            when (tab) {
                MainTab.Wallets -> WalletsTab(onManageWallets = onManageWallets)
                MainTab.AddressBook -> AddressBookTab()
                MainTab.Send -> SendTab()
                MainTab.Activity -> ActivityTab()
                MainTab.Backup -> BackupTab()
                MainTab.Settings -> SettingsTab()
            }
        }
    }
}
