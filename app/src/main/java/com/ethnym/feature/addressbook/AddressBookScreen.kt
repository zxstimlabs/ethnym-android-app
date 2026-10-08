package com.ethnym.feature.addressbook

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.ethnym.feature.common.userMessage
import com.ethnym.ui.components.AddressInputField
import com.ethnym.ui.components.CopyIconButton
import com.ethnym.ui.components.ErrorText
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionCard
import com.ethnym.ui.components.Tag
import com.ethnym.ui.components.rememberCopyWithFeedback
import com.ethnym.ui.navigation.LocalDismissSheet
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddressBookViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
) : ViewModel() {

    val contacts: StateFlow<List<Contact>?> = contactsRepository.contacts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var query by mutableStateOf("")

    /** Why the last delete failed; the contact stays in the list. */
    var error by mutableStateOf<String?>(null)
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

    fun delete(id: String) {
        viewModelScope.launch {
            error = try {
                contactsRepository.delete(id)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.userMessage()
            }
        }
    }
}

@HiltViewModel
class AddContactViewModel @Inject constructor(
    private val contactsRepository: ContactsRepository,
    client: EthereumClient,
) : ViewModel() {

    private val contacts: StateFlow<List<Contact>> = contactsRepository.contacts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var name by mutableStateOf("")
    val address = AddressFieldState(viewModelScope, client)
    var chain by mutableStateOf("")
    var tags by mutableStateOf("")
    var note by mutableStateOf("")
    var saving by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Set once the contact is saved, for the sheet to close. */
    var added by mutableStateOf(false)
        private set

    val chainError: String?
        get() = chain.trim().takeIf { it.isNotEmpty() && it.toLongOrNull() == null }?.let { "Chain must be a numeric chain ID" }

    fun duplicateError(): String? {
        val resolved = address.resolved ?: return null
        return if (contacts.value.any { it.address.equals(resolved, ignoreCase = true) }) "Address already in address book" else null
    }

    val canAdd: Boolean
        get() = !saving && name.isNotBlank() && address.resolved != null && chainError == null && duplicateError() == null

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
        saving = true
        error = null
        viewModelScope.launch {
            try {
                contactsRepository.add(contact)
                added = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                saving = false
            }
        }
    }

    private companion object {
        const val CONTACT_VERSION = "0.0.1"
    }
}

/**
 * Address Book tab, laid out like the Wallets tab: one card with the title, Edit, the search field
 * and Add, then a row per contact. A tap or long press on a row opens its actions.
 */
@Composable
fun AddressBookTab(onAddContact: () -> Unit, viewModel: AddressBookViewModel = hiltViewModel()) {
    val contacts by viewModel.contacts.collectAsStateWithLifecycle()
    val hasContacts = !contacts.isNullOrEmpty()
    var editing by rememberSaveable { mutableStateOf(false) }
    // Deleting the last contact leaves nothing to edit.
    LaunchedEffect(hasContacts) {
        if (!hasContacts) editing = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionCard(
            title = stringResource(R.string.address_book),
            info = stringResource(R.string.address_book_info),
            accessory = {
                if (hasContacts) {
                    TextButton(onClick = { editing = !editing }) {
                        Text(stringResource(if (editing) R.string.done else R.string.edit))
                    }
                }
            },
            modifier = Modifier.animateContentSize(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContactSearchField(query = viewModel.query, onQueryChange = { viewModel.query = it }, modifier = Modifier.weight(1f))
                FilledIconButton(onClick = onAddContact, shape = RoundedCornerShape(12.dp), modifier = Modifier.size(56.dp)) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.add_contact))
                }
            }

            val all = contacts
            if (all != null) {
                val filtered = viewModel.filter(all)
                when {
                    all.isEmpty() -> HintText(stringResource(R.string.no_contacts_yet))
                    filtered.isEmpty() -> HintText(stringResource(R.string.no_contacts_match, viewModel.query.trim()))
                    else -> Column {
                        filtered.forEachIndexed { index, contact ->
                            if (index > 0) HorizontalDivider()
                            key(contact.id) {
                                ContactRow(contact = contact, editing = editing, onDelete = { viewModel.delete(contact.id) })
                            }
                        }
                    }
                }
            }
            viewModel.error?.let { ErrorText(it) }
        }
    }
}

/** Filters the contacts as you type. */
@Composable
private fun ContactSearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    TextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(stringResource(R.string.search_contacts), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.clear_search))
                }
            }
        } else {
            null
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        // A plain filled box, like the iOS search field, without the underline.
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        modifier = modifier,
    )
}

/**
 * A contact: name and chain, the address with Copy, then the note and tags. In edit mode a delete
 * button slides in on the left.
 */
@Composable
private fun ContactRow(contact: Contact, editing: Boolean, onDelete: () -> Unit) {
    val copy = rememberCopyWithFeedback()
    val uriHandler = LocalUriHandler.current
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(enabled = !editing, onClick = { menuOpen = true }, onLongClick = { menuOpen = true })
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedVisibility(
                visible = editing,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut(),
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.delete_contact, contact.name),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        contact.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    contact.chain?.let { Tag(chainLabel(it)) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(contact.address, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    CopyIconButton(contact.address)
                }
                if (contact.metadata.note.isNotEmpty()) {
                    Text(contact.metadata.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (contact.metadata.tags.isNotEmpty()) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        contact.metadata.tags.forEach { tag ->
                            Text(
                                tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.copy_address)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_content_copy), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    copy(contact.address)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_on_explorer)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_open_in_new), contentDescription = null) },
                onClick = {
                    menuOpen = false
                    uriHandler.openUri(Mainnet.addressUrl(contact.address))
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error),
                onClick = {
                    menuOpen = false
                    onDelete()
                },
            )
        }
    }
}

/**
 * Adds a contact, in a sheet over the Address Book tab: X and Add in the header, as Cancel and Add
 * are on iOS. ENS names are resolved and saved as the address they point to.
 */
@Composable
fun AddContactSheet(onClose: () -> Unit, viewModel: AddContactViewModel = hiltViewModel()) {
    val dismissSheet = LocalDismissSheet.current
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(viewModel.added) {
        if (viewModel.added) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            dismissSheet(onClose)
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { dismissSheet(onClose) }) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.cancel))
        }
        Text(
            stringResource(R.string.add_contact),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp)
                .semantics { heading() },
        )
        TextButton(onClick = viewModel::add, enabled = viewModel.canAdd) {
            Text(stringResource(R.string.add))
        }
    }
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FormGroupHeader(stringResource(R.string.contact))
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

        FormGroupHeader(stringResource(R.string.details))
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
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = viewModel.note,
            onValueChange = { viewModel.note = it },
            label = { Text(stringResource(R.string.note_optional)) },
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        viewModel.error?.let { ErrorText(it) }
    }
}

/** Names a group of fields, like an iOS form section header. */
@Composable
private fun FormGroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

/** The chain name, or "Chain 42161" when it's not one we know. */
private fun chainLabel(chainId: Long): String = when (chainId) {
    Mainnet.CHAIN_ID -> Mainnet.NAME
    137L -> "Polygon"
    8453L -> "Base"
    else -> "Chain $chainId"
}
