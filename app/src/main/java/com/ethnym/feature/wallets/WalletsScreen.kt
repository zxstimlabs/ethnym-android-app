package com.ethnym.feature.wallets

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethnym.R
import com.ethnym.core.eth.shortHash
import com.ethnym.data.model.WalletKeystore
import com.ethnym.feature.balances.BalancesSection
import com.ethnym.ui.components.AddressQrDialog
import com.ethnym.ui.components.AddressText
import com.ethnym.ui.components.CopyIconButton
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionCard
import com.ethnym.ui.components.Tag

/** Wallets tab: the active wallet and its address, then its balances. */
@Composable
fun WalletsTab(
    onManageWallets: (ManageWalletsTab) -> Unit,
    viewModel: WalletsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        WalletsUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is WalletsUiState.Ready -> WalletsContent(
            state = state,
            onSelect = viewModel::select,
            onMigrate = viewModel::migrate,
            onManageWallets = onManageWallets,
        )
    }
}

@Composable
private fun WalletsContent(
    state: WalletsUiState.Ready,
    onSelect: (String?) -> Unit,
    onMigrate: () -> Unit,
    onManageWallets: (ManageWalletsTab) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WalletSection(state = state, onSelect = onSelect, onManageWallets = onManageWallets)
        if (state.staleCount > 0) MigrationNotice(count = state.staleCount, onMigrate = onMigrate)
        BalancesSection(hasWallets = state.wallets.isNotEmpty(), hasActiveWallet = state.active != null)
    }
}

/**
 * The wallet picker, the active address, and Receive / Manage. Balances are in their own section.
 * Until the first wallet exists, a prompt and Create / Import take the same rows, so adding one
 * barely moves the layout.
 */
@Composable
private fun WalletSection(
    state: WalletsUiState.Ready,
    onSelect: (String?) -> Unit,
    onManageWallets: (ManageWalletsTab) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.wallets),
        info = stringResource(R.string.wallets_info),
        accessory = { if (state.offline) Tag(stringResource(R.string.offline)) },
    ) {
        if (state.wallets.isEmpty()) {
            Text(
                stringResource(R.string.create_or_import_wallet),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            WalletPicker(wallets = state.wallets, active = state.active, onSelect = onSelect)
        }

        val active = state.active
        if (active != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddressText(active.address, Modifier.weight(1f))
                CopyIconButton(active.address)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val buttonModifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            if (state.wallets.isEmpty()) {
                Button(onClick = { onManageWallets(ManageWalletsTab.Create) }, modifier = buttonModifier) {
                    ActionLabel(stringResource(R.string.create), R.drawable.ic_add)
                }
                FilledTonalButton(onClick = { onManageWallets(ManageWalletsTab.Import) }, modifier = buttonModifier) {
                    ActionLabel(stringResource(R.string.import_), R.drawable.ic_download)
                }
            } else {
                // Manage stays with no wallet selected, to create or import one.
                var showQr by rememberSaveable { mutableStateOf(false) }
                Button(onClick = { showQr = true }, enabled = active != null, modifier = buttonModifier) {
                    ActionLabel(stringResource(R.string.receive), R.drawable.ic_qr_code)
                }
                ManageButton(canDelete = active != null, onManageWallets = onManageWallets, modifier = buttonModifier)
                if (showQr && active != null) AddressQrDialog(address = active.address, onDismiss = { showQr = false })
            }
        }
    }
}

@Composable
private fun RowScope.ActionLabel(text: String, @DrawableRes icon: Int) {
    Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
    Text(text, maxLines = 1)
}

/** Create, import, export and delete, in a menu, as under the web wallet's Manage. Each opens that tab. */
@Composable
private fun ManageButton(
    canDelete: Boolean,
    onManageWallets: (ManageWalletsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }

    @Composable
    fun Item(text: Int, icon: Int, tab: ManageWalletsTab, enabled: Boolean = true, destructive: Boolean = false) {
        DropdownMenuItem(
            text = { Text(stringResource(text)) },
            leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
            enabled = enabled,
            colors = if (destructive) {
                MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error, leadingIconColor = MaterialTheme.colorScheme.error)
            } else {
                MenuDefaults.itemColors()
            },
            onClick = {
                open = false
                onManageWallets(tab)
            },
        )
    }

    // Passes the row's width and minimum height on to the button, to match Receive beside it.
    Box(modifier, propagateMinConstraints = true) {
        FilledTonalButton(onClick = { open = true }) {
            ActionLabel(stringResource(R.string.manage), R.drawable.ic_more_horiz)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            Item(R.string.create_wallet, R.drawable.ic_add, ManageWalletsTab.Create)
            Item(R.string.import_wallet, R.drawable.ic_download, ManageWalletsTab.Import)
            Item(R.string.export, R.drawable.ic_upload, ManageWalletsTab.Export)
            HorizontalDivider()
            Item(R.string.delete_wallet, R.drawable.ic_delete, ManageWalletsTab.Delete, enabled = canDelete, destructive = true)
        }
    }
}

/** The active wallet's name; tapping it lists the wallets to switch to. */
@Composable
private fun WalletPicker(
    wallets: List<WalletKeystore>,
    active: WalletKeystore?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = stringResource(R.string.switch_wallet), role = Role.DropdownList) { expanded = true }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                active?.name ?: stringResource(R.string.select_a_wallet),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = if (active == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                painterResource(R.drawable.ic_unfold_more),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            wallets.forEach { wallet ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(wallet.name)
                            Text(
                                shortHash(wallet.address),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = if (wallet.id == active?.id) {
                        { Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.active)) }
                    } else {
                        null
                    },
                    onClick = {
                        onSelect(wallet.id)
                        expanded = false
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_wallet_selected), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingIcon = if (active == null) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = stringResource(R.string.active)) }
                } else {
                    null
                },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun MigrationNotice(count: Int, onMigrate: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_warning), contentDescription = null)
                Text(pluralStringResource(R.plurals.migration_notice, count, count), style = MaterialTheme.typography.bodyMedium)
            }
            HintText(stringResource(R.string.migration_explainer, WalletKeystore.CURRENT_UM_VERSION))
            OutlinedButton(onClick = onMigrate) { Text(pluralStringResource(R.plurals.migrate_wallets, count, count)) }
        }
    }
}
