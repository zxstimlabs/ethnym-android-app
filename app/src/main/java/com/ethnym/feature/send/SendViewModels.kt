package com.ethnym.feature.send

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethnym.core.eth.Addresses
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.PreparedTransaction
import com.ethnym.core.eth.TransactionJson
import com.ethnym.core.eth.Units
import com.ethnym.core.eth.Erc20
import com.ethnym.core.eth.Erc721
import com.ethnym.core.eth.validateTransactionJson
import com.ethnym.data.activity.ActivityRepository
import com.ethnym.data.chain.EthereumClient
import com.ethnym.data.chain.FeeChoice
import com.ethnym.data.chain.TransactionRequest
import com.ethnym.data.chain.TransactionSender
import com.ethnym.data.model.ActivityRecord
import com.ethnym.data.model.TxType
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.portfolio.KnownCollection
import com.ethnym.data.portfolio.OwnedNft
import com.ethnym.data.portfolio.PortfolioRepository
import com.ethnym.data.settings.SettingsRepository
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.feature.balances.TokenRow
import com.ethnym.feature.common.AddressFieldState
import com.ethnym.feature.common.GasPresetState
import com.ethnym.feature.common.TransactionRunner
import com.ethnym.feature.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

/** Recipient, gas preset, password and transaction progress, shared by the ETH, Token and NFT forms. */
abstract class SendFormViewModel(
    walletRepository: WalletRepository,
    client: EthereumClient,
    sender: TransactionSender,
    activityRepository: ActivityRepository,
) : ViewModel() {

    val activeWallet: StateFlow<WalletKeystore?> = walletRepository.activeWallet
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val recipient = AddressFieldState(viewModelScope, client)
    val gas = GasPresetState(viewModelScope, client)
    val tx = TransactionRunner(viewModelScope, sender, activityRepository)
    var password by mutableStateOf("")

    init {
        gas.refresh()
    }

    /** Checks common to every form; returns the active wallet and recipient when they are ready. */
    protected fun readyToSend(): Pair<WalletKeystore, String>? {
        val wallet = activeWallet.value ?: return null.also { tx.showError("No active wallet selected.") }
        val to = recipient.resolved ?: return null.also {
            tx.showError(
                if (recipient.isEnsName) "ENS address not resolved. Tap the search icon to resolve it first."
                else "Enter a valid recipient address.",
            )
        }
        if (password.isEmpty()) return null.also { tx.showError("Please enter your wallet password") }
        if (gas.gasPrice == null) return null.also { tx.showError("Gas price unavailable. Refresh it and try again.") }
        return wallet to to
    }

    protected val ensName: String? get() = recipient.text.trim().takeIf { recipient.isEnsName }

    open fun reset() {
        recipient.clear()
        password = ""
        tx.reset()
    }
}

/** Validation for the amount field, with the web wallet's messages. */
fun amountError(amount: String, decimals: Int, balance: BigInteger?): String? {
    if (amount.isBlank()) return "Please enter an amount to send"
    val value = try {
        Units.parse(amount, decimals)
    } catch (e: IllegalArgumentException) {
        return e.message ?: "Invalid amount format"
    }
    if (balance != null && value > balance) return "Insufficient balance"
    return null
}

@HiltViewModel
class SendNativeViewModel @Inject constructor(
    walletRepository: WalletRepository,
    private val client: EthereumClient,
    sender: TransactionSender,
    activityRepository: ActivityRepository,
) : SendFormViewModel(walletRepository, client, sender, activityRepository) {

    var amount by mutableStateOf("")
    var balance by mutableStateOf<BigInteger?>(null)
        private set
    var balanceLoading by mutableStateOf(false)
        private set

    val amountError: String? get() = amount.takeIf { it.isNotEmpty() }?.let { amountError(it, 18, balance) }

    init {
        viewModelScope.launch { activeWallet.map { it?.address }.distinctUntilChanged().collectLatest { refreshBalance() } }
    }

    fun refreshBalance() {
        val address = activeWallet.value?.address ?: run { balance = null; return }
        balanceLoading = true
        viewModelScope.launch {
            balance = runCatching { client.balance(address) }.getOrNull()
            balanceLoading = false
        }
    }

    /** 25 / 50 / 75 / 100 % of the balance. */
    fun fillPercent(percent: Int) {
        balance?.let { amount = Units.formatEther(it * BigInteger.valueOf(percent.toLong()) / BigInteger.valueOf(100)) }
    }

    fun send() {
        if (amountError(amount, 18, balance) != null) return tx.showError(amountError(amount, 18, balance)!!)
        val (wallet, to) = readyToSend() ?: return
        val value = Units.parseEther(amount)
        val gasPrice = gas.gasPrice!!
        tx.send(
            wallet = wallet,
            password = password,
            request = TransactionRequest(to = to, value = value, fees = FeeChoice.GasPrice(gasPrice)),
            activity = ActivityRecord(
                txHash = "",
                from = wallet.address,
                to = to,
                chainId = Mainnet.CHAIN_ID,
                type = TxType.Native,
                nativeValue = value.toString(),
                gasPrice = gasPrice.toString(),
                ensName = ensName,
                timestamp = 0,
            ),
        )
    }

    override fun reset() {
        super.reset()
        amount = ""
    }
}

data class TokenInfo(val name: String, val symbol: String, val decimals: Int, val balance: BigInteger?)

@HiltViewModel
class SendTokenViewModel @Inject constructor(
    walletRepository: WalletRepository,
    client: EthereumClient,
    sender: TransactionSender,
    activityRepository: ActivityRepository,
    private val portfolioRepository: PortfolioRepository,
) : SendFormViewModel(walletRepository, client, sender, activityRepository) {

    val token = AddressFieldState(viewModelScope, client)
    var amount by mutableStateOf("")
    var info by mutableStateOf<TokenInfo?>(null)
        private set
    var infoLoading by mutableStateOf(false)
        private set
    var infoError by mutableStateOf<String?>(null)
        private set

    var pickerTokens by mutableStateOf<List<TokenRow>>(emptyList())
        private set
    var pickerLoading by mutableStateOf(false)
        private set

    val amountError: String? get() = amount.takeIf { it.isNotEmpty() }?.let { amountError(it, info?.decimals ?: 18, info?.balance) }

    init {
        viewModelScope.launch {
            combine(snapshotFlow { token.resolved }, activeWallet.map { it?.address }) { tokenAddress, owner -> tokenAddress to owner }
                .distinctUntilChanged()
                .collectLatest { (tokenAddress, owner) -> loadInfo(tokenAddress, owner) }
        }
    }

    fun refreshInfo() {
        viewModelScope.launch { loadInfo(token.resolved, activeWallet.value?.address) }
    }

    private suspend fun loadInfo(tokenAddress: String?, owner: String?) {
        info = null
        infoError = null
        if (tokenAddress == null) return
        infoLoading = true
        try {
            val metadata = portfolioRepository.tokenMetadata(tokenAddress)
            if (metadata == null) {
                infoError = "Not an ERC-20 token"
            } else {
                val balance = owner?.let { portfolioRepository.tokenBalances(it, listOf(metadata)).value[metadata.address.lowercase()] }
                info = TokenInfo(metadata.name, metadata.symbol, metadata.decimals, balance)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            infoError = e.userMessage()
        } finally {
            infoLoading = false
        }
    }

    /** Tokens for the picker, held ones first by balance (as the web wallet sorts them). */
    fun loadPicker() {
        pickerLoading = true
        viewModelScope.launch {
            try {
                val known = portfolioRepository.knownTokens()
                val owner = activeWallet.value?.address
                val balances = owner?.let { runCatching { portfolioRepository.tokenBalances(it, known.map { k -> k.token }).value }.getOrNull() }
                pickerTokens = known
                    .map { TokenRow(it, balance = balances?.get(it.token.address.lowercase()), failed = false) }
                    .sortedByDescending { it.balance ?: BigInteger.ZERO }
            } finally {
                pickerLoading = false
            }
        }
    }

    fun pickToken(address: String) {
        token.onTextChange(address)
    }

    fun fillPercent(percent: Int) {
        val current = info ?: return
        current.balance?.let { amount = Units.format(it * BigInteger.valueOf(percent.toLong()) / BigInteger.valueOf(100), current.decimals) }
    }

    fun send() {
        val tokenAddress = token.resolved ?: return tx.showError("Enter a token address")
        val current = info ?: return tx.showError("Token details are still loading")
        amountError(amount, current.decimals, current.balance)?.let { return tx.showError(it) }
        val (wallet, to) = readyToSend() ?: return
        val value = Units.parse(amount, current.decimals)
        val gasPrice = gas.gasPrice!!
        tx.send(
            wallet = wallet,
            password = password,
            request = TransactionRequest(to = tokenAddress, data = Erc20.transfer(to, value), fees = FeeChoice.GasPrice(gasPrice)),
            activity = ActivityRecord(
                txHash = "",
                from = wallet.address,
                to = to,
                chainId = Mainnet.CHAIN_ID,
                type = TxType.Erc20,
                tokenValue = value.toString(),
                tokenAddress = tokenAddress,
                tokenSymbol = current.symbol,
                tokenDecimals = current.decimals,
                gasPrice = gasPrice.toString(),
                ensName = ensName,
                timestamp = 0,
            ),
        )
    }

    override fun reset() {
        super.reset()
        token.clear()
        amount = ""
    }
}

data class NftInfo(val name: String?, val symbol: String?, val owner: String?)

@HiltViewModel
class SendNftViewModel @Inject constructor(
    walletRepository: WalletRepository,
    client: EthereumClient,
    sender: TransactionSender,
    activityRepository: ActivityRepository,
    private val portfolioRepository: PortfolioRepository,
) : SendFormViewModel(walletRepository, client, sender, activityRepository) {

    val collection = AddressFieldState(viewModelScope, client)
    var tokenId by mutableStateOf("")
    var info by mutableStateOf<NftInfo?>(null)
        private set
    var infoLoading by mutableStateOf(false)
        private set
    var infoError by mutableStateOf<String?>(null)
        private set

    var pickerOwned by mutableStateOf<List<OwnedNft>>(emptyList())
        private set
    var pickerCollections by mutableStateOf<List<KnownCollection>>(emptyList())
        private set
    var pickerLoading by mutableStateOf(false)
        private set

    val tokenIdError: String?
        get() = when {
            tokenId.isEmpty() -> null
            tokenId.toBigIntegerOrNull()?.takeIf { it.signum() >= 0 } == null -> "Please enter a valid token ID"
            else -> null
        }

    /** Whether the active wallet owns the token, once known. */
    val ownsToken: Boolean?
        get() {
            val owner = info?.owner ?: return null
            val me = activeWallet.value?.address ?: return null
            return owner.equals(me, ignoreCase = true)
        }

    init {
        viewModelScope.launch {
            combine(snapshotFlow { collection.resolved to tokenId.toBigIntegerOrNull() }, activeWallet.map { it?.address }) { pair, _ -> pair }
                .distinctUntilChanged()
                .collectLatest { (address, id) -> loadInfo(address, id) }
        }
    }

    private suspend fun loadInfo(address: String?, id: BigInteger?) {
        info = null
        infoError = null
        if (address == null || id == null) return
        infoLoading = true
        try {
            val (name, symbol, owner) = portfolioRepository.nftDetails(address, id)
            info = NftInfo(name, symbol, owner?.let(Addresses::checksum))
            if (owner == null) infoError = "Could not read the owner of this token"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            infoError = e.userMessage()
        } finally {
            infoLoading = false
        }
    }

    fun loadPicker() {
        pickerLoading = true
        viewModelScope.launch {
            try {
                val collections = portfolioRepository.knownCollections()
                val owner = activeWallet.value?.address
                val owned = owner?.let { runCatching { portfolioRepository.ownedNfts(it, collections).value }.getOrNull() }.orEmpty()
                pickerOwned = owned
                pickerCollections = collections.filter { c ->
                    owned.none { it.collection.collection.address.equals(c.collection.address, ignoreCase = true) }
                }
            } finally {
                pickerLoading = false
            }
        }
    }

    /** An owned NFT fills both fields; a collection fills the contract and leaves the ID to type. */
    fun pick(contract: String, id: BigInteger?) {
        collection.onTextChange(contract)
        tokenId = id?.toString().orEmpty()
    }

    fun send() {
        val contract = collection.resolved ?: return tx.showError("Enter a collection address")
        val id = tokenId.toBigIntegerOrNull()?.takeIf { it.signum() >= 0 } ?: return tx.showError("Please enter a valid token ID")
        if (ownsToken == false) return tx.showError("The active wallet does not own this token")
        val (wallet, to) = readyToSend() ?: return
        val gasPrice = gas.gasPrice!!
        tx.send(
            wallet = wallet,
            password = password,
            request = TransactionRequest(
                to = contract,
                data = Erc721.safeTransferFrom(wallet.address, to, id),
                fees = FeeChoice.GasPrice(gasPrice),
            ),
            activity = ActivityRecord(
                txHash = "",
                from = wallet.address,
                to = to,
                chainId = Mainnet.CHAIN_ID,
                type = TxType.Erc721,
                nftId = id.toString(),
                tokenAddress = contract,
                tokenSymbol = info?.symbol,
                gasPrice = gasPrice.toString(),
                ensName = ensName,
                timestamp = 0,
            ),
        )
    }

    override fun reset() {
        super.reset()
        collection.clear()
        tokenId = ""
    }
}

/**
 * Sign tab: a transaction pasted as JSON. Online, missing fields are filled in and the
 * transaction is broadcast. In offline mode it is only signed, and the raw transaction is
 * shown to broadcast elsewhere.
 */
@HiltViewModel
class SignTransactionViewModel @Inject constructor(
    walletRepository: WalletRepository,
    private val settingsRepository: SettingsRepository,
    private val sender: TransactionSender,
    activityRepository: ActivityRepository,
) : ViewModel() {

    val activeWallet: StateFlow<WalletKeystore?> = walletRepository.activeWallet
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val offline: StateFlow<Boolean> = settingsRepository.settings.map { it.offlineMode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val tx = TransactionRunner(viewModelScope, sender, activityRepository)
    var json by mutableStateOf("")
    var password by mutableStateOf("")
    var signedOffline by mutableStateOf<String?>(null)
        private set
    var signingOffline by mutableStateOf(false)
        private set

    val validation: TransactionJson? get() = json.takeIf { it.isNotEmpty() }?.let(::validateTransactionJson)

    val parsed: PreparedTransaction? get() = (validation as? TransactionJson.Valid)?.transaction

    fun send() {
        val transaction = when (val result = validateTransactionJson(json)) {
            is TransactionJson.Invalid -> return tx.showError(result.message)
            is TransactionJson.Valid -> result.transaction
        }
        val wallet = activeWallet.value ?: return tx.showError("No active wallet selected.")
        if (transaction.chainId != Mainnet.CHAIN_ID) return tx.showError("Only Ethereum mainnet (chainId 1) is supported")
        if (!Addresses.isValid(transaction.to)) return tx.showError("'to' is not a valid address")
        if (password.isEmpty()) return tx.showError("Please enter your wallet password")
        val request = TransactionRequest(
            to = transaction.to,
            value = transaction.value ?: BigInteger.ZERO,
            data = transaction.data ?: "0x",
            chainId = transaction.chainId,
            nonce = transaction.nonce,
            gasLimit = transaction.gas,
            fees = FeeChoice.Eip1559(transaction.maxFeePerGas, transaction.maxPriorityFeePerGas),
        )
        viewModelScope.launch {
            if (settingsRepository.settings.first().offlineMode) {
                signingOffline = true
                try {
                    signedOffline = sender.signOffline(wallet, password, request).raw
                    tx.clearError()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    tx.showError(e.userMessage())
                } finally {
                    signingOffline = false
                }
            } else {
                // The web wallet does not record raw transactions in Activity; neither does this.
                tx.send(wallet, password, request, activity = null)
            }
        }
    }

    fun reset() {
        json = ""
        password = ""
        signedOffline = null
        tx.reset()
    }
}
