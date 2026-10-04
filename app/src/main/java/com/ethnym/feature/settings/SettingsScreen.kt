package com.ethnym.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.BuildConfig
import com.ethnym.R
import com.ethnym.data.model.RpcEntry
import com.ethnym.data.model.WalletSettings
import com.ethnym.data.settings.SettingsRepository
import com.ethnym.data.settings.validateRpcUrl
import com.ethnym.ui.components.ComingSoon
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val settings: StateFlow<WalletSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
}

/** Settings tab: RPC endpoint, offline mode and the upcoming VPN relay. */
@Composable
fun SettingsTab(viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val current = settings ?: return
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(R.string.rpc_endpoint))
        Text(stringResource(R.string.active), style = MaterialTheme.typography.labelLarge)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                current.activeRpc?.let { it.name ?: stringResource(R.string.custom) } ?: stringResource(R.string.default_label),
                style = MaterialTheme.typography.labelMedium,
            )
            HintText(current.activeRpc?.url ?: BuildConfig.MAINNET_RPC_URL, Modifier.weight(1f))
        }
        if (current.activeRpc != null) {
            OutlinedButton(onClick = { viewModel.select(null) }) { Text(stringResource(R.string.reset_to_default)) }
        }

        SectionHeader(stringResource(R.string.saved_rpcs), Modifier.padding(top = 8.dp))
        if (current.rpcList.isEmpty()) HintText(stringResource(R.string.no_custom_rpcs))
        current.rpcList.forEach { entry ->
            val active = current.activeRpc?.id == entry.id
            OutlinedCard(Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(entry.name ?: entry.url, maxLines = 1) },
                    supportingContent = entry.name?.let { { Text(entry.url, style = MaterialTheme.typography.bodySmall, maxLines = 1) } },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.select(entry) }, enabled = !active) {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    stringResource(if (active) R.string.currently_active else R.string.set_as_active),
                                )
                            }
                            IconButton(onClick = { viewModel.delete(entry.id) }) {
                                Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
                            }
                        }
                    },
                    overlineContent = if (active) ({ Text(stringResource(R.string.active)) }) else null,
                )
            }
        }

        SectionHeader(stringResource(R.string.add_rpc), Modifier.padding(top = 8.dp))
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
            Icon(painterResource(R.drawable.ic_save), contentDescription = null)
            Text(stringResource(R.string.save), Modifier.padding(start = 8.dp))
        }

        SectionHeader(stringResource(R.string.offline_mode), Modifier.padding(top = 8.dp))
        HintText(stringResource(R.string.offline_mode_description))
        ListItem(
            headlineContent = {
                Text(stringResource(if (current.offlineMode) R.string.offline_fetching_disabled else R.string.online))
            },
            trailingContent = { Switch(checked = current.offlineMode, onCheckedChange = viewModel::setOffline) },
        )
        if (current.offlineMode) {
            OutlinedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
                    Text(stringResource(R.string.offline_notice), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        SectionHeader(stringResource(R.string.vpn_relay), Modifier.padding(top = 8.dp))
        ComingSoon(stringResource(R.string.vpn_relay_description))
    }
}
