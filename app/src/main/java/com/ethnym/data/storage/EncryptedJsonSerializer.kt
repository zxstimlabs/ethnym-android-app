package com.ethnym.data.storage

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256-GCM with a key that lives in the Android Keystore and never leaves it. Used to wrap
 * every file the wallet persists, on top of the per-wallet password encryption.
 */
class KeystoreCipher(private val alias: String = "ethnym_storage") {

    fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plaintext)
    }

    fun decrypt(blob: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, IV_SIZE))
        return cipher.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    @Synchronized
    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as SecretKey?)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}

/** DataStore serializer that stores [T] as JSON encrypted with [KeystoreCipher]. */
class EncryptedJsonSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
    private val cipher: KeystoreCipher,
    private val json: Json,
) : Serializer<T> {

    override suspend fun readFrom(input: InputStream): T {
        val blob = input.readBytes()
        if (blob.isEmpty()) return defaultValue
        return try {
            json.decodeFromString(serializer, cipher.decrypt(blob).toString(Charsets.UTF_8))
        } catch (e: GeneralSecurityException) {
            throw CorruptionException("Could not decrypt stored data", e)
        } catch (e: SerializationException) {
            throw CorruptionException("Could not parse stored data", e)
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(cipher.encrypt(json.encodeToString(serializer, t).toByteArray(Charsets.UTF_8)))
    }
}
