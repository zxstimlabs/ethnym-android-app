package com.ethnym.data.backup

import com.ethnym.core.crypto.BackupCipher
import com.ethnym.core.crypto.KeystoreCrypto
import com.ethnym.core.crypto.SecretKeystore
import com.ethnym.data.AppJson
import com.ethnym.data.activity.ActivityRepository
import com.ethnym.data.contacts.ContactsRepository
import com.ethnym.data.model.ActivityRecord
import com.ethnym.data.model.Contact
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.model.WalletSettings
import com.ethnym.data.settings.SettingsRepository
import com.ethnym.data.wallet.WalletCrypto
import com.ethnym.data.wallet.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The full-device backup file, in the web wallet's `um-wallet-backup` v1 format. Self-describing:
 * KDF parameters and IV sit in plaintext next to the ciphertext, so any PBKDF2 + AES-GCM
 * implementation can decrypt it.
 */
@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val version: Int = 1,
    /** ISO 8601. */
    val createdAt: String,
    val encryption: Encryption,
    /** Base64 AES-GCM ciphertext of a JSON [BackupPayload]. */
    val data: String,
) {
    @Serializable
    data class Encryption(
        val kdf: String = "PBKDF2",
        val kdfHash: String = "SHA-256",
        val kdfIterations: Int,
        /** Base64. */
        val kdfSalt: String,
        val algorithm: String = "AES-GCM-256",
        /** Base64. */
        val iv: String,
    )

    companion object {
        const val FORMAT = "um-wallet-backup"
    }
}

@Serializable
data class BackupPayload(
    val wallets: List<WalletKeystore>,
    val activeWalletAddress: String?,
    val contacts: List<Contact>,
    val settings: WalletSettings,
    val activity: List<ActivityRecord>,
)

@Singleton
class BackupRepository @Inject constructor(
    private val walletRepository: WalletRepository,
    private val contactsRepository: ContactsRepository,
    private val settingsRepository: SettingsRepository,
    private val activityRepository: ActivityRepository,
) {
    data class Summary(val wallets: Int, val contacts: Int, val activity: Int)

    val summary: Flow<Summary> = combine(
        walletRepository.wallets,
        contactsRepository.contacts,
        activityRepository.all,
    ) { wallets, contacts, activity -> Summary(wallets.size, contacts.size, activity.size) }

    /** Encrypts everything into backup file contents. Runs PBKDF2 (600,000 rounds). */
    suspend fun export(password: String): String {
        val vault = walletRepository.vault.first()
        val payload = BackupPayload(
            wallets = vault.wallets,
            activeWalletAddress = vault.wallets.find { it.id == vault.activeWalletId }?.address,
            contacts = contactsRepository.contacts.first(),
            settings = settingsRepository.settings.first(),
            activity = activityRepository.all.first(),
        )
        val encrypted = withContext(Dispatchers.Default) {
            BackupCipher.encrypt(AppJson.default.encodeToString(BackupPayload.serializer(), payload), password)
        }
        val file = BackupFile(
            createdAt = Instant.now().toString(),
            encryption = BackupFile.Encryption(
                kdfIterations = BackupCipher.KDF_ITERATIONS,
                kdfSalt = encrypted.kdfSalt,
                iv = encrypted.iv,
            ),
            data = encrypted.data,
        )
        return AppJson.pretty.encodeToString(BackupFile.serializer(), file)
    }

    fun backupFileName(): String = "ethnym-wallet-backup-${LocalDate.now()}.json"
}

/** The standalone keystore tool: any phrase to a keystore, any keystore back to its phrase. */
object KeystoreTool {

    /** Same shape the web wallet's tool produces (`meta.type` = "secret-phrase", no `umVersion`). */
    fun encrypt(name: String, password: String, phrase: String): WalletKeystore =
        WalletCrypto.encryptPhrase(name, password, phrase, metaType = "secret-phrase", umVersion = null)

    /** Accepts any keystore v3 JSON that encrypts a phrase. @throws WrongPasswordException */
    fun decrypt(keystoreJson: String, password: String): String {
        val crypto = AppJson.default.decodeFromJsonElement(
            KeystoreCrypto.serializer(),
            requireNotNull(AppJson.default.parseToJsonElement(keystoreJson).jsonObject["crypto"]) { "No \"crypto\" field in keystore" },
        )
        return SecretKeystore.decrypt(crypto, password).toString(Charsets.UTF_8)
    }
}
