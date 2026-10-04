package com.ethnym.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ethnym.R
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.Units
import com.ethnym.core.eth.shortHash
import com.ethnym.data.activity.ActivityRepository
import com.ethnym.data.model.ActivityRecord
import com.ethnym.data.model.TxType
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.ui.components.ComingSoon
import com.ethnym.ui.components.DetailRow
import com.ethnym.ui.components.HintText
import com.ethnym.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.math.BigInteger
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

sealed interface ActivityUiState {
    data object Loading : ActivityUiState
    data object NoWallet : ActivityUiState
    data class Ready(val records: List<ActivityRecord>) : ActivityUiState
}

@HiltViewModel
class ActivityViewModel @Inject constructor(
    walletRepository: WalletRepository,
    activityRepository: ActivityRepository,
) : ViewModel() {

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<ActivityUiState> = walletRepository.activeWallet
        .flatMapLatest { wallet ->
            if (wallet == null) flowOf(ActivityUiState.NoWallet)
            else activityRepository.outgoing(wallet.address).map(ActivityUiState::Ready)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState.Loading)
}

/** Activity tab: transactions sent from the active wallet, recorded locally on confirmation. */
@Composable
fun ActivityTab(viewModel: ActivityViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SectionHeader(stringResource(R.string.outgoing)) }
        when (val state = uiState) {
            ActivityUiState.Loading -> item { HintText(stringResource(R.string.loading)) }
            ActivityUiState.NoWallet -> item { HintText(stringResource(R.string.no_active_wallet)) }
            is ActivityUiState.Ready -> {
                if (state.records.isEmpty()) item { HintText(stringResource(R.string.no_activity_yet)) }
                items(state.records, key = { it.id ?: it.txHash }) { record ->
                    ActivityRow(record)
                    HorizontalDivider()
                }
            }
        }
        item { SectionHeader(stringResource(R.string.incoming), Modifier.padding(top = 16.dp)) }
        item { ComingSoon() }
    }
}

@Composable
private fun ActivityRow(record: ActivityRecord) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row {
            TextButton(onClick = { uriHandler.openUri(Mainnet.txUrl(record.txHash)) }) {
                Text("${Mainnet.NAME} · ${shortHash(record.txHash)}")
            }
        }
        HintText(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(record.timestamp)))
        DetailRow(stringResource(R.string.detail_to), record.ensName?.let { "$it (${record.to})" } ?: record.to)
        formatValue(record)?.let { DetailRow(stringResource(R.string.amount), it) }
    }
}

/** Same wording as the web wallet's activity list. */
private fun formatValue(record: ActivityRecord): String? = when (record.type) {
    TxType.Native -> record.nativeValue?.let { "${Units.formatEther(BigInteger(it))} ${Mainnet.NATIVE_SYMBOL}" }
    TxType.Erc20 -> record.tokenValue?.let { "${Units.format(BigInteger(it), record.tokenDecimals ?: 18)} ${record.tokenSymbol.orEmpty()}".trim() }
    TxType.Erc721 -> record.nftId?.let { "Token ID: $it" }
    TxType.Raw -> null
}
