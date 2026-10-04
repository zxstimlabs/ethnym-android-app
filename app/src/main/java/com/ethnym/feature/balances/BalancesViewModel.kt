package com.ethnym.feature.balances

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ethnym.core.eth.Addresses
import com.ethnym.data.assets.AssetsRepository
import com.ethnym.data.model.NftCollection
import com.ethnym.data.model.TokenListToken
import com.ethnym.data.portfolio.KnownCollection
import com.ethnym.data.portfolio.KnownToken
import com.ethnym.data.portfolio.OwnedNft
import com.ethnym.data.portfolio.PortfolioRepository
import com.ethnym.data.settings.SettingsRepository
import com.ethnym.data.wallet.WalletRepository
import com.ethnym.feature.common.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

data class TokenRow(val known: KnownToken, val balance: BigInteger?, val failed: Boolean)

data class BalancesUiState(
    val offline: Boolean = false,
    val native: BigInteger? = null,
    val nativeError: String? = null,
    val tokens: List<TokenRow> = emptyList(),
    val tokensError: String? = null,
    val nfts: List<OwnedNft> = emptyList(),
    /** Custom collections where the wallet holds nothing; listed so they can be removed. */
    val emptyCustomCollections: List<KnownCollection> = emptyList(),
    val nftsError: String? = null,
    val loading: Boolean = false,
) {
    /**
     * As in the web wallet: verified tokens appear only with a balance, tokens the user added
     * always appear.
     */
    val visibleTokens: List<TokenRow>
        get() = tokens.filter { it.known.custom || (it.balance != null && it.balance.signum() > 0) }
}

sealed interface Lookup<out T> {
    data object Idle : Lookup<Nothing>
    data object Loading : Lookup<Nothing>
    data class Found<T>(val value: T) : Lookup<T>
    data class Failed(val message: String) : Lookup<Nothing>
}

@HiltViewModel
class BalancesViewModel @Inject constructor(
    walletRepository: WalletRepository,
    settingsRepository: SettingsRepository,
    private val assetsRepository: AssetsRepository,
    private val portfolioRepository: PortfolioRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BalancesUiState())
    val uiState: StateFlow<BalancesUiState> = _uiState.asStateFlow()

    private val refreshes = MutableStateFlow(0)

    var tokenInput by mutableStateOf("")
        private set
    var tokenLookup by mutableStateOf<Lookup<TokenListToken>>(Lookup.Idle)
        private set
    var nftInput by mutableStateOf("")
        private set
    var nftLookup by mutableStateOf<Lookup<NftCollection>>(Lookup.Idle)
        private set

    init {
        viewModelScope.launch {
            combine(
                walletRepository.activeWallet.map { it?.address }.distinctUntilChanged(),
                settingsRepository.settings.map { it.offlineMode to it.activeRpc?.url }.distinctUntilChanged(),
                assetsRepository.customAssets,
                refreshes,
            ) { address, (offline, _), _, _ -> address to offline }
                .collectLatest { (address, offline) ->
                    try {
                        load(address, offline)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Anything load() does not contain itself (say, reading the bundled lists) shows as an error, not a crash.
                        val message = e.userMessage()
                        _uiState.update { it.copy(loading = false, tokensError = message, nftsError = message) }
                    }
                }
        }
    }

    fun refresh() {
        refreshes.update { it + 1 }
    }

    private suspend fun load(address: String?, offline: Boolean) {
        val tokens = portfolioRepository.knownTokens()
        val collections = portfolioRepository.knownCollections()
        val pending = tokens.map { TokenRow(it, balance = null, failed = false) }
        if (address == null || offline) {
            _uiState.value = BalancesUiState(offline = offline, tokens = pending, emptyCustomCollections = collections.filter { it.custom })
            return
        }
        _uiState.update { it.copy(offline = false, loading = true) }
        coroutineScope {
            val native = async { runCatching { portfolioRepository.nativeBalance(address) } }
            val balances = async { runCatching { portfolioRepository.tokenBalances(address, tokens.map { it.token }) } }
            val nfts = async { runCatching { portfolioRepository.ownedNfts(address, collections) } }

            val nativeResult = native.await()
            val balancesResult = balances.await()
            val nftsResult = nfts.await()
            listOf(nativeResult, balancesResult, nftsResult).forEach { result ->
                result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            }
            val owned = nftsResult.getOrNull()?.value.orEmpty()
            _uiState.value = BalancesUiState(
                native = nativeResult.getOrNull(),
                nativeError = nativeResult.exceptionOrNull()?.userMessage(),
                tokens = balancesResult.getOrNull()?.value?.let { map ->
                    tokens.map { known ->
                        val key = known.token.address.lowercase()
                        TokenRow(known, balance = map[key], failed = map.containsKey(key) && map[key] == null)
                    }
                } ?: pending,
                // A partial read keeps what did load and says the rest is missing.
                tokensError = balancesResult.exceptionOrNull()?.userMessage()
                    ?: "Some token balances could not be loaded. Refresh to try again."
                        .takeIf { balancesResult.getOrNull()?.complete == false },
                nfts = owned,
                emptyCustomCollections = collections.filter { collection ->
                    collection.custom && owned.none { it.collection.collection.address.equals(collection.collection.address, ignoreCase = true) }
                },
                nftsError = nftsResult.exceptionOrNull()?.userMessage()
                    ?: "Some NFTs could not be loaded. Refresh to try again.".takeIf { nftsResult.getOrNull()?.complete == false },
                loading = false,
            )
        }
    }

    fun onTokenInput(value: String) {
        tokenInput = value
        tokenLookup = Lookup.Idle
    }

    fun lookUpToken() {
        val address = tokenInput.trim()
        if (!Addresses.isValid(address)) {
            tokenLookup = Lookup.Failed("Must be a valid 0x address (42 chars)")
            return
        }
        tokenLookup = Lookup.Loading
        viewModelScope.launch {
            tokenLookup = try {
                portfolioRepository.tokenMetadata(Addresses.checksum(address))?.let { Lookup.Found(it) }
                    ?: Lookup.Failed("Could not fetch token metadata. Check the address and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Lookup.Failed(e.userMessage())
            }
        }
    }

    fun addToken(token: TokenListToken) {
        viewModelScope.launch {
            assetsRepository.addToken(token)
            tokenInput = ""
            tokenLookup = Lookup.Idle
        }
    }

    fun removeToken(address: String) {
        viewModelScope.launch { assetsRepository.removeToken(address) }
    }

    fun onNftInput(value: String) {
        nftInput = value
        nftLookup = Lookup.Idle
    }

    fun lookUpNft() {
        val address = nftInput.trim()
        if (!Addresses.isValid(address)) {
            nftLookup = Lookup.Failed("Must be a valid 0x address (42 chars)")
            return
        }
        nftLookup = Lookup.Loading
        viewModelScope.launch {
            nftLookup = try {
                portfolioRepository.collectionMetadata(Addresses.checksum(address))?.let { Lookup.Found(it) }
                    ?: Lookup.Failed("Could not fetch collection metadata. Check the address and try again.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Lookup.Failed(e.userMessage())
            }
        }
    }

    fun addNft(collection: NftCollection) {
        viewModelScope.launch {
            assetsRepository.addNft(collection)
            nftInput = ""
            nftLookup = Lookup.Idle
        }
    }

    fun removeNft(address: String) {
        viewModelScope.launch { assetsRepository.removeNft(address) }
    }
}
