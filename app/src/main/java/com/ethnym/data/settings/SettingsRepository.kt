package com.ethnym.data.settings

import androidx.datastore.core.DataStore
import com.ethnym.core.eth.Mainnet
import com.ethnym.data.model.RpcEntry
import com.ethnym.data.model.WalletSettings
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RPC endpoints and offline mode. Stored encrypted because RPC URLs often embed provider API keys.
 * The shape matches the web wallet's `wallet-settings`, which the backup file carries.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<WalletSettings>,
) {
    val settings: Flow<WalletSettings> = dataStore.data

    suspend fun addRpc(name: String?, url: String) {
        val entry = RpcEntry(id = UUID.randomUUID().toString(), name = name, url = url, chainId = Mainnet.CHAIN_ID)
        dataStore.updateData { it.copy(rpcList = it.rpcList + entry) }
    }

    suspend fun deleteRpc(id: String) {
        dataStore.updateData { settings ->
            settings.copy(
                rpcList = settings.rpcList.filterNot { it.id == id },
                activeRpc = settings.activeRpc.takeUnless { it?.id == id },
            )
        }
    }

    /** null selects the built-in default RPC. */
    suspend fun setActiveRpc(entry: RpcEntry?) {
        dataStore.updateData { it.copy(activeRpc = entry) }
    }

    suspend fun setOfflineMode(offline: Boolean) {
        dataStore.updateData { it.copy(offlineMode = offline) }
    }
}

/** Returns an error message, or null when [url] is a usable http(s) URL. */
fun validateRpcUrl(url: String): String? {
    val trimmed = url.trim()
    if (trimmed.isEmpty()) return "RPC URL is required"
    val uri = runCatching { java.net.URI(trimmed) }.getOrNull()
    if (uri?.scheme == null || uri.host.isNullOrEmpty()) return "Invalid URL"
    if (uri.scheme !in setOf("http", "https")) return "RPC URL must use http or https"
    return null
}
