package com.ethnym.feature.wallets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ethnym.R
import com.ethnym.data.model.WalletKeystore
import com.ethnym.feature.balances.BalancesSection
import com.ethnym.ui.components.AddressQrDialog
import com.ethnym.ui.components.AddressText
import com.ethnym.ui.components.CopyIconButton
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionHeader
import com.ethnym.ui.components.SubTabs

/** Wallets tab: pick the active wallet, see its address and balances, manage wallets. */
@Composable
fun WalletsTab(
    onManageWallets: () -> Unit,
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
    onManageWallets: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.wallets.isEmpty()) {
            HintText(stringResource(R.string.no_wallets_found))
            var tab by rememberSaveable { mutableIntStateOf(0) }
            SubTabs(
                tabs = listOf(stringResource(R.string.create), stringResource(R.string.import_)),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            when (tab) {
                0 -> CreateWalletForm()
                else -> ImportWalletForm()
            }
            return@Column
        }

        WalletSelector(wallets = state.wallets, active = state.active, onSelect = onSelect)

        state.active?.let { wallet ->
            var showQr by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AddressText(wallet.address, Modifier.weight(1f))
                CopyIconButton(wallet.address)
                IconButton(onClick = { showQr = true }) {
                    Icon(painterResource(R.drawable.ic_qr_code), contentDescription = stringResource(R.string.qr_code))
                }
            }
            if (showQr) AddressQrDialog(address = wallet.address, onDismiss = { showQr = false })
        }

        if (state.staleCount > 0) MigrationNotice(count = state.staleCount, onMigrate = onMigrate)

        OutlinedButton(onClick = onManageWallets, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.manage_wallets))
        }

        SectionHeader(stringResource(R.string.balances))
        val active = state.active
        if (active == null) {
            HintText(stringResource(R.string.select_wallet_for_balances))
        } else {
            BalancesSection()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletSelector(
    wallets: List<WalletKeystore>,
    active: WalletKeystore?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = active?.name ?: stringResource(R.string.no_wallet_selected),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.wallet)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.no_wallet_selected), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                onClick = {
                    onSelect(null)
                    expanded = false
                },
            )
            HorizontalDivider()
            wallets.forEach { wallet ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(wallet.name)
                            Text(
                                wallet.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    onClick = {
                        onSelect(wallet.id)
                        expanded = false
                    },
                )
            }
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
