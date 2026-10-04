package com.ethnym.feature.send

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethnym.R
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.TransactionJson
import com.ethnym.core.eth.Units
import com.ethnym.feature.balances.TokenRow
import com.ethnym.feature.common.GasPreset
import com.ethnym.feature.common.GasPresetState
import com.ethnym.feature.common.TransactionRunner
import com.ethnym.data.portfolio.KnownCollection
import com.ethnym.data.portfolio.OwnedNft
import com.ethnym.ui.components.AddressInputField
import com.ethnym.ui.components.AddressText
import com.ethnym.ui.components.BusyIndicator
import com.ethnym.ui.components.CopyButton
import com.ethnym.ui.components.DetailRow
import com.ethnym.ui.components.ErrorText
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.InfoIconButton
import com.ethnym.ui.components.PasswordField
import com.ethnym.ui.components.SubTabs
import com.ethnym.ui.components.TransactionStatusPanel
import java.math.BigInteger

/** Send tab: ETH, Token (ERC-20), NFT (ERC-721) and Sign (raw transaction JSON). */
@Composable
fun SendTab() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SubTabs(
            tabs = listOf(
                stringResource(R.string.send_eth),
                stringResource(R.string.token),
                stringResource(R.string.nft),
                stringResource(R.string.sign),
            ),
            selectedIndex = tab,
            onSelect = { tab = it },
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (tab) {
                0 -> SendNativeForm()
                1 -> SendTokenForm()
                2 -> SendNftForm()
                else -> SignTransactionForm()
            }
        }
    }
}

@Composable
private fun SendNativeForm(viewModel: SendNativeViewModel = hiltViewModel()) {
    AmountField(
        amount = viewModel.amount,
        onAmountChange = { viewModel.amount = it },
        error = viewModel.amountError,
        balanceText = viewModel.balance?.let { "${Units.formatEther(it)} ${Mainnet.NATIVE_SYMBOL}" },
        balanceLoading = viewModel.balanceLoading,
        onRefreshBalance = viewModel::refreshBalance,
        onPercent = viewModel::fillPercent,
    )
    RecipientSection(viewModel)
    GasPresetField(viewModel.gas)
    SubmitSection(
        password = viewModel.password,
        onPasswordChange = { viewModel.password = it },
        tx = viewModel.tx,
        onSend = viewModel::send,
        onReset = viewModel::reset,
    )
}

@Composable
private fun SendTokenForm(viewModel: SendTokenViewModel = hiltViewModel()) {
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.token), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = {
            viewModel.loadPicker()
            picking = true
        }) {
            Text(viewModel.info?.symbol ?: stringResource(R.string.select_token))
        }
        AddressInputField(state = viewModel.token, label = stringResource(R.string.token_address), showAddressBook = false)
        when {
            viewModel.infoLoading -> BusyIndicator()
            viewModel.infoError != null -> ErrorText(viewModel.infoError!!)
            viewModel.info != null -> HintText(viewModel.info!!.let { "${it.name} (${it.symbol})" })
        }
    }
    val info = viewModel.info
    AmountField(
        amount = viewModel.amount,
        onAmountChange = { viewModel.amount = it },
        error = viewModel.amountError,
        balanceText = info?.balance?.let { "${Units.format(it, info.decimals)} ${info.symbol}" },
        balanceLoading = viewModel.infoLoading,
        onRefreshBalance = viewModel::refreshInfo,
        onPercent = viewModel::fillPercent,
    )
    RecipientSection(viewModel)
    GasPresetField(viewModel.gas)
    SubmitSection(
        password = viewModel.password,
        onPasswordChange = { viewModel.password = it },
        tx = viewModel.tx,
        onSend = viewModel::send,
        onReset = viewModel::reset,
    )
    if (picking) {
        TokenPickerSheet(
            tokens = viewModel.pickerTokens,
            loading = viewModel.pickerLoading,
            onDismiss = { picking = false },
            onSelect = {
                viewModel.pickToken(it)
                picking = false
            },
        )
    }
}

@Composable
private fun SendNftForm(viewModel: SendNftViewModel = hiltViewModel()) {
    var picking by remember { mutableStateOf(false) }
    val activeWallet by viewModel.activeWallet.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.nft), style = MaterialTheme.typography.titleSmall)
        OutlinedButton(onClick = {
            viewModel.loadPicker()
            picking = true
        }) {
            Text(viewModel.info?.symbol?.let { symbol -> "$symbol #${viewModel.tokenId}" } ?: stringResource(R.string.select_nft))
        }
        AddressInputField(state = viewModel.collection, label = stringResource(R.string.collection_address), showAddressBook = false)
        OutlinedTextField(
            value = viewModel.tokenId,
            onValueChange = { viewModel.tokenId = it },
            label = { Text(stringResource(R.string.token_id)) },
            isError = viewModel.tokenIdError != null,
            supportingText = viewModel.tokenIdError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        val info = viewModel.info
        when {
            viewModel.infoLoading -> BusyIndicator()
            viewModel.infoError != null -> ErrorText(viewModel.infoError!!)
            info?.owner != null -> {
                info.name?.let { HintText("$it (${info.symbol.orEmpty()})") }
                HintText(stringResource(R.string.owned_by))
                AddressText(info.owner)
                if (viewModel.ownsToken == false && activeWallet != null) ErrorText(stringResource(R.string.not_owner))
            }
        }
    }
    RecipientSection(viewModel)
    GasPresetField(viewModel.gas)
    SubmitSection(
        password = viewModel.password,
        onPasswordChange = { viewModel.password = it },
        tx = viewModel.tx,
        onSend = viewModel::send,
        onReset = viewModel::reset,
    )
    if (picking) {
        NftPickerSheet(
            owned = viewModel.pickerOwned,
            collections = viewModel.pickerCollections,
            loading = viewModel.pickerLoading,
            onDismiss = { picking = false },
            onSelect = { contract, id ->
                viewModel.pick(contract, id)
                picking = false
            },
        )
    }
}

@Composable
private fun SignTransactionForm(viewModel: SignTransactionViewModel = hiltViewModel()) {
    val offline by viewModel.offline.collectAsStateWithLifecycle()
    val invalid = viewModel.validation as? TransactionJson.Invalid
    if (offline) HintText(stringResource(R.string.sign_offline_hint))
    OutlinedTextField(
        value = viewModel.json,
        onValueChange = { viewModel.json = it },
        label = { Text(stringResource(R.string.transaction_json)) },
        placeholder = { Text(stringResource(R.string.transaction_json_placeholder)) },
        isError = invalid != null,
        supportingText = invalid?.let { { Text(it.message) } },
        minLines = 5,
        textStyle = MaterialTheme.typography.bodySmall,
        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
        modifier = Modifier.fillMaxWidth(),
    )
    viewModel.parsed?.let { tx ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.transaction_details), style = MaterialTheme.typography.titleSmall)
                HorizontalDivider()
                DetailRow(stringResource(R.string.detail_chain), if (tx.chainId == Mainnet.CHAIN_ID) Mainnet.NAME else "Chain ${tx.chainId}")
                DetailRow(stringResource(R.string.detail_to), tx.to)
                tx.from?.let { DetailRow(stringResource(R.string.detail_from), it) }
                tx.value?.takeIf { it.signum() != 0 }?.let { DetailRow(stringResource(R.string.detail_value), "${Units.formatEther(it)} ETH") }
                tx.type?.let { DetailRow(stringResource(R.string.detail_type), it) }
                tx.gas?.let { DetailRow(stringResource(R.string.detail_gas), it.toString()) }
                tx.nonce?.let { DetailRow(stringResource(R.string.detail_nonce), it.toString()) }
                tx.maxFeePerGas?.let { DetailRow(stringResource(R.string.detail_max_fee), "${Units.formatGwei(it)} gwei") }
                tx.maxPriorityFeePerGas?.let { DetailRow(stringResource(R.string.detail_max_priority_fee), "${Units.formatGwei(it)} gwei") }
                tx.data?.takeIf { it != "0x" }?.let { DetailRow(stringResource(R.string.detail_data), it) }
            }
        }
    }
    SubmitSection(
        password = viewModel.password,
        onPasswordChange = { viewModel.password = it },
        tx = viewModel.tx,
        onSend = viewModel::send,
        onReset = viewModel::reset,
        primaryLabel = stringResource(if (offline) R.string.sign else R.string.send),
        busyOverride = viewModel.signingOffline,
    )
    viewModel.signedOffline?.let { raw ->
        Text(stringResource(R.string.signed_transaction), style = MaterialTheme.typography.titleSmall)
        OutlinedTextField(
            value = raw,
            onValueChange = {},
            readOnly = true,
            textStyle = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        CopyButton(raw)
    }
}

@Composable
private fun RecipientSection(viewModel: SendFormViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.recipient), style = MaterialTheme.typography.titleSmall)
            InfoIconButton(title = stringResource(R.string.recipient), text = stringResource(R.string.recipient_info))
        }
        AddressInputField(state = viewModel.recipient, label = stringResource(R.string.address_or_ens))
    }
}

@Composable
private fun AmountField(
    amount: String,
    onAmountChange: (String) -> Unit,
    error: String?,
    balanceText: String?,
    balanceLoading: Boolean,
    onRefreshBalance: () -> Unit,
    onPercent: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sending), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            listOf(25, 50, 75).forEach { percent ->
                TextButton(onClick = { onPercent(percent) }) { Text("$percent%") }
            }
            TextButton(onClick = { onPercent(100) }) { Text(stringResource(R.string.max)) }
        }
        OutlinedTextField(
            value = amount,
            onValueChange = onAmountChange,
            placeholder = { Text("0") },
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            HintText(stringResource(R.string.balance_value, balanceText ?: "--"), Modifier.weight(1f))
            IconButton(onClick = onRefreshBalance, enabled = !balanceLoading) {
                if (balanceLoading) BusyIndicator() else Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
            }
        }
    }
}

@Composable
private fun GasPresetField(gas: GasPresetState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.gas_preset), style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            GasPreset.entries.forEachIndexed { index, preset ->
                SegmentedButton(
                    selected = gas.preset == preset,
                    onClick = { gas.select(preset) },
                    shape = SegmentedButtonDefaults.itemShape(index, GasPreset.entries.size),
                ) { Text(preset.label) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HintText(stringResource(R.string.gwei_value, gas.gasPriceGwei), Modifier.weight(1f))
            IconButton(onClick = gas::refresh, enabled = !gas.loading) {
                if (gas.loading) BusyIndicator() else Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
            }
        }
        gas.error?.let { ErrorText(it) }
    }
}

@Composable
private fun SubmitSection(
    password: String,
    onPasswordChange: (String) -> Unit,
    tx: TransactionRunner,
    onSend: () -> Unit,
    onReset: () -> Unit,
    primaryLabel: String = stringResource(R.string.send),
    busyOverride: Boolean = false,
) {
    val busy = tx.busy || busyOverride
    HorizontalDivider()
    PasswordField(value = password, onValueChange = onPasswordChange)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onReset, enabled = !busy, modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.reset))
        }
        Button(onClick = onSend, enabled = !busy, modifier = Modifier.weight(2f)) {
            if (busy) BusyIndicator() else Text(primaryLabel)
        }
    }
    TransactionStatusPanel(progress = tx.progress, error = tx.error, onClearError = tx::clearError)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenPickerSheet(
    tokens: List<TokenRow>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    val filtered = tokens.filter { q in it.known.token.symbol.lowercase() || q in it.known.token.name.lowercase() }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PickerSearch(title = stringResource(R.string.select_token), query = query, onQuery = { query = it }, hint = stringResource(R.string.search_tokens))
        if (loading) BusyIndicator(Modifier.padding(16.dp))
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            if (!loading && filtered.isEmpty()) item { HintText(stringResource(R.string.no_tokens_found), Modifier.padding(16.dp)) }
            items(filtered, key = { it.known.token.address }) { row ->
                val token = row.known.token
                ListItem(
                    headlineContent = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(token.symbol)
                            if (row.known.verified) Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.verified), Modifier.size(16.dp))
                        }
                    },
                    supportingContent = { Text(token.name) },
                    trailingContent = row.balance?.takeIf { it.signum() > 0 }?.let { { Text(Units.format(it, token.decimals)) } },
                    modifier = Modifier.clickable { onSelect(token.address) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NftPickerSheet(
    owned: List<OwnedNft>,
    collections: List<KnownCollection>,
    loading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String, BigInteger?) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val q = query.trim().lowercase()
    fun matches(c: KnownCollection) = q in c.collection.name.lowercase() || q in c.collection.symbol.lowercase()
    val ownedFiltered = owned.filter { matches(it.collection) || q in it.tokenId.toString() }
    val collectionsFiltered = collections.filter(::matches)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        PickerSearch(title = stringResource(R.string.select_nft), query = query, onQuery = { query = it }, hint = stringResource(R.string.search_nfts))
        if (loading) BusyIndicator(Modifier.padding(16.dp))
        LazyColumn(Modifier.padding(bottom = 16.dp)) {
            if (!loading && ownedFiltered.isEmpty() && collectionsFiltered.isEmpty()) {
                item { HintText(stringResource(R.string.no_results), Modifier.padding(16.dp)) }
            }
            items(ownedFiltered, key = { "${it.collection.collection.address}-${it.tokenId}" }) { nft ->
                ListItem(
                    headlineContent = { Text("${nft.collection.collection.symbol} ${nft.collection.collection.name}") },
                    trailingContent = { Text("#${nft.tokenId}") },
                    modifier = Modifier.clickable { onSelect(nft.collection.collection.address, nft.tokenId) },
                )
            }
            items(collectionsFiltered, key = { it.collection.address }) { known ->
                ListItem(
                    headlineContent = { Text("${known.collection.symbol} ${known.collection.name}") },
                    supportingContent = { Text(stringResource(R.string.enter_token_id_manually)) },
                    modifier = Modifier.clickable { onSelect(known.collection.address, null) },
                )
            }
        }
    }
}

@Composable
private fun PickerSearch(title: String, query: String, onQuery: (String) -> Unit, hint: String) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            placeholder = { Text(hint) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
