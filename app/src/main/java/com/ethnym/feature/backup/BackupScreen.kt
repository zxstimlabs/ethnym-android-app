package com.ethnym.feature.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.R
import com.ethnym.core.crypto.BackupCipher
import com.ethnym.core.crypto.Mnemonics
import com.ethnym.data.AppJson
import com.ethnym.data.backup.BackupRepository
import com.ethnym.data.backup.KeystoreTool
import com.ethnym.data.files.DocumentRepository
import com.ethnym.data.model.WalletKeystore
import com.ethnym.feature.common.userMessage
import com.ethnym.ui.components.ComingSoon
import com.ethnym.ui.components.CopyButton
import com.ethnym.ui.components.DetailRow
import com.ethnym.ui.components.ErrorText
import com.ethnym.ui.components.FormButtons
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.PasswordField
import com.ethnym.ui.components.SectionHeader
import com.ethnym.ui.components.SubTabs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Standalone keystore utility: any phrase to a keystore, any keystore back to a phrase. */
@HiltViewModel
class KeystoreToolViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
) : ViewModel() {
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var phrase by mutableStateOf("")
    var keystore by mutableStateOf<WalletKeystore?>(null)
        private set

    var keystoreInput by mutableStateOf("")
    var decryptPassword by mutableStateOf("")
    var decrypted by mutableStateOf<String?>(null)
        private set

    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val keystoreJson: String? get() = keystore?.let { AppJson.pretty.encodeToString(WalletKeystore.serializer(), it) }

    fun encrypt() {
        if (name.isBlank() || password.isEmpty() || phrase.isBlank()) return
        val normalized = Mnemonics.normalizeInput(phrase)
        if (!Mnemonics.isValid(normalized)) {
            error = "Not a valid secret phrase"
            return
        }
        busy = true
        viewModelScope.launch {
            try {
                keystore = withContext(Dispatchers.Default) { KeystoreTool.encrypt(name.trim(), password, normalized) }
                name = ""
                password = ""
                phrase = ""
                error = null
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun decrypt() {
        if (keystoreInput.isBlank() || decryptPassword.isEmpty()) return
        busy = true
        viewModelScope.launch {
            try {
                decrypted = withContext(Dispatchers.Default) { KeystoreTool.decrypt(keystoreInput, decryptPassword) }
                error = null
            } catch (e: Exception) {
                error = if (e is IllegalArgumentException || e is kotlinx.serialization.SerializationException) {
                    "Not a keystore: ${e.message}"
                } else {
                    e.userMessage()
                }
            } finally {
                busy = false
            }
        }
    }

    fun fileName(): String = "${keystore?.name?.replace(" ", "-") ?: "keystore"}-DO_NOT_DELETE.json"

    fun save(uri: Uri) {
        val json = keystoreJson ?: return
        viewModelScope.launch { runCatching { documentRepository.writeText(uri, json) }.onFailure { error = it.userMessage() } }
    }

    fun resetEncrypt() {
        name = ""
        password = ""
        phrase = ""
        keystore = null
        error = null
    }

    fun resetDecrypt() {
        keystoreInput = ""
        decryptPassword = ""
        decrypted = null
        error = null
    }
}

@HiltViewModel
class LocalBackupViewModel @Inject constructor(
    private val backupRepository: BackupRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    val summary: StateFlow<BackupRepository.Summary?> = backupRepository.summary
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun fileName(): String = backupRepository.backupFileName()

    /** Encrypts and writes the backup to the file the user chose. */
    fun export(uri: Uri) {
        if (password.isEmpty()) return
        busy = true
        viewModelScope.launch {
            message = try {
                documentRepository.writeText(uri, backupRepository.export(password))
                password = ""
                "Backup saved"
            } catch (e: Exception) {
                e.userMessage()
            } finally {
                busy = false
            }
        }
    }
}

/** Backup tab: keystore tool, encrypted local backup, cloud sync. */
@Composable
fun BackupTab() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(R.string.keystore))
        KeystoreSection()
        SectionHeader(stringResource(R.string.local_device_backup), Modifier.padding(top = 16.dp))
        LocalBackupSection()
        SectionHeader(stringResource(R.string.cloud_sync), Modifier.padding(top = 16.dp))
        ComingSoon(stringResource(R.string.cloud_sync_description))
    }
}

@Composable
private fun KeystoreSection(viewModel: KeystoreToolViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::save)
    }
    SubTabs(
        tabs = listOf(stringResource(R.string.encrypt), stringResource(R.string.decrypt)),
        selectedIndex = tab,
        onSelect = { tab = it },
    )
    viewModel.error?.let { ErrorText(it) }
    if (tab == 0) {
        HintText(stringResource(R.string.keystore_encrypt_hint))
        OutlinedTextField(
            value = viewModel.name,
            onValueChange = { viewModel.name = it },
            label = { Text(stringResource(R.string.wallet_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(
            value = viewModel.password,
            onValueChange = { viewModel.password = it },
            label = stringResource(R.string.strong_password),
            imeAction = ImeAction.Next,
        )
        OutlinedTextField(
            value = viewModel.phrase,
            onValueChange = { viewModel.phrase = it },
            label = { Text(stringResource(R.string.secret_phrase)) },
            minLines = 2,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        FormButtons(
            primary = stringResource(R.string.backup),
            onPrimary = viewModel::encrypt,
            primaryEnabled = viewModel.name.isNotBlank() && viewModel.password.isNotEmpty() && viewModel.phrase.isNotBlank() && !viewModel.busy,
            busy = viewModel.busy,
            onReset = viewModel::resetEncrypt,
        )
        val json = viewModel.keystoreJson
        if (json == null) {
            HintText(stringResource(R.string.no_backup_keystore))
        } else {
            HintText(stringResource(R.string.save_keystore_hint))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CopyButton(json)
                OutlinedButton(onClick = { save.launch(viewModel.fileName()) }) { Text(stringResource(R.string.download)) }
            }
            OutlinedTextField(
                value = json,
                onValueChange = {},
                readOnly = true,
                textStyle = MaterialTheme.typography.bodySmall,
                maxLines = 12,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        HintText(stringResource(R.string.keystore_decrypt_hint))
        OutlinedTextField(
            value = viewModel.keystoreInput,
            onValueChange = { viewModel.keystoreInput = it },
            label = { Text(stringResource(R.string.keystore)) },
            minLines = 3,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        PasswordField(value = viewModel.decryptPassword, onValueChange = { viewModel.decryptPassword = it })
        FormButtons(
            primary = stringResource(R.string.decrypt),
            onPrimary = viewModel::decrypt,
            primaryEnabled = viewModel.keystoreInput.isNotBlank() && viewModel.decryptPassword.isNotEmpty() && !viewModel.busy,
            busy = viewModel.busy,
            onReset = viewModel::resetDecrypt,
        )
        val phrase = viewModel.decrypted
        if (phrase == null) {
            HintText(stringResource(R.string.no_decrypted_phrase))
        } else {
            CopyButton(phrase, sensitive = true)
            OutlinedTextField(value = phrase, onValueChange = {}, readOnly = true, minLines = 3, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LocalBackupSection(viewModel: LocalBackupViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    SubTabs(
        tabs = listOf(stringResource(R.string.export), stringResource(R.string.import_)),
        selectedIndex = tab,
        onSelect = { tab = it },
    )
    if (tab == 1) {
        ComingSoon()
        return
    }
    HintText(stringResource(R.string.local_backup_hint, BackupCipher.KDF_ITERATIONS))
    HintText(stringResource(R.string.local_backup_password_warning))
    summary?.let {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DetailRow(stringResource(R.string.wallets), it.wallets.toString())
                DetailRow(stringResource(R.string.contacts), it.contacts.toString())
                DetailRow(stringResource(R.string.activity_records), it.activity.toString())
            }
        }
    }
    PasswordField(value = viewModel.password, onValueChange = { viewModel.password = it }, label = stringResource(R.string.backup_password))
    Button(
        onClick = { save.launch(viewModel.fileName()) },
        enabled = viewModel.password.isNotEmpty() && !viewModel.busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (viewModel.busy) R.string.encrypting else R.string.download_backup))
    }
    viewModel.message?.let { HintText(it) }
}
