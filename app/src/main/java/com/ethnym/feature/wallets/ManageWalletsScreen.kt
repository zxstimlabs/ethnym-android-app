package com.ethnym.feature.wallets

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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethnym.R
import com.ethnym.ui.components.CopyButton
import com.ethnym.ui.components.FormButtons
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.PasswordField
import com.ethnym.ui.components.SubTabs

/** Create, Export, Import and Delete, the web wallet's "Manage" tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWalletsRoute(onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_wallets)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SubTabs(
                tabs = listOf(
                    stringResource(R.string.create),
                    stringResource(R.string.export),
                    stringResource(R.string.import_),
                    stringResource(R.string.delete),
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
                    0 -> CreateWalletForm()
                    1 -> ExportWalletForm()
                    2 -> ImportWalletForm()
                    else -> DeleteWalletForm()
                }
            }
        }
    }
}

@Composable
fun CreateWalletForm(viewModel: CreateWalletViewModel = hiltViewModel()) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HintText(stringResource(R.string.create_wallet_hint))
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
        )
        FormButtons(
            primary = stringResource(R.string.create),
            onPrimary = viewModel::create,
            primaryEnabled = viewModel.canSubmit,
            busy = viewModel.busy,
            onReset = viewModel::reset,
        )
        viewModel.message?.let { HintText(it) }
    }
}

@Composable
fun ImportWalletForm(viewModel: ImportWalletViewModel = hiltViewModel()) {
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::onFilePicked)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        viewModel.message?.let { HintText(it) }

        HintText(stringResource(R.string.import_keystore_file))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
                Text(stringResource(R.string.choose_file))
            }
            Button(onClick = viewModel::importFile, enabled = viewModel.fileKeystores != null) {
                Text(stringResource(R.string.import_))
            }
        }
        viewModel.fileKeystores?.let { keystores ->
            HintText(stringResource(R.string.keystores_in_file, viewModel.fileName ?: "", keystores.size))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        HintText(stringResource(R.string.import_keystore_contents))
        OutlinedTextField(
            value = viewModel.pasted,
            onValueChange = { viewModel.pasted = it },
            placeholder = { Text(stringResource(R.string.paste_keystore_placeholder)) },
            minLines = 3,
            maxLines = 8,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = viewModel::importPasted, enabled = viewModel.pasted.isNotBlank()) {
            Text(stringResource(R.string.import_))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        HintText(stringResource(R.string.import_secret_phrase))
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
            isError = viewModel.phraseError != null,
            supportingText = viewModel.phraseError?.let { { Text(it) } },
            minLines = 3,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        FormButtons(
            primary = stringResource(R.string.import_),
            onPrimary = viewModel::importPhrase,
            primaryEnabled = viewModel.canImportPhrase,
            busy = viewModel.busy,
            onReset = viewModel::resetPhraseForm,
        )
    }
}

@Composable
fun ExportWalletForm(viewModel: ExportWalletViewModel = hiltViewModel()) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    val saveActive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::saveActive)
    }
    val saveAll = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::saveAll)
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        viewModel.message?.let { HintText(it) }

        HintText(stringResource(if (active != null) R.string.download_active_keystore else R.string.select_wallet_to_download))
        Button(onClick = { saveActive.launch(viewModel.activeFileName()) }, enabled = active != null) {
            Text(stringResource(R.string.download))
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        HintText(stringResource(R.string.download_all_keystores))
        Button(onClick = { saveAll.launch(viewModel.allFileName()) }) { Text(stringResource(R.string.download)) }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        HintText(stringResource(if (active != null) R.string.reveal_phrase_hint else R.string.select_wallet_to_reveal))
        PasswordField(value = viewModel.password, onValueChange = { viewModel.password = it })
        FormButtons(
            primary = stringResource(R.string.export),
            onPrimary = viewModel::reveal,
            primaryEnabled = active != null && viewModel.password.isNotEmpty() && !viewModel.busy,
            busy = viewModel.busy,
            onReset = viewModel::reset,
        )
        OutlinedTextField(
            value = viewModel.revealed.orEmpty(),
            onValueChange = {},
            readOnly = true,
            placeholder = { Text(stringResource(R.string.exported_phrase_placeholder)) },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        CopyButton(viewModel.revealed.orEmpty(), sensitive = true)
    }
}

@Composable
fun DeleteWalletForm(viewModel: DeleteWalletViewModel = hiltViewModel()) {
    val active by viewModel.active.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        viewModel.message?.let { HintText(it) }
        HintText(
            if (active != null) stringResource(R.string.delete_wallet_hint, active?.name.orEmpty())
            else stringResource(R.string.select_wallet_to_delete),
        )
        Text(stringResource(R.string.delete_step_password), style = MaterialTheme.typography.titleSmall)
        PasswordField(value = viewModel.password, onValueChange = { viewModel.password = it })
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(stringResource(R.string.delete_step_confirm), style = MaterialTheme.typography.titleSmall)
        FormButtons(
            primary = stringResource(R.string.delete),
            onPrimary = viewModel::delete,
            primaryEnabled = active != null && viewModel.password.isNotEmpty() && !viewModel.busy,
            busy = viewModel.busy,
            onReset = viewModel::reset,
        )
        if (viewModel.wrongPassword) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Text(stringResource(R.string.wrong_password), modifier = Modifier.padding(12.dp))
            }
        }
    }
}
