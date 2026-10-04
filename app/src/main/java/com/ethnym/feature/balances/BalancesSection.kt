package com.ethnym.feature.balances

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethnym.R
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.Units
import com.ethnym.ui.components.BusyIndicator
import com.ethnym.ui.components.CopyIconButton
import com.ethnym.ui.components.ErrorText
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SubTabs

/** Balances for the active wallet: ETH and ERC-20 tokens, and ERC-721 NFTs. */
@Composable
fun BalancesSection(viewModel: BalancesViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SubTabs(
                tabs = listOf(stringResource(R.string.token), stringResource(R.string.nft)),
                selectedIndex = tab,
                onSelect = { tab = it },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::refresh, enabled = !uiState.loading && !uiState.offline) {
                if (uiState.loading) BusyIndicator() else Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
            }
        }
        if (uiState.offline) HintText(stringResource(R.string.offline_balances_paused))
        if (tab == 0) TokenBalances(uiState, viewModel) else NftBalances(uiState, viewModel)
    }
}

@Composable
private fun TokenBalances(uiState: BalancesUiState, viewModel: BalancesViewModel) {
    ListItem(
        headlineContent = { Text("${Mainnet.NATIVE_NAME} ${Mainnet.NATIVE_SYMBOL}") },
        trailingContent = {
            when {
                uiState.nativeError != null -> Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
                uiState.native != null -> Text(Units.formatEther(uiState.native))
                uiState.loading -> BusyIndicator()
                else -> Text("--")
            }
        },
    )
    uiState.nativeError?.let { ErrorText(it) }
    uiState.tokensError?.let { ErrorText(it) }

    val tokens = uiState.visibleTokens
    if (tokens.isNotEmpty()) HorizontalDivider()
    tokens.forEach { row ->
        val token = row.known.token
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${token.name} ${token.symbol}")
                    if (row.known.verified) {
                        Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.verified), Modifier.size(16.dp))
                    }
                }
            },
            supportingContent = { Text(token.address, style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when {
                        row.failed -> Text(stringResource(R.string.error), color = MaterialTheme.colorScheme.error)
                        row.balance != null -> Text(Units.format(row.balance, token.decimals))
                        uiState.loading -> BusyIndicator()
                        else -> Text("--")
                    }
                    CopyIconButton(token.address)
                    if (!row.known.verified) RemoveMenu(onRemove = { viewModel.removeToken(token.address) })
                }
            },
        )
    }

    HorizontalDivider()
    var adding by rememberSaveable { mutableStateOf(false) }
    if (!adding) {
        TextButton(onClick = { adding = true }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.add_custom_token))
        }
    } else {
        AddByAddress(
            title = stringResource(R.string.add_custom_token),
            placeholder = stringResource(R.string.token_address_placeholder),
            input = viewModel.tokenInput,
            onInput = viewModel::onTokenInput,
            lookup = viewModel.tokenLookup,
            onLookUp = viewModel::lookUpToken,
            describe = { "${it.name} ${it.symbol} · ${stringResource(R.string.decimals_count, it.decimals)}" },
            onAdd = {
                viewModel.addToken(it)
                adding = false
            },
            onCancel = {
                viewModel.onTokenInput("")
                adding = false
            },
        )
    }
}

@Composable
private fun NftBalances(uiState: BalancesUiState, viewModel: BalancesViewModel) {
    uiState.nftsError?.let { ErrorText(it) }
    if (uiState.loading && uiState.nfts.isEmpty()) {
        BusyIndicator()
    } else if (uiState.nfts.isEmpty() && uiState.emptyCustomCollections.isEmpty()) {
        HintText(stringResource(R.string.no_nfts_found))
    }
    uiState.nfts.forEach { nft ->
        val collection = nft.collection.collection
        ListItem(
            headlineContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${collection.name} ${collection.symbol}")
                    if (nft.collection.verified) {
                        Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.verified), Modifier.size(16.dp))
                    }
                }
            },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#${nft.tokenId}")
                    if (!nft.collection.verified) RemoveMenu(onRemove = { viewModel.removeNft(collection.address) })
                }
            },
        )
    }
    uiState.emptyCustomCollections.forEach { known ->
        ListItem(
            headlineContent = { Text("${known.collection.name} ${known.collection.symbol}") },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("--")
                    RemoveMenu(onRemove = { viewModel.removeNft(known.collection.address) })
                }
            },
        )
    }

    HorizontalDivider()
    var adding by rememberSaveable { mutableStateOf(false) }
    if (!adding) {
        TextButton(onClick = { adding = true }) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = null)
            Text(stringResource(R.string.add_custom_nft))
        }
    } else {
        AddByAddress(
            title = stringResource(R.string.add_custom_nft),
            placeholder = stringResource(R.string.collection_address_placeholder),
            input = viewModel.nftInput,
            onInput = viewModel::onNftInput,
            lookup = viewModel.nftLookup,
            onLookUp = viewModel::lookUpNft,
            describe = { "${it.name} ${it.symbol}" },
            onAdd = {
                viewModel.addNft(it)
                adding = false
            },
            onCancel = {
                viewModel.onNftInput("")
                adding = false
            },
        )
    }
}

@Composable
private fun <T> AddByAddress(
    title: String,
    placeholder: String,
    input: String,
    onInput: (String) -> Unit,
    lookup: Lookup<T>,
    onLookUp: () -> Unit,
    describe: @Composable (T) -> String,
    onAdd: (T) -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = onCancel) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.cancel)) }
        }
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = onLookUp, enabled = lookup !is Lookup.Loading && input.isNotBlank()) {
            if (lookup is Lookup.Loading) BusyIndicator() else Text(stringResource(R.string.look_up))
        }
        when (lookup) {
            is Lookup.Found -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(describe(lookup.value), modifier = Modifier.weight(1f))
                Button(onClick = { onAdd(lookup.value) }) { Text(stringResource(R.string.add)) }
            }
            is Lookup.Failed -> ErrorText(lookup.message)
            else -> Unit
        }
    }
}

@Composable
private fun RemoveMenu(onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        Icon(painterResource(R.drawable.ic_more_vert), stringResource(R.string.more_options))
    }
    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.remove)) },
            leadingIcon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
            onClick = {
                open = false
                onRemove()
            },
        )
    }
}
