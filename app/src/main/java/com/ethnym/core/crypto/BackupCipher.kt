package com.ethnym.core.crypto

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Encryption for the full-device backup file, byte-compatible with the web wallet's
 * `lib/crypto.ts`: PBKDF2-SHA256 (600,000 iterations) derives an AES-256-GCM key. Salt, IV and
 * ciphertext (with the GCM tag appended, as WebCrypto does) are base64 encoded.
 */
object BackupCipher {

    const val KDF_ITERATIONS = 600_000

    data class Encrypted(val kdfSalt: String, val iv: String, val data: String)

    fun encrypt(
        plaintext: String,
        password: String,
        salt: ByteArray = randomBytes(16),
        iv: ByteArray = randomBytes(12),
    ): Encrypted {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        return Encrypted(
            kdfSalt = base64.encodeToString(salt),
            iv = base64.encodeToString(iv),
            data = base64.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))),
        )
    }

    /** @throws WrongPasswordException when authentication fails. */
    fun decrypt(encrypted: Encrypted, password: String, iterations: Int = KDF_ITERATIONS): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            deriveKey(password, base64Decoder.decode(encrypted.kdfSalt), iterations),
            GCMParameterSpec(128, base64Decoder.decode(encrypted.iv)),
        )
        return try {
            cipher.doFinal(base64Decoder.decode(encrypted.data)).toString(Charsets.UTF_8)
        } catch (_: AEADBadTagException) {
            throw WrongPasswordException()
        }
    }

    private fun deriveKey(password: String, salt: ByteArray, iterations: Int = KDF_ITERATIONS): SecretKeySpec {
        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(password.toByteArray(Charsets.UTF_8), salt, iterations)
        return SecretKeySpec((generator.generateDerivedParameters(256) as KeyParameter).key, "AES")
    }

    private val base64 = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    private fun randomBytes(size: Int) = ByteArray(size).also(SecureRandom()::nextBytes)
}
