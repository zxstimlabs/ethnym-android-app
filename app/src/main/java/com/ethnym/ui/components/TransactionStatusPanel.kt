package com.ethnym.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ethnym.R
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.shortHash
import com.ethnym.feature.common.TxProgress

/** Signature and transaction status, plus any error, as in the web wallet's send forms. */
@Composable
fun TransactionStatusPanel(
    progress: TxProgress,
    error: String?,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(stringResource(R.string.error), style = MaterialTheme.typography.titleSmall)
                        Text(error, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = onClearError) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.dismiss))
                    }
                }
            }
        }
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (progress) {
                    TxProgress.Idle -> StatusLine(done = false, text = stringResource(R.string.status_nothing_to_sign), muted = true)
                    TxProgress.Signing -> StatusLine(busy = true, text = stringResource(R.string.status_pending_signature))
                    else -> StatusLine(done = true, text = stringResource(R.string.status_signed))
                }
                when (progress) {
                    is TxProgress.Confirming -> {
                        StatusLine(busy = true, text = stringResource(R.string.status_confirming))
                        TxHashLink(progress.hash)
                    }
                    is TxProgress.Confirmed -> {
                        StatusLine(
                            done = true,
                            text = stringResource(if (progress.succeeded) R.string.status_confirmed else R.string.status_reverted),
                        )
                        TxHashLink(progress.hash)
                    }
                    else -> StatusLine(done = false, text = stringResource(R.string.status_no_transaction), muted = true)
                }
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, done: Boolean = false, busy: Boolean = false, muted: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            busy -> BusyIndicator()
            done -> Icon(painterResource(R.drawable.ic_check), contentDescription = null, modifier = Modifier.size(20.dp))
            else -> Icon(painterResource(R.drawable.ic_radio_button_unchecked), contentDescription = null, modifier = Modifier.size(20.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TxHashLink(hash: String) {
    val uriHandler = LocalUriHandler.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { uriHandler.openUri(Mainnet.txUrl(hash)) }) {
            Text(shortHash(hash))
            Icon(
                painterResource(R.drawable.ic_open_in_new),
                contentDescription = stringResource(R.string.view_on_explorer),
                modifier = Modifier.padding(start = 4.dp).size(16.dp),
            )
        }
        CopyIconButton(hash)
    }
}
