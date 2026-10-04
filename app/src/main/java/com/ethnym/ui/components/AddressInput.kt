package com.ethnym.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.R
import com.ethnym.data.contacts.ContactsRepository
import com.ethnym.data.model.Contact
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.feature.common.AddressFieldState
import com.ethnym.feature.common.EnsLookup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Address or ENS input with the web wallet's three actions: resolve ENS, scan a QR code and
 * (optionally) pick from the address book.
 */
@Composable
fun AddressInputField(
    state: AddressFieldState,
    label: String,
    modifier: Modifier = Modifier,
    showAddressBook: Boolean = true,
    extraError: String? = null,
) {
    var scanning by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val error = extraError ?: state.error
    val ensMessage = when (val ens = state.ens) {
        is EnsLookup.Resolved -> ens.address
        EnsLookup.NotFound -> stringResource(R.string.ens_invalid)
        is EnsLookup.Failed -> stringResource(R.string.ens_failed, ens.message)
        else -> null
    }
    val ensIsError = state.ens is EnsLookup.NotFound || state.ens is EnsLookup.Failed

    OutlinedTextField(
        value = state.text,
        onValueChange = state::onTextChange,
        label = { Text(label) },
        placeholder = { Text(stringResource(R.string.address_or_ens_placeholder)) },
        singleLine = true,
        isError = error != null || ensIsError,
        supportingText = (error ?: ensMessage)?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
        trailingIcon = {
            Row {
                IconButton(onClick = state::lookup, enabled = state.isEnsName && state.ens !is EnsLookup.Loading) {
                    if (state.ens is EnsLookup.Loading) {
                        BusyIndicator()
                    } else {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.look_up_ens))
                    }
                }
                IconButton(onClick = { scanning = true }) {
                    Icon(painterResource(R.drawable.ic_qr_code_scanner), contentDescription = stringResource(R.string.scan_qr_code))
                }
                if (showAddressBook) {
                    IconButton(onClick = { picking = true }) {
                        Icon(painterResource(R.drawable.ic_contacts), contentDescription = stringResource(R.string.pick_from_address_book))
                    }
                }
            }
        },
        modifier = modifier.fillMaxWidth(),
    )

    if (scanning) {
        QrScannerDialog(onDismiss = { scanning = false }, onAddress = state::onTextChange)
    }
    if (picking) {
        AddressBookPickerSheet(
            onDismiss = { picking = false },
            onSelect = { address ->
                state.onTextChange(address)
                picking = false
            },
        )
    }
}

@HiltViewModel
class AddressBookPickerViewModel @Inject constructor(
    walletRepository: WalletRepository,
    contactsRepository: ContactsRepository,
) : ViewModel() {

    data class UiState(
        val wallets: List<WalletKeystore> = emptyList(),
        val activeAddress: String? = null,
        val contacts: List<Contact> = emptyList(),
    )

    val uiState: StateFlow<UiState> = combine(
        walletRepository.wallets,
        walletRepository.activeWallet,
        contactsRepository.contacts,
    ) { wallets, active, contacts -> UiState(wallets, active?.address, contacts) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())
}

/** Your own wallets and saved contacts, searchable; tapping one fills the address. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddressBookPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    viewModel: AddressBookPickerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val wallets = uiState.wallets.filter { q in it.name.lowercase() || q in it.address.lowercase() }
    val contacts = uiState.contacts.filter { q in it.name.lowercase() || q in it.address.lowercase() }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.address_book), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_name_or_address)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            if (wallets.isNotEmpty()) {
                item { PickerHeader(stringResource(R.string.my_addresses)) }
                items(wallets, key = { "w-${it.id}-${it.address}" }) { wallet ->
                    ListItem(
                        headlineContent = { Text(wallet.name) },
                        supportingContent = { Text(wallet.address, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = if (wallet.address == uiState.activeAddress) {
                            { Text(stringResource(R.string.current), style = MaterialTheme.typography.labelSmall) }
                        } else {
                            null
                        },
                        modifier = Modifier.clickable { onSelect(wallet.address) },
                    )
                }
            }
            if (contacts.isNotEmpty()) {
                item { PickerHeader(stringResource(R.string.contacts)) }
                items(contacts, key = { "c-${it.id}" }) { contact ->
                    ListItem(
                        headlineContent = { Text(contact.name) },
                        supportingContent = { Text(contact.address, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.clickable { onSelect(contact.address) },
                    )
                }
            }
            if (wallets.isEmpty() && contacts.isEmpty()) {
                item {
                    HintText(
                        stringResource(
                            if (uiState.wallets.isEmpty() && uiState.contacts.isEmpty()) R.string.no_addresses_saved else R.string.no_results,
                        ),
                        Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PickerHeader(text: String) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
