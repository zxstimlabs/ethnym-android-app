package com.ethnym.data.wallet

import androidx.datastore.core.DataStore
import com.ethnym.core.crypto.Mnemonics
import com.ethnym.core.crypto.SecretKeystore
import com.ethnym.core.crypto.WrongPasswordException
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.model.WalletVault
import com.ethnym.data.model.needsMigration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.web3j.crypto.Credentials
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The wallet list. Each wallet's phrase is encrypted with its own password; every operation
 * that needs the phrase (signing, revealing, deleting) decrypts it on demand and keeps nothing.
 */
@Singleton
class WalletRepository @Inject constructor(
    private val dataStore: DataStore<WalletVault>,
) {
    val vault: Flow<WalletVault> = dataStore.data

    val wallets: Flow<List<WalletKeystore>> = vault.map { it.wallets }

    val activeWallet: Flow<WalletKeystore?> = vault.map { vault -> vault.wallets.find { it.id == vault.activeWalletId } }

    /** Generates a new phrase and stores it encrypted. Selects it when no wallet is selected. */
    suspend fun create(name: String, password: String): WalletKeystore =
        add(withContext(Dispatchers.Default) { WalletCrypto.encryptPhrase(name, password, Mnemonics.generate()) })

    /** @throws IllegalArgumentException when the phrase is not a valid BIP-39 phrase. */
    suspend fun importPhrase(name: String, password: String, phrase: String): WalletKeystore {
        val normalized = Mnemonics.normalizeInput(phrase)
        require(Mnemonics.isValid(normalized)) { "Invalid secret phrase" }
        return add(withContext(Dispatchers.Default) { WalletCrypto.encryptPhrase(name, password, normalized) })
    }

    /**
     * Adds keystores from a file or pasted JSON. A keystore already in the list (same id and
     * address) is skipped; one that only shares its id gets a fresh id.
     */
    suspend fun importKeystores(keystores: List<WalletKeystore>): ImportResult {
        var imported = 0
        var skipped = 0
        dataStore.updateData { vault ->
            val wallets = vault.wallets.toMutableList()
            for (keystore in keystores) {
                when {
                    wallets.any { it.id == keystore.id && it.address.equals(keystore.address, ignoreCase = true) } -> skipped++
                    wallets.any { it.id == keystore.id } -> { wallets += keystore.copy(id = SecretKeystore.newId()); imported++ }
                    else -> { wallets += keystore; imported++ }
                }
            }
            vault.copy(wallets = wallets)
        }
        return ImportResult(imported = imported, skipped = skipped)
    }

    suspend fun setActive(id: String?) {
        dataStore.updateData { it.copy(activeWalletId = id) }
    }

    /** Deletes [wallet] after proving the password unlocks it. */
    suspend fun delete(wallet: WalletKeystore, password: String) {
        unlock(wallet, password)
        dataStore.updateData { vault ->
            vault.copy(
                wallets = vault.wallets.filterNot { it.id == wallet.id && it.address == wallet.address },
                activeWalletId = vault.activeWalletId.takeUnless { it == wallet.id },
            )
        }
    }

    /** @throws WrongPasswordException */
    suspend fun revealPhrase(wallet: WalletKeystore, password: String): String =
        withContext(Dispatchers.Default) { WalletCrypto.decryptPhrase(wallet, password) }

    /**
     * Decrypts the phrase and derives the signing credentials, checking they match the stored
     * address. @throws WrongPasswordException
     */
    suspend fun unlock(wallet: WalletKeystore, password: String): Credentials =
        withContext(Dispatchers.Default) { WalletCrypto.unlock(wallet, password) }

    /** Rewrites outdated metadata. Keys are untouched. */
    suspend fun migrate() {
        dataStore.updateData { vault ->
            vault.copy(
                wallets = vault.wallets.map { wallet ->
                    if (!wallet.needsMigration()) {
                        wallet
                    } else {
                        wallet.copy(
                            meta = wallet.meta.copy(
                                type = WalletKeystore.CURRENT_META_TYPE,
                                umVersion = WalletKeystore.CURRENT_UM_VERSION,
                            ),
                        )
                    }
                },
            )
        }
    }

    private suspend fun add(wallet: WalletKeystore): WalletKeystore {
        dataStore.updateData { vault ->
            vault.copy(wallets = vault.wallets + wallet, activeWalletId = vault.activeWalletId ?: wallet.id)
        }
        return wallet
    }

    data class ImportResult(val imported: Int, val skipped: Int)
}

/** Keystore encryption for wallets, matching the web wallet's create and import flows. */
object WalletCrypto {

    /** Runs PBKDF2 (262,144 rounds): call off the main thread. */
    fun encryptPhrase(
        name: String,
        password: String,
        phrase: String,
        metaType: String = WalletKeystore.CURRENT_META_TYPE,
        umVersion: String? = WalletKeystore.CURRENT_UM_VERSION,
    ): WalletKeystore = WalletKeystore(
        crypto = SecretKeystore.encrypt(phrase.toByteArray(Charsets.UTF_8), password),
        id = SecretKeystore.newId(),
        meta = WalletKeystore.Meta(type = metaType, note = WalletKeystore.META_NOTE, umVersion = umVersion),
        name = name,
        address = Mnemonics.address(phrase),
    )

    /** @throws WrongPasswordException */
    fun decryptPhrase(wallet: WalletKeystore, password: String): String =
        SecretKeystore.decrypt(wallet.crypto, password).toString(Charsets.UTF_8)

    /** @throws WrongPasswordException, also when the phrase does not derive the stored address. */
    fun unlock(wallet: WalletKeystore, password: String): Credentials {
        val credentials = Mnemonics.credentials(decryptPhrase(wallet, password))
        if (!credentials.address.equals(wallet.address, ignoreCase = true)) throw WrongPasswordException()
        return credentials
    }
}
