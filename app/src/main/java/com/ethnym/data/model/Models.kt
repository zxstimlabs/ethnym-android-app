package com.ethnym.data.model

import com.ethnym.core.crypto.KeystoreCrypto
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Persisted and exported shapes. Field names match the web wallet's JSON so keystores, backups
// and contacts move between the two apps unchanged.

/**
 * A wallet: the BIP-39 phrase encrypted with the user's password (keystore v3 format), plus the
 * name and address shown in the UI. The web wallet calls this `UmKeystore`.
 */
@Serializable
data class WalletKeystore(
    val crypto: KeystoreCrypto,
    val id: String,
    val version: Int = 3,
    val meta: Meta,
    val name: String,
    val address: String,
) {
    @Serializable
    data class Meta(val type: String, val note: String, val umVersion: String? = null)

    companion object {
        const val CURRENT_UM_VERSION = "0.0.1"
        const val CURRENT_META_TYPE = "password-keystore-seedphrase"
        const val META_NOTE =
            "the 12 words secret phrase (aka mnemonic phrase) is encrypted with the password using the keystore encryption process"
    }
}

/** Wallets on an older storage format (missing `umVersion`); migrating only rewrites metadata. */
fun WalletKeystore.needsMigration(): Boolean =
    meta.umVersion != WalletKeystore.CURRENT_UM_VERSION || meta.type != WalletKeystore.CURRENT_META_TYPE

@Serializable
data class Contact(
    val id: String,
    val address: String,
    val name: String,
    /** EVM chain ID, or null for chain-agnostic. */
    val chain: Long? = null,
    val metadata: Metadata,
) {
    @Serializable
    data class Metadata(val tags: List<String>, val version: String, val note: String)
}

@Serializable
enum class TxType {
    @SerialName("native") Native,
    @SerialName("erc20") Erc20,
    @SerialName("erc721") Erc721,
    @SerialName("raw") Raw,
}

@Serializable
data class ActivityRecord(
    val id: Long? = null,
    val txHash: String,
    val from: String,
    val to: String,
    val chainId: Long,
    val type: TxType,
    /** Amount in wei. */
    val nativeValue: String? = null,
    /** ERC-20 amount in base units. */
    val tokenValue: String? = null,
    /** ERC-721 token ID. */
    val nftId: String? = null,
    val tokenAddress: String? = null,
    val tokenSymbol: String? = null,
    val tokenDecimals: Int? = null,
    /** Gas price in wei at the time of sending. */
    val gasPrice: String? = null,
    /** ENS name, when the recipient was entered as one. */
    val ensName: String? = null,
    /** Epoch millis at confirmation. */
    val timestamp: Long,
)

@Serializable
data class RpcEntry(
    val id: String,
    /** Optional label, e.g. "Alchemy Mainnet". */
    val name: String? = null,
    val url: String,
    val chainId: Long,
)

@Serializable
data class WalletSettings(
    val rpcList: List<RpcEntry> = emptyList(),
    /** The RPC in use, or null for the built-in default. */
    val activeRpc: RpcEntry? = null,
    /** Suspends all network requests (balances, gas, ENS, broadcasting). */
    val offlineMode: Boolean = false,
)

@Serializable
data class TokenListToken(
    val chainId: Long,
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val logoURI: String? = null,
)

@Serializable
data class NftCollection(
    val chainId: Long,
    val address: String,
    val name: String,
    val symbol: String,
    val standard: String = "ERC721",
)

/** The wallet list and the selected wallet, stored together. */
@Serializable
data class WalletVault(
    val wallets: List<WalletKeystore> = emptyList(),
    val activeWalletId: String? = null,
)

/** Tokens and NFT collections the user added by contract address. */
@Serializable
data class CustomAssets(
    val tokens: List<TokenListToken> = emptyList(),
    val nfts: List<NftCollection> = emptyList(),
)
