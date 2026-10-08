package com.ethnym.feature.balances

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethnym.R
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.Units
import com.ethnym.ui.components.BusyIndicator
import com.ethnym.ui.components.ErrorText
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionCard
import com.ethnym.ui.components.rememberCopyWithFeedback
import java.math.BigInteger

/** Amounts in the list stop at this many decimals; Copy balance copies them in full. */
private const val LIST_FRACTION_DIGITS = 6

private enum class BalanceKind(@param:StringRes val label: Int) {
    Tokens(R.string.tokens),
    Nfts(R.string.nfts),
}

/** Token and NFT balances for the active wallet. */
@Composable
fun BalancesSection(
    hasWallets: Boolean,
    hasActiveWallet: Boolean,
    viewModel: BalancesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf(BalanceKind.Tokens) }

    SectionCard(
        title = stringResource(R.string.balances),
        info = stringResource(R.string.balances_info),
        accessory = {
            if (hasActiveWallet && !uiState.offline) {
                // One size for both, so the header doesn't jump when loading starts or stops.
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (uiState.loading) {
                        BusyIndicator()
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.refresh))
                        }
                    }
                }
            }
        },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            BalanceKind.entries.forEachIndexed { index, item ->
                SegmentedButton(
                    selected = item == kind,
                    onClick = { kind = item },
                    shape = SegmentedButtonDefaults.itemShape(index, BalanceKind.entries.size),
                    icon = {},
                    label = { Text(stringResource(item.label)) },
                )
            }
        }

        if (!hasActiveWallet) {
            HintText(stringResource(if (hasWallets) R.string.select_wallet_for_balances else R.string.create_wallet_for_balances))
        } else {
            if (uiState.offline) HintText(stringResource(R.string.offline_balances_paused))
            when (kind) {
                BalanceKind.Tokens -> TokenBalances(uiState, viewModel)
                BalanceKind.Nfts -> NftBalances(uiState, viewModel)
            }
        }
    }
}

@Composable
private fun TokenBalances(uiState: BalancesUiState, viewModel: BalancesViewModel) {
    val copy = rememberCopyWithFeedback()
    val tokens = uiState.visibleTokens

    // Ether, always first, as on the web wallet.
    Column {
        val native = uiState.native
        AssetRow(
            symbol = Mainnet.NATIVE_SYMBOL,
            name = Mainnet.NATIVE_NAME,
            verified = true,
            value = amountText(native, Mainnet.NATIVE_DECIMALS, failed = uiState.nativeError != null),
            valueColor = amountColor(native, failed = uiState.nativeError != null),
            actions = listOfNotNull(
                native?.let { RowAction(R.string.copy_balance, R.drawable.ic_content_copy) { copy(Units.formatEther(it)) } },
            ),
        )
        tokens.forEach { row ->
            val token = row.known.token
            HorizontalDivider()
            AssetRow(
                symbol = token.symbol,
                name = token.name,
                verified = row.known.verified,
                value = amountText(row.balance, token.decimals, failed = row.failed),
                valueColor = amountColor(row.balance, failed = row.failed),
                actions = listOfNotNull(
                    RowAction(R.string.copy_contract_address, R.drawable.ic_content_copy) { copy(token.address) },
                    row.balance?.let { RowAction(R.string.copy_balance, R.drawable.ic_content_copy) { copy(Units.format(it, token.decimals)) } },
                    if (row.known.custom) RowAction(R.string.remove, R.drawable.ic_delete, destructive = true) { viewModel.removeToken(token.address) } else null,
                ),
            )
        }
    }
    uiState.nativeError?.let { ErrorText(it) }
    if (tokens.isEmpty() && !uiState.offline) {
        HintText(stringResource(if (uiState.loading) R.string.loading_token_balances else R.string.no_token_balances))
    }
    uiState.tokensError?.let { ErrorText(it) }

    var adding by rememberSaveable { mutableStateOf(false) }
    if (!adding) {
        AddCustomButton(stringResource(R.string.add_custom_token), onClick = { adding = true })
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
    val copy = rememberCopyWithFeedback()

    // Custom collections are listed even when the wallet owns none of their tokens.
    if (uiState.nfts.isNotEmpty() || uiState.emptyCustomCollections.isNotEmpty()) {
        Column {
            uiState.nfts.forEachIndexed { index, nft ->
                val collection = nft.collection.collection
                val tokenId = nft.tokenId.toString()
                if (index > 0) HorizontalDivider()
                AssetRow(
                    symbol = collection.symbol,
                    name = collection.name,
                    verified = nft.collection.verified,
                    value = "#$tokenId",
                    truncateValue = true,
                    actions = listOfNotNull(
                        RowAction(R.string.copy_contract_address, R.drawable.ic_content_copy) { copy(collection.address) },
                        RowAction(R.string.copy_token_id, R.drawable.ic_content_copy) { copy(tokenId) },
                        if (nft.collection.custom) {
                            RowAction(R.string.remove_collection, R.drawable.ic_delete, destructive = true) { viewModel.removeNft(collection.address) }
                        } else {
                            null
                        },
                    ),
                )
            }
            uiState.emptyCustomCollections.forEachIndexed { index, known ->
                val collection = known.collection
                if (index > 0 || uiState.nfts.isNotEmpty()) HorizontalDivider()
                AssetRow(
                    symbol = collection.symbol,
                    name = collection.name,
                    verified = known.verified,
                    value = "—",
                    valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actions = listOf(
                        RowAction(R.string.copy_contract_address, R.drawable.ic_content_copy) { copy(collection.address) },
                        RowAction(R.string.remove_collection, R.drawable.ic_delete, destructive = true) { viewModel.removeNft(collection.address) },
                    ),
                )
            }
        }
    } else if (!uiState.offline) {
        HintText(stringResource(if (uiState.loading) R.string.loading_nfts else R.string.no_nfts_found))
    }
    uiState.nftsError?.let { ErrorText(it) }

    var adding by rememberSaveable { mutableStateOf(false) }
    if (!adding) {
        AddCustomButton(stringResource(R.string.add_custom_nft), onClick = { adding = true })
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
private fun amountText(balance: BigInteger?, decimals: Int, failed: Boolean): String = when {
    balance != null -> Units.format(balance, decimals, LIST_FRACTION_DIGITS)
    failed -> stringResource(R.string.error)
    else -> "—"
}

@Composable
private fun amountColor(balance: BigInteger?, failed: Boolean): Color = when {
    balance != null -> Color.Unspecified
    failed -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** One entry in a row's menu. */
private class RowAction(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Symbol and name on the left, the amount or token ID on the right: one line of the list. A tap or
 * a long press opens its [actions].
 */
@Composable
private fun AssetRow(
    symbol: String,
    name: String,
    verified: Boolean,
    value: String,
    actions: List<RowAction>,
    valueColor: Color = Color.Unspecified,
    truncateValue: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (actions.isEmpty()) {
                        Modifier
                    } else {
                        Modifier.combinedClickable(onClick = { menuOpen = true }, onLongClick = { menuOpen = true })
                    },
                )
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        symbol,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (verified) {
                        Icon(painterResource(R.drawable.ic_verified), stringResource(R.string.verified), Modifier.size(14.dp))
                    }
                }
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = valueColor,
                maxLines = 1,
                // Token IDs can run to 78 digits; amounts are capped at LIST_FRACTION_DIGITS and stay whole.
                overflow = if (truncateValue) TextOverflow.MiddleEllipsis else TextOverflow.Clip,
                modifier = if (truncateValue) Modifier.widthIn(max = 160.dp) else Modifier,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            actions.forEach { action ->
                DropdownMenuItem(
                    text = { Text(stringResource(action.label)) },
                    leadingIcon = { Icon(painterResource(action.icon), contentDescription = null) },
                    colors = if (action.destructive) {
                        MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
                    } else {
                        MenuDefaults.itemColors()
                    },
                    onClick = {
                        menuOpen = false
                        action.onClick()
                    },
                )
            }
        }
    }
}

/** A full-width row, lined up with the balances above it. */
@Composable
private fun AddCustomButton(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
    ) {
        Icon(
            painterResource(R.drawable.ic_add),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
