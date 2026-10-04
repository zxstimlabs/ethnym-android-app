package com.ethnym.feature.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ethnym.core.crypto.WrongPasswordException
import com.ethnym.core.eth.Addresses
import com.ethnym.core.eth.Units
import com.ethnym.data.activity.ActivityRepository
import com.ethnym.data.chain.EthereumClient
import com.ethnym.data.chain.TransactionRequest
import com.ethnym.data.chain.TransactionSender
import com.ethnym.data.model.ActivityRecord
import com.ethnym.data.model.WalletKeystore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.math.BigInteger

// State holders shared by several screens. ViewModels own them and pass their viewModelScope.

sealed interface EnsLookup {
    data object Idle : EnsLookup
    data object Loading : EnsLookup
    data class Resolved(val address: String) : EnsLookup
    data object NotFound : EnsLookup
    data class Failed(val message: String) : EnsLookup
}

/** A text field that takes an address or an ENS name, resolved on demand like the web wallet. */
class AddressFieldState(
    private val scope: CoroutineScope,
    private val client: EthereumClient,
) {
    var text by mutableStateOf("")
        private set
    var ens by mutableStateOf<EnsLookup>(EnsLookup.Idle)
        private set
    var touched by mutableStateOf(false)
        private set

    private var lookupJob: Job? = null

    fun onTextChange(value: String) {
        text = value
        touched = true
        lookupJob?.cancel()
        ens = EnsLookup.Idle
    }

    fun clear() {
        lookupJob?.cancel()
        text = ""
        ens = EnsLookup.Idle
        touched = false
    }

    val isEnsName: Boolean get() = Addresses.isEnsName(text.trim())

    fun lookup() {
        if (!isEnsName) return
        val name = text.trim()
        lookupJob?.cancel()
        ens = EnsLookup.Loading
        lookupJob = scope.launch {
            ens = try {
                client.resolveEns(name)?.let(EnsLookup::Resolved) ?: EnsLookup.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                EnsLookup.Failed(e.userMessage())
            }
        }
    }

    /** The address to use: the typed address, or the resolved ENS address. */
    val resolved: String?
        get() = when {
            isEnsName -> (ens as? EnsLookup.Resolved)?.address
            Addresses.isValid(text.trim()) -> Addresses.checksum(text.trim())
            else -> null
        }

    /** Shown under the field once the user has typed. */
    val error: String?
        get() = when {
            !touched -> null
            text.isBlank() -> "Please enter an address or ENS"
            isEnsName -> null
            !Addresses.isValid(text.trim()) -> "Not a valid address"
            else -> null
        }
}

enum class GasPreset(val label: String, val percent: Long) {
    Slow("Slow", 90),
    Normal("Normal", 100),
    Fast("Fast", 110),
}

/** Slow / Normal / Fast gas price presets around the node's current gas price. */
class GasPresetState(
    private val scope: CoroutineScope,
    private val client: EthereumClient,
) {
    var networkGasPrice by mutableStateOf<BigInteger?>(null)
        private set
    var preset by mutableStateOf(GasPreset.Normal)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun select(value: GasPreset) {
        preset = value
    }

    fun refresh() {
        loading = true
        scope.launch {
            try {
                networkGasPrice = client.gasPrice()
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.userMessage()
            } finally {
                loading = false
            }
        }
    }

    /** The gas price to send with, in wei, or null until the network price is known. */
    val gasPrice: BigInteger?
        get() = networkGasPrice?.let { it * BigInteger.valueOf(preset.percent) / BigInteger.valueOf(100) }

    val gasPriceGwei: String get() = gasPrice?.let(Units::formatGwei) ?: "0"
}

sealed interface TxProgress {
    data object Idle : TxProgress

    /** Decrypting the wallet, filling in nonce/gas/fees and signing. */
    data object Signing : TxProgress

    data class Confirming(val hash: String) : TxProgress

    data class Confirmed(val hash: String, val succeeded: Boolean) : TxProgress
}

/** Runs sign → broadcast → wait for receipt, recording the activity once mined. */
class TransactionRunner(
    private val scope: CoroutineScope,
    private val sender: TransactionSender,
    private val activityRepository: ActivityRepository,
) {
    var progress by mutableStateOf<TxProgress>(TxProgress.Idle)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    val busy: Boolean get() = progress is TxProgress.Signing || progress is TxProgress.Confirming

    private var job: Job? = null

    /** [activity] builds the record to store on confirmation (txHash and timestamp are filled in). */
    fun send(
        wallet: WalletKeystore,
        password: String,
        request: TransactionRequest,
        activity: ActivityRecord?,
    ) {
        if (busy) return
        error = null
        progress = TxProgress.Signing
        job = scope.launch {
            try {
                val signed = sender.sign(wallet, password, request)
                val hash = sender.broadcast(signed)
                progress = TxProgress.Confirming(hash)
                val receipt = sender.awaitReceipt(hash)
                progress = TxProgress.Confirmed(hash, succeeded = receipt.isStatusOK)
                activity?.let { activityRepository.record(it.copy(txHash = hash)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (progress is TxProgress.Signing) progress = TxProgress.Idle
                error = e.userMessage()
            }
        }
    }

    fun showError(message: String) {
        error = message
    }

    fun clearError() {
        error = null
    }

    fun reset() {
        job?.cancel()
        progress = TxProgress.Idle
        error = null
    }
}

/** A message fit to show the user. */
fun Throwable.userMessage(): String = when (this) {
    is WrongPasswordException -> "Wrong password. Please try again."
    else -> message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName
}
