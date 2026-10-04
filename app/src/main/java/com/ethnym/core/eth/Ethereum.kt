package com.ethnym.core.eth

import org.web3j.crypto.Keys
import java.math.BigDecimal
import java.math.BigInteger

/** The one network the wallet supports, as in the web wallet. */
object Mainnet {
    const val CHAIN_ID = 1L
    const val NAME = "Ethereum"
    const val NATIVE_NAME = "Ether"
    const val NATIVE_SYMBOL = "ETH"
    const val NATIVE_DECIMALS = 18
    const val EXPLORER_URL = "https://etherscan.io"

    fun txUrl(hash: String) = "$EXPLORER_URL/tx/$hash"
}

object Addresses {
    private val addressRegex = Regex("^0x[0-9a-fA-F]{40}$")

    /**
     * viem's `isAddress` rule: 40 hex characters, and a mixed-case address must carry a valid
     * EIP-55 checksum.
     */
    fun isValid(address: String): Boolean {
        if (!addressRegex.matches(address)) return false
        if (address.lowercase() == address) return true
        return Keys.toChecksumAddress(address) == address
    }

    fun checksum(address: String): String = Keys.toChecksumAddress(address)

    fun isEnsName(input: String): Boolean = input.endsWith(".eth") && input.length > ".eth".length

    const val ZERO = "0x0000000000000000000000000000000000000000"
}

/** Decimal string ⇄ base-unit conversion, like viem's `parseUnits` and `formatUnits`. */
object Units {
    private val decimalRegex = Regex("^\\d*\\.?\\d*$")

    /**
     * @throws IllegalArgumentException for anything that is not a plain non-negative decimal, or
     * that has more fractional digits than [decimals] allows (viem would silently round).
     */
    fun parse(value: String, decimals: Int): BigInteger {
        val trimmed = value.trim()
        require(trimmed.isNotEmpty() && trimmed != "." && decimalRegex.matches(trimmed)) { "Invalid amount format" }
        val amount = BigDecimal(if (trimmed.startsWith(".")) "0$trimmed" else trimmed)
        require(amount.stripTrailingZeros().scale() <= decimals) { "Too many decimal places (max $decimals)" }
        return amount.movePointRight(decimals).toBigIntegerExact()
    }

    fun format(value: BigInteger, decimals: Int): String =
        BigDecimal(value, decimals).stripTrailingZeros().toPlainString()

    fun parseEther(value: String) = parse(value, 18)

    fun formatEther(value: BigInteger) = format(value, 18)

    fun formatGwei(value: BigInteger) = format(value, 9)

    fun parseGwei(value: String) = parse(value, 9)
}

/**
 * Pulls an address out of scanned QR data, handling:
 *   - plain address: `0xABC...`
 *   - ERC-3770 short name: `eth:0xABC...`
 *   - CAIP-10 / EIP-155: `eip155:1:0xABC...`
 *   - EIP-681 URI: `ethereum:0xABC...@1/transfer?...`
 */
fun parseQrAddress(raw: String): String? {
    val candidate = raw.trim().substringAfterLast(":")
        .substringBefore("@").substringBefore("/").substringBefore("?")
    return candidate.takeIf { Regex("^0x[0-9a-fA-F]{40}$").matches(it) }
}

fun shortHash(hash: String): String = if (hash.length > 10) "${hash.take(6)}...${hash.takeLast(4)}" else hash
