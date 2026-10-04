package com.ethnym.core.crypto

import kotlinx.serialization.Serializable
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.generators.SCrypt
import org.bouncycastle.crypto.params.KeyParameter
import org.web3j.crypto.Hash
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The Web3 Secret Storage (keystore v3) `crypto` object.
 *
 * The web wallet (via ox's `Keystore`) encrypts the BIP-39 phrase itself rather than a private
 * key, which web3j's `Wallet` cannot do, since it only encrypts and decrypts EC keys. This file
 * assembles that same format from library primitives so keystores move between the two apps:
 * BouncyCastle for PBKDF2 and scrypt, the platform's AES-128-CTR, and web3j's Keccak-256 for the MAC.
 */
@Serializable
data class KeystoreCrypto(
    val cipher: String,
    val ciphertext: String,
    val cipherparams: CipherParams,
    val kdf: String,
    val kdfparams: KdfParams,
    val mac: String,
) {
    @Serializable
    data class CipherParams(val iv: String)

    /** PBKDF2 uses `c` and `prf`; scrypt uses `n`, `r` and `p`. */
    @Serializable
    data class KdfParams(
        val c: Int? = null,
        val n: Int? = null,
        val r: Int? = null,
        val p: Int? = null,
        val dklen: Int,
        val prf: String? = null,
        val salt: String,
    )
}

class WrongPasswordException : Exception("Wrong password")

object SecretKeystore {

    /** ox's default PBKDF2 work factor. */
    const val PBKDF2_ITERATIONS = 262_144

    fun encrypt(
        secret: ByteArray,
        password: String,
        iterations: Int = PBKDF2_ITERATIONS,
        salt: ByteArray = randomBytes(32),
        iv: ByteArray = randomBytes(16),
    ): KeystoreCrypto {
        val derived = pbkdf2(password, salt, iterations, keyLength = 32)
        val ciphertext = aes128Ctr(Cipher.ENCRYPT_MODE, derived.copyOfRange(0, 16), iv, secret)
        return KeystoreCrypto(
            cipher = "aes-128-ctr",
            ciphertext = ciphertext.toHexString(),
            cipherparams = KeystoreCrypto.CipherParams(iv = iv.toHexString()),
            kdf = "pbkdf2",
            kdfparams = KeystoreCrypto.KdfParams(c = iterations, dklen = 32, prf = "hmac-sha256", salt = salt.toHexString()),
            mac = mac(derived, ciphertext).toHexString(),
        )
    }

    /** @throws WrongPasswordException when the MAC does not match. */
    fun decrypt(crypto: KeystoreCrypto, password: String): ByteArray {
        require(crypto.cipher == "aes-128-ctr") { "Unsupported cipher: ${crypto.cipher}" }
        val params = crypto.kdfparams
        val salt = params.salt.hexToByteArray()
        val derived = when (crypto.kdf) {
            "pbkdf2" -> {
                require(params.prf == null || params.prf == "hmac-sha256") { "Unsupported PRF: ${params.prf}" }
                pbkdf2(password, salt, requireNotNull(params.c) { "Missing kdfparams.c" }, params.dklen)
            }
            "scrypt" -> SCrypt.generate(
                password.toByteArray(Charsets.UTF_8),
                salt,
                requireNotNull(params.n) { "Missing kdfparams.n" },
                requireNotNull(params.r) { "Missing kdfparams.r" },
                requireNotNull(params.p) { "Missing kdfparams.p" },
                params.dklen,
            )
            else -> throw IllegalArgumentException("Unsupported KDF: ${crypto.kdf}")
        }
        val ciphertext = crypto.ciphertext.hexToByteArray()
        if (!mac(derived, ciphertext).contentEquals(crypto.mac.hexToByteArray())) throw WrongPasswordException()
        return aes128Ctr(Cipher.DECRYPT_MODE, derived.copyOfRange(0, 16), crypto.cipherparams.iv.hexToByteArray(), ciphertext)
    }

    fun newId(): String = UUID.randomUUID().toString()

    private fun mac(derivedKey: ByteArray, ciphertext: ByteArray): ByteArray =
        Hash.sha3(derivedKey.copyOfRange(16, 32) + ciphertext)

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(password.toByteArray(Charsets.UTF_8), salt, iterations)
        return (generator.generateDerivedParameters(keyLength * 8) as KeyParameter).key
    }

    private fun aes128Ctr(mode: Int, key: ByteArray, iv: ByteArray, input: ByteArray): ByteArray =
        Cipher.getInstance("AES/CTR/NoPadding").run {
            init(mode, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
            doFinal(input)
        }

    private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)
}
