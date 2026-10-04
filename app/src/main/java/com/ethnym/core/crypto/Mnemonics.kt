package com.ethnym.core.crypto

import org.web3j.crypto.Bip32ECKeyPair
import org.web3j.crypto.Credentials
import org.web3j.crypto.Keys
import org.web3j.crypto.MnemonicUtils
import java.security.SecureRandom

/** BIP-39 phrases and the Ethereum account they derive, via web3j. */
object Mnemonics {

    /** m/44'/60'/0'/0/0, the path viem's `mnemonicToAccount` uses, so addresses match the web wallet. */
    private val ETHEREUM_PATH = intArrayOf(
        44 or Bip32ECKeyPair.HARDENED_BIT,
        60 or Bip32ECKeyPair.HARDENED_BIT,
        0 or Bip32ECKeyPair.HARDENED_BIT,
        0,
        0,
    )

    /** A new 12-word phrase (128 bits of entropy). */
    fun generate(random: SecureRandom = SecureRandom()): String =
        MnemonicUtils.generateMnemonic(ByteArray(16).also(random::nextBytes))

    /** Every word is in the English wordlist and the checksum matches. */
    fun isValid(phrase: String): Boolean = MnemonicUtils.validateMnemonic(phrase)

    /**
     * Canonical form for a phrase the user typed: lowercase, single spaces. Apply it before a
     * phrase is stored, never to one already stored, because the stored bytes are what the
     * address was derived from.
     */
    fun normalizeInput(phrase: String): String =
        phrase.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    /**
     * The signing credentials for [phrase]. They hold the private key, so keep them only for the
     * duration of one operation.
     */
    fun credentials(phrase: String): Credentials {
        val master = Bip32ECKeyPair.generateKeyPair(MnemonicUtils.generateSeed(phrase, ""))
        return Credentials.create(Bip32ECKeyPair.deriveKeyPair(master, ETHEREUM_PATH))
    }

    /** EIP-55 checksummed address for [phrase]. */
    fun address(phrase: String): String = Keys.toChecksumAddress(credentials(phrase).address)
}
