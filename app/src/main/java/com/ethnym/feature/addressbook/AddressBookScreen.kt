package com.ethnym.feature.addressbook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.R
import com.ethnym.core.eth.Mainnet
import com.ethnym.data.chain.EthereumClient
import com.ethnym.data.contacts.ContactsRepository
import com.ethnym.data.model.Contact
import com.ethnym.feature.common.AddressFieldState
import com.ethnym.ui.components.AddressInputField
import com.ethnym.ui.components.AddressText
import com.ethnym.ui.components.ComingSoon
import com.ethnym.ui.components.CopyButton
import com.ethnym.ui.components.FormButtons
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SubTabs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
    client: EthereumClient,
) : ViewModel() {

    val contacts: StateFlow<List<Contact>?> = contactsRepository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var query by mutableStateOf("")

    var name by mutableStateOf("")
    val address = AddressFieldState(viewModelScope, client)
    var chain by mutableStateOf("")
    var tags by mutableStateOf("")
    var note by mutableStateOf("")
    var message by mutableStateOf<String?>(null)
        private set

    /** Search by name, address, tag or note. */
    fun filter(contacts: List<Contact>): List<Contact> {
        val q = query.trim().lowercase()
        return contacts.filter { contact ->
            q in contact.name.lowercase() ||
                q in contact.address.lowercase() ||
                contact.metadata.tags.any { q in it.lowercase() } ||
                q in contact.metadata.note.lowercase()
        }
    }

    val chainError: String?
        get() = chain.trim().takeIf { it.isNotEmpty() && it.toLongOrNull() == null }?.let { "Chain must be a numeric chain ID" }

    fun duplicateError(): String? {
        val resolved = address.resolved ?: return null
        return if (contacts.value.orEmpty().any { it.address.equals(resolved, ignoreCase = true) }) "Address already in address book" else null
    }

    val canAdd: Boolean get() = name.isNotBlank() && address.resolved != null && chainError == null && duplicateError() == null

    fun add() {
        val resolved = address.resolved ?: return
        if (!canAdd) return
        val contact = Contact(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            address = resolved,
            chain = chain.trim().toLongOrNull(),
            metadata = Contact.Metadata(
                tags = tags.split(",").map(String::trim).filter(String::isNotEmpty),
                version = CONTACT_VERSION,
                note = note.trim(),
            ),
        )
        viewModelScope.launch {
            contactsRepository.add(contact)
            resetForm()
            message = "Added ${contact.name}"
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { contactsRepository.delete(id) }
    }

    fun resetForm() {
        name = ""
        address.clear()
        chain = ""
        tags = ""
        note = ""
        message = null
    }

    private companion object {
        const val CONTACT_VERSION = "0.0.1"
    }
}

/** Address Book tab: saved contacts, and a form to add one. */
@Composable
fun AddressBookTab(viewModel: AddressBookViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(
            tabs = listOf(
                stringResource(R.string.addresses),
                stringResource(R.string.create),
                stringResource(R.string.import_),
                stringResource(R.string.export),
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> ContactList(viewModel)
                1 -> AddContactForm(viewModel)
                else -> ComingSoon()
            }
        }
    }
}

@Composable
private fun ContactList(viewModel: AddressBookViewModel) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    OutlinedTextField(
        value = viewModel.query,
        onValueChange = { viewModel.query = it },
        placeholder = { Text(stringResource(R.string.search_contacts)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val all = contacts ?: return
    val filtered = viewModel.filter(all)
    if (filtered.isEmpty()) {
        HintText(stringResource(if (all.isEmpty()) R.string.no_contacts_yet else R.string.no_contacts_match))
    }
    filtered.forEach { contact -> ContactCard(contact, onDelete = { viewModel.delete(contact.id) }) }
}

@Composable
private fun ContactCard(contact: Contact, onDelete: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(contact.name, style = MaterialTheme.typography.titleSmall)
            AddressText(contact.address)
            contact.chain?.let { HintText(chainName(it)) }
            if (contact.metadata.note.isNotEmpty()) HintText(contact.metadata.note)
            if (contact.metadata.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    contact.metadata.tags.forEach { tag -> AssistChip(onClick = {}, label = { Text(tag) }) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CopyButton(contact.address)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete)) }
            }
        }
    }
}

@Composable
private fun AddContactForm(viewModel: AddressBookViewModel) {
    viewModel.message?.let { HintText(it) }
    OutlinedTextField(
        value = viewModel.name,
        onValueChange = { viewModel.name = it },
        label = { Text(stringResource(R.string.name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    AddressInputField(
        state = viewModel.address,
        label = stringResource(R.string.address_or_ens),
        showAddressBook = false,
        extraError = viewModel.duplicateError(),
    )
    OutlinedTextField(
        value = viewModel.chain,
        onValueChange = { viewModel.chain = it },
        label = { Text(stringResource(R.string.chain_id_optional)) },
        isError = viewModel.chainError != null,
        supportingText = { Text(viewModel.chainError ?: stringResource(R.string.chain_id_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.tags,
        onValueChange = { viewModel.tags = it },
        label = { Text(stringResource(R.string.tags)) },
        placeholder = { Text(stringResource(R.string.tags_placeholder)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = viewModel.note,
        onValueChange = { viewModel.note = it },
        label = { Text(stringResource(R.string.note_optional)) },
        modifier = Modifier.fillMaxWidth(),
    )
    FormButtons(
        primary = stringResource(R.string.add),
        onPrimary = viewModel::add,
        primaryEnabled = viewModel.canAdd,
        busy = false,
        onReset = viewModel::resetForm,
    )
}

private fun chainName(chainId: Long): String = when (chainId) {
    Mainnet.CHAIN_ID -> Mainnet.NAME
    137L -> "Polygon"
    8453L -> "Base"
    else -> "Chain $chainId"
}
