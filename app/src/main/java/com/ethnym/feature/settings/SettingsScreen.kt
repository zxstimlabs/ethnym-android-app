package com.ethnym.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.BuildConfig
import com.ethnym.R
import com.ethnym.data.model.RpcEntry
import com.ethnym.data.model.ViewOnlyWallet
import com.ethnym.data.model.Wallet
import com.ethnym.data.model.WalletSettings
import com.ethnym.data.settings.SettingsRepository
import com.ethnym.data.settings.ThemeMode
import com.ethnym.data.settings.UserPreferencesRepository
import com.ethnym.data.settings.validateRpcUrl
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.feature.wallets.ViewOnlyTag
import com.ethnym.ui.components.AddressText
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionCard
import com.ethnym.ui.components.Tag
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val walletRepository: WalletRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val settings: StateFlow<WalletSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeWallet: StateFlow<Wallet?> = walletRepository.activeWallet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val themeMode: StateFlow<ThemeMode> = userPreferencesRepository.userPreferences
        .map { it.themeMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeMode.System)

    var newName by mutableStateOf("")
    var newUrl by mutableStateOf("")
    var addError by mutableStateOf<String?>(null)
        private set

    fun onUrlChange(value: String) {
        newUrl = value
        addError = null
    }

    fun addRpc() {
        validateRpcUrl(newUrl)?.let {
            addError = it
            return
        }
        viewModelScope.launch {
            settingsRepository.addRpc(newName.trim().ifEmpty { null }, newUrl.trim())
            newName = ""
            newUrl = ""
        }
    }

    /** Takes effect on the next request; no reload needed (the web wallet reloads the page). */
    fun select(entry: RpcEntry?) {
        viewModelScope.launch { settingsRepository.setActiveRpc(entry) }
    }

    fun delete(id: String) {
        viewModelScope.launch { settingsRepository.deleteRpc(id) }
    }

    fun setOffline(offline: Boolean) {
        viewModelScope.launch { settingsRepository.setOfflineMode(offline) }
    }

    /** Deselects the wallet, as the web wallet's Log out does. Its keystore stays on the device. */
    fun logOut() {
        viewModelScope.launch { walletRepository.setActive(null) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { userPreferencesRepository.setThemeMode(mode) }
    }
}

/**
 * Settings, opened from the top bar: the active wallet and Log out, RPC endpoint, offline mode, the
 * upcoming VPN relay and appearance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        SettingsContent(
            viewModel = viewModel,
            onLoggedOut = onBack,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        )
    }
}

@Composable
private fun SettingsContent(viewModel: SettingsViewModel, onLoggedOut: () -> Unit, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val activeWallet by viewModel.activeWallet.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val current = settings ?: return
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionCard(title = stringResource(R.string.wallet), info = stringResource(R.string.wallet_settings_info)) {
            val wallet = activeWallet
            if (wallet != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(wallet.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                        if (wallet is ViewOnlyWallet) ViewOnlyTag()
                    }
                    AddressText(wallet.address)
                }
            } else {
                HintText(stringResource(R.string.no_wallet_selected))
            }
            OutlinedButton(
                onClick = {
                    viewModel.logOut()
                    onLoggedOut()
                },
                enabled = wallet != null,
            ) {
                Icon(painterResource(R.drawable.ic_logout), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.log_out))
            }
        }

        SectionCard(title = stringResource(R.string.rpc_endpoint), info = stringResource(R.string.rpc_endpoint_info)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.active), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Tag(current.activeRpc?.let { it.name ?: stringResource(R.string.custom) } ?: stringResource(R.string.default_label))
            }
            HintText(current.activeRpc?.url ?: BuildConfig.MAINNET_RPC_URL)
            if (current.activeRpc != null) {
                OutlinedButton(onClick = { viewModel.select(null) }) { Text(stringResource(R.string.reset_to_default)) }
            }
        }

        SectionCard(title = stringResource(R.string.saved_rpcs), info = stringResource(R.string.saved_rpcs_info)) {
            if (current.rpcList.isEmpty()) {
                HintText(stringResource(R.string.no_custom_rpcs))
            } else {
                Column {
                    current.rpcList.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        RpcRow(
                            entry = entry,
                            active = current.activeRpc?.id == entry.id,
                            onSelect = { viewModel.select(entry) },
                            onDelete = { viewModel.delete(entry.id) },
                        )
                    }
                }
            }
        }

        SectionCard(title = stringResource(R.string.add_rpc), info = stringResource(R.string.add_rpc_info)) {
            OutlinedTextField(
                value = viewModel.newName,
                onValueChange = { viewModel.newName = it },
                label = { Text(stringResource(R.string.rpc_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.newUrl,
                onValueChange = viewModel::onUrlChange,
                label = { Text(stringResource(R.string.rpc_url)) },
                placeholder = { Text("https://...") },
                isError = viewModel.addError != null,
                supportingText = viewModel.addError?.let { { Text(it) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = viewModel::addRpc) {
                Icon(painterResource(R.drawable.ic_save), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.save))
            }
        }

        SectionCard(title = stringResource(R.string.offline_mode), info = stringResource(R.string.offline_mode_description)) {
            // The whole row toggles, and reads as one switch to screen readers.
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = current.offlineMode, role = Role.Switch, onValueChange = viewModel::setOffline),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (current.offlineMode) R.string.offline_fetching_disabled else R.string.online),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = current.offlineMode, onCheckedChange = null)
            }
            if (current.offlineMode) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.offline_notice), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionCard(title = stringResource(R.string.vpn_relay), info = stringResource(R.string.vpn_relay_description)) {
            HintText(stringResource(R.string.coming_soon))
        }

        SectionCard(title = stringResource(R.string.appearance), info = stringResource(R.string.appearance_info)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = mode == themeMode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                        icon = {},
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        ThemeMode.System -> R.string.theme_system
                                        ThemeMode.Light -> R.string.theme_light
                                        ThemeMode.Dark -> R.string.theme_dark
                                    },
                                ),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** A saved RPC: its name and URL, with buttons to make it active or delete it. */
@Composable
private fun RpcRow(entry: RpcEntry, active: Boolean, onSelect: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    entry.name ?: entry.url,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (active) Tag(stringResource(R.string.active))
            }
            if (entry.name != null) {
                Text(
                    entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }
        }
        IconButton(onClick = onSelect, enabled = !active) {
            Icon(painterResource(R.drawable.ic_check), stringResource(if (active) R.string.currently_active else R.string.set_as_active))
        }
        IconButton(onClick = onDelete) {
            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
        }
    }
}
