package com.ethnym.data.assets

import android.content.Context
import androidx.datastore.core.DataStore
import com.ethnym.core.eth.Mainnet
import com.ethnym.data.AppJson
import com.ethnym.data.model.CustomAssets
import com.ethnym.data.model.NftCollection
import com.ethnym.data.model.TokenListToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** Mainnet tokens and NFT collections: the bundled (verified) lists plus any the user added. */
@Singleton
class AssetsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dataStore: DataStore<CustomAssets>,
) {
    val customAssets: Flow<CustomAssets> = dataStore.data

    @Volatile private var tokenList: List<TokenListToken>? = null

    @Volatile private var nftList: List<NftCollection>? = null

    /** The bundled token list, without its ETH placeholder entry. */
    suspend fun tokenList(): List<TokenListToken> = tokenList ?: withContext(Dispatchers.IO) {
        AppJson.default.decodeFromString<TokenListFile>(readAsset("token-list.json")).tokens
            .filter { it.chainId == Mainnet.CHAIN_ID && !it.address.equals(ETH_SENTINEL, ignoreCase = true) }
            .also { tokenList = it }
    }

    suspend fun nftList(): List<NftCollection> = nftList ?: withContext(Dispatchers.IO) {
        AppJson.default.decodeFromString<NftListFile>(readAsset("nft-list.json")).collections
            .filter { it.chainId == Mainnet.CHAIN_ID }
            .also { nftList = it }
    }

    suspend fun addToken(token: TokenListToken) {
        dataStore.updateData { assets ->
            if (assets.tokens.any { it.address.equals(token.address, ignoreCase = true) }) assets
            else assets.copy(tokens = assets.tokens + token)
        }
    }

    suspend fun removeToken(address: String) {
        dataStore.updateData { assets ->
            assets.copy(tokens = assets.tokens.filterNot { it.address.equals(address, ignoreCase = true) })
        }
    }

    suspend fun addNft(collection: NftCollection) {
        dataStore.updateData { assets ->
            if (assets.nfts.any { it.address.equals(collection.address, ignoreCase = true) }) assets
            else assets.copy(nfts = assets.nfts + collection)
        }
    }

    suspend fun removeNft(address: String) {
        dataStore.updateData { assets ->
            assets.copy(nfts = assets.nfts.filterNot { it.address.equals(address, ignoreCase = true) })
        }
    }

    private fun readAsset(name: String): String = context.assets.open(name).bufferedReader().use { it.readText() }

    @Serializable
    private data class TokenListFile(val tokens: List<TokenListToken>)

    @Serializable
    private data class NftListFile(val collections: List<NftCollection>)

    private companion object {
        const val ETH_SENTINEL = "0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"
    }
}
