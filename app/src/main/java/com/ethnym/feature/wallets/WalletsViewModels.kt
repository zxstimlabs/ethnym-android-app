package com.ethnym.feature.wallets

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethnym.core.crypto.Mnemonics
import com.ethnym.core.crypto.WrongPasswordException
import com.ethnym.core.eth.shortHash
import com.ethnym.data.AppJson
import com.ethnym.data.chain.EthereumClient
import com.ethnym.data.files.DocumentRepository
import com.ethnym.data.model.ViewOnlyWallet
import com.ethnym.data.model.Wallet
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.model.needsMigration
import com.ethnym.data.settings.SettingsRepository
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.feature.common.AddressFieldState
import com.ethnym.feature.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Inject

sealed interface WalletsUiState {
    data object Loading : WalletsUiState

    data class Ready(
        val wallets: List<Wallet>,
        val active: Wallet?,
        val offline: Boolean,
    ) : WalletsUiState {
        val staleCount: Int get() = wallets.count { it is WalletKeystore && it.needsMigration() }
    }
}

@HiltViewModel
class WalletsViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<WalletsUiState> = combine(
        walletRepository.vault,
        settingsRepository.settings.map { it.offlineMode }.distinctUntilChanged(),
    ) { vault, offline -> WalletsUiState.Ready(vault.allWallets, vault.active, offline) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletsUiState.Loading)

    fun select(id: String?) {
        viewModelScope.launch { walletRepository.setActive(id) }
    }

    fun migrate() {
        viewModelScope.launch { walletRepository.migrate() }
    }
}

@HiltViewModel
class CreateWalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
) : ViewModel() {
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    val canSubmit: Boolean get() = name.isNotBlank() && password.isNotEmpty() && !busy

    fun create() {
        if (!canSubmit) return
        busy = true
        viewModelScope.launch {
            message = try {
                val wallet = walletRepository.create(name.trim(), password)
                reset()
                "Created ${wallet.name}"
            } catch (e: Exception) {
                e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun reset() {
        name = ""
        password = ""
        message = null
    }
}

@HiltViewModel
class ImportWalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val documentRepository: DocumentRepository,
    client: EthereumClient,
) : ViewModel() {
    private val wallets: StateFlow<List<Wallet>> = walletRepository.vault.map { it.allWallets }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var fileKeystores by mutableStateOf<List<WalletKeystore>?>(null)
        private set
    var fileName by mutableStateOf<String?>(null)
        private set
    var pasted by mutableStateOf("")
    var name by mutableStateOf("")
    var password by mutableStateOf("")
    var phrase by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var viewOnlyName by mutableStateOf("")
    val viewOnlyAddress = AddressFieldState(viewModelScope, client)
    var addingViewOnly by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Set when one of the wallets already has the address. */
    val viewOnlyDuplicateError: String?
        get() {
            val resolved = viewOnlyAddress.resolved ?: return null
            return wallets.value.find { it.address.equals(resolved, ignoreCase = true) }?.let { "Already added as ${it.name}" }
        }

    val canAddViewOnly: Boolean get() = viewOnlyAddress.resolved != null && viewOnlyDuplicateError == null && !addingViewOnly

    val canImportPhrase: Boolean get() = name.isNotBlank() && password.isNotEmpty() && phrase.isNotBlank() && !busy

    /** Shown once the phrase looks complete but fails the BIP-39 checksum or wordlist. */
    val phraseError: String?
        get() {
            val words = Mnemonics.normalizeInput(phrase).split(" ").filter { it.isNotEmpty() }
            return if (words.size >= 12 && !Mnemonics.isValid(Mnemonics.normalizeInput(phrase))) "Not a valid secret phrase" else null
        }

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            try {
                fileKeystores = AppJson.parseWallets(documentRepository.readText(uri))
                fileName = documentRepository.displayName(uri)
                message = null
            } catch (e: Exception) {
                fileKeystores = null
                fileName = null
                message = keystoreParseError(e)
            }
        }
    }

    fun importFile() {
        val keystores = fileKeystores ?: return
        viewModelScope.launch {
            message = importMessage(walletRepository.importKeystores(keystores))
            fileKeystores = null
            fileName = null
        }
    }

    fun importPasted() {
        viewModelScope.launch {
            message = try {
                importMessage(walletRepository.importKeystores(AppJson.parseWallets(pasted))).also { pasted = "" }
            } catch (e: Exception) {
                keystoreParseError(e)
            }
        }
    }

    fun importPhrase() {
        if (!canImportPhrase) return
        busy = true
        viewModelScope.launch {
            message = try {
                val wallet = walletRepository.importPhrase(name.trim(), password, phrase)
                resetPhraseForm()
                "Imported ${wallet.name}"
            } catch (e: Exception) {
                e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun resetPhraseForm() {
        name = ""
        password = ""
        phrase = ""
    }

    /** Without a name, the wallet is named after the ENS name typed, else the short address. */
    fun addViewOnly() {
        val address = viewOnlyAddress.resolved ?: return
        if (!canAddViewOnly) return
        val name = viewOnlyName.trim().ifEmpty { viewOnlyAddress.text.trim().takeIf { viewOnlyAddress.isEnsName } ?: shortHash(address) }
        addingViewOnly = true
        viewModelScope.launch {
            message = try {
                val wallet = walletRepository.addViewOnly(name, address)
                resetViewOnlyForm()
                "Imported ${wallet.name} as view-only"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.userMessage()
            } finally {
                addingViewOnly = false
            }
        }
    }

    fun resetViewOnlyForm() {
        viewOnlyName = ""
        viewOnlyAddress.clear()
    }

    private fun importMessage(result: WalletRepository.ImportResult): String =
        buildString {
            append("Imported ${result.imported} wallet${if (result.imported == 1) "" else "s"}")
            if (result.skipped > 0) append(", skipped ${result.skipped} already added")
        }

    private fun keystoreParseError(e: Exception): String = when (e) {
        is SerializationException, is IllegalArgumentException -> "Not a wallet keystore file"
        else -> e.userMessage()
    }
}

@HiltViewModel
class ExportWalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    /** View-only wallets have nothing to export; the form says so. */
    val active: StateFlow<Wallet?> = walletRepository.activeWallet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var password by mutableStateOf("")
    var revealed by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun activeFileName(): String = "${active.value?.name?.replace(" ", "-") ?: "wallet"}-DO_NOT_DELETE.json"

    fun allFileName(): String = "ethnym-wallets-DO_NOT_DELETE.json"

    fun saveActive(uri: Uri) {
        val wallet = active.value as? WalletKeystore ?: return
        viewModelScope.launch {
            message = runCatching {
                documentRepository.writeText(uri, AppJson.pretty.encodeToString(WalletKeystore.serializer(), wallet))
            }.fold({ "Saved keystore for ${wallet.name}" }, { it.userMessage() })
        }
    }

    fun saveAll(uri: Uri) {
        viewModelScope.launch {
            message = runCatching {
                val wallets = walletRepository.wallets.first()
                documentRepository.writeText(uri, AppJson.pretty.encodeToString(ListSerializer(WalletKeystore.serializer()), wallets))
                wallets.size
            }.fold({ "Saved $it keystore${if (it == 1) "" else "s"}" }, { it.userMessage() })
        }
    }

    fun reveal() {
        val wallet = active.value as? WalletKeystore ?: return
        if (password.isEmpty()) return
        busy = true
        viewModelScope.launch {
            try {
                revealed = walletRepository.revealPhrase(wallet, password)
                password = ""
                message = null
            } catch (e: Exception) {
                message = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun reset() {
        password = ""
        revealed = null
        message = null
    }

    override fun onCleared() {
        revealed = null
    }
}

@HiltViewModel
class DeleteWalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
) : ViewModel() {

    val active: StateFlow<Wallet?> = walletRepository.activeWallet
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var wrongPassword by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    /** Wallets with keys need their password; view-only ones have none. */
    fun delete() {
        val wallet = active.value ?: return
        if (wallet is WalletKeystore && password.isEmpty()) return
        busy = true
        viewModelScope.launch {
            try {
                when (wallet) {
                    is WalletKeystore -> walletRepository.delete(wallet, password)
                    is ViewOnlyWallet -> walletRepository.removeViewOnly(wallet)
                }
                password = ""
                wrongPassword = false
                message = "Deleted ${wallet.name}"
            } catch (_: WrongPasswordException) {
                wrongPassword = true
            } catch (e: Exception) {
                message = e.userMessage()
            } finally {
                busy = false
            }
        }
    }

    fun reset() {
        password = ""
        wrongPassword = false
        message = null
    }
}
