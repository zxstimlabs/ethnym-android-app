package com.ethnym.data.portfolio

import com.ethnym.core.eth.Erc20
import com.ethnym.core.eth.Erc721
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.Multicall3
import com.ethnym.data.assets.AssetsRepository
import com.ethnym.data.chain.EthereumClient
import com.ethnym.data.model.NftCollection
import com.ethnym.data.model.TokenListToken
import kotlinx.coroutines.flow.first
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/** A token from the bundled list ([verified]) or added by the user ([custom]); it can be both. */
data class KnownToken(val token: TokenListToken, val verified: Boolean, val custom: Boolean)

data class KnownCollection(val collection: NftCollection, val verified: Boolean, val custom: Boolean)

data class OwnedNft(val collection: KnownCollection, val tokenId: BigInteger)

/** A bulk read that may be partial: [complete] is false when part of it could not be fetched. */
data class BulkRead<T>(val value: T, val complete: Boolean)

/** Balances and holdings for an address, read in bulk through Multicall3. */
@Singleton
class PortfolioRepository @Inject constructor(
    private val client: EthereumClient,
    private val assetsRepository: AssetsRepository,
) {
    /** Bundled tokens first, then custom ones not already in the list. */
    suspend fun knownTokens(): List<KnownToken> {
        val list = assetsRepository.tokenList()
        val custom = assetsRepository.customAssets.first().tokens.filter { it.chainId == Mainnet.CHAIN_ID }
        val customAddresses = custom.map { it.address.lowercase() }.toSet()
        val listAddresses = list.map { it.address.lowercase() }.toSet()
        return list.map { KnownToken(it, verified = true, custom = it.address.lowercase() in customAddresses) } +
            custom.filter { it.address.lowercase() !in listAddresses }.map { KnownToken(it, verified = false, custom = true) }
    }

    suspend fun knownCollections(): List<KnownCollection> {
        val list = assetsRepository.nftList()
        val custom = assetsRepository.customAssets.first().nfts.filter { it.chainId == Mainnet.CHAIN_ID }
        val customAddresses = custom.map { it.address.lowercase() }.toSet()
        val listAddresses = list.map { it.address.lowercase() }.toSet()
        return list.map { KnownCollection(it, verified = true, custom = it.address.lowercase() in customAddresses) } +
            custom.filter { it.address.lowercase() !in listAddresses }.map { KnownCollection(it, verified = false, custom = true) }
    }

    suspend fun nativeBalance(owner: String): BigInteger = client.balance(owner)

    /** ERC-20 balances keyed by lowercase token address; a failed call maps to null. */
    suspend fun tokenBalances(owner: String, tokens: List<TokenListToken>): BulkRead<Map<String, BigInteger?>> {
        if (tokens.isEmpty()) return BulkRead(emptyMap(), complete = true)
        val results = client.multicall(tokens.map { Multicall3.Call(it.address, Erc20.balanceOf(owner)) })
        val balances = tokens.zip(results).associate { (token, result) ->
            token.address.lowercase() to if (result.success) Erc20.decodeUint(result.returnData) else null
        }
        return BulkRead(balances, complete = results.all { it.delivered })
    }

    /**
     * ERC-721 tokens held by [owner] in [collections]: `balanceOf` per collection, then
     * `tokenOfOwnerByIndex` for each held token (needs ERC721Enumerable, as in the web wallet).
     * A broken contract can report any balance, so at most [MAX_NFTS_PER_COLLECTION] are read.
     */
    suspend fun ownedNfts(owner: String, collections: List<KnownCollection>): BulkRead<List<OwnedNft>> {
        if (collections.isEmpty()) return BulkRead(emptyList(), complete = true)
        val results = client.multicall(collections.map { Multicall3.Call(it.collection.address, Erc721.balanceOf(owner)) })
        val counts = results.map { if (it.success) Erc20.decodeUint(it.returnData) ?: BigInteger.ZERO else BigInteger.ZERO }
        val requests = collections.zip(counts).flatMap { (collection, count) ->
            (0 until count.min(MAX_NFTS_PER_COLLECTION).toInt()).map { index -> collection to index }
        }
        val countsComplete = results.all { it.delivered } && counts.none { it > MAX_NFTS_PER_COLLECTION }
        if (requests.isEmpty()) return BulkRead(emptyList(), countsComplete)
        val ids = client.multicall(
            requests.map { (collection, index) ->
                Multicall3.Call(collection.collection.address, Erc721.tokenOfOwnerByIndex(owner, BigInteger.valueOf(index.toLong())))
            },
        )
        val owned = requests.zip(ids).mapNotNull { (request, result) ->
            val tokenId = if (result.success) Erc20.decodeUint(result.returnData) else null
            tokenId?.let { OwnedNft(request.first, it) }
        }
        return BulkRead(owned, complete = countsComplete && ids.all { it.delivered })
    }

    /** Reads name, symbol and decimals; null when the contract does not answer like an ERC-20. */
    suspend fun tokenMetadata(address: String): TokenListToken? {
        val (name, symbol, decimals) = client.multicall(
            listOf(Multicall3.Call(address, Erc20.name), Multicall3.Call(address, Erc20.symbol), Multicall3.Call(address, Erc20.decimals)),
        )
        return TokenListToken(
            chainId = Mainnet.CHAIN_ID,
            address = address,
            name = name.takeIf { it.success }?.let { Erc20.decodeString(it.returnData) } ?: return null,
            symbol = symbol.takeIf { it.success }?.let { Erc20.decodeString(it.returnData) } ?: return null,
            decimals = decimals.takeIf { it.success }?.let { Erc20.decodeDecimals(it.returnData) } ?: return null,
        )
    }

    /** Reads name and symbol; null when the contract does not answer like an ERC-721. */
    suspend fun collectionMetadata(address: String): NftCollection? {
        val (name, symbol) = client.multicall(listOf(Multicall3.Call(address, Erc20.name), Multicall3.Call(address, Erc20.symbol)))
        return NftCollection(
            chainId = Mainnet.CHAIN_ID,
            address = address,
            name = name.takeIf { it.success }?.let { Erc20.decodeString(it.returnData) } ?: return null,
            symbol = symbol.takeIf { it.success }?.let { Erc20.decodeString(it.returnData) } ?: return null,
        )
    }

    /** Collection name/symbol and the current owner of [tokenId]; each null if its call failed. */
    suspend fun nftDetails(collection: String, tokenId: BigInteger): Triple<String?, String?, String?> {
        val (name, symbol, owner) = client.multicall(
            listOf(
                Multicall3.Call(collection, Erc20.name),
                Multicall3.Call(collection, Erc20.symbol),
                Multicall3.Call(collection, Erc721.ownerOf(tokenId)),
            ),
        )
        return Triple(
            name.takeIf { it.success }?.let { Erc20.decodeString(it.returnData) },
            symbol.takeIf { it.success }?.let { Erc20.decodeString(it.returnData) },
            owner.takeIf { it.success }?.let { Erc721.decodeAddress(it.returnData) },
        )
    }

    private companion object {
        val MAX_NFTS_PER_COLLECTION: BigInteger = BigInteger.valueOf(1_000)
    }
}
