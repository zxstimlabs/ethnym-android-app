package com.ethnym.data.wallet

import androidx.datastore.core.DataStore
import com.ethnym.core.crypto.KeystoreCrypto
import com.ethnym.data.AppJson
import com.ethnym.data.model.ViewOnlyWallet
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.model.WalletVault
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewOnlyWalletTest {

    private val keystore = WalletKeystore(
        crypto = KeystoreCrypto(
            cipher = "aes-128-ctr",
            ciphertext = "00",
            cipherparams = KeystoreCrypto.CipherParams(iv = "00"),
            kdf = "pbkdf2",
            kdfparams = KeystoreCrypto.KdfParams(c = 1, dklen = 32, prf = "hmac-sha256", salt = "00"),
            mac = "00",
        ),
        id = "keystore-id",
        meta = WalletKeystore.Meta(type = WalletKeystore.CURRENT_META_TYPE, note = WalletKeystore.META_NOTE),
        name = "Main",
        address = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
    )

    private val watched = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045"

    @Test
    fun vault_savedBeforeViewOnlyWalletsStillLoads() {
        val json = AppJson.default.encodeToString(WalletVault.serializer(), WalletVault(listOf(keystore), activeWalletId = keystore.id))
            .let { JsonObject(AppJson.default.parseToJsonElement(it).jsonObject - "viewOnlyWallets").toString() }
        val vault = AppJson.default.decodeFromString(WalletVault.serializer(), json)
        assertEquals(emptyList<ViewOnlyWallet>(), vault.viewOnlyWallets)
        assertEquals(keystore, vault.active)
    }

    @Test
    fun keystoreExport_isUnchanged() {
        val keys = AppJson.default.encodeToJsonElement(WalletKeystore.serializer(), keystore).jsonObject.keys
        assertEquals(setOf("crypto", "id", "version", "meta", "name", "address"), keys)
    }

    @Test
    fun addViewOnly_listsAfterKeystoresAndSelectsWhenNoneSelected() = runTest {
        val repository = WalletRepository(FakeDataStore(WalletVault(listOf(keystore))))
        val added = repository.addViewOnly("vitalik.eth", watched)
        val vault = repository.vault.first()
        assertEquals(listOf(keystore, added), vault.allWallets)
        assertEquals(added, repository.activeWallet.first())
    }

    @Test
    fun addViewOnly_rejectsAnAddressAlreadyAdded() = runTest {
        val repository = WalletRepository(FakeDataStore(WalletVault(listOf(keystore))))
        repository.addViewOnly("vitalik.eth", watched)
        assertTrue(runCatching { repository.addViewOnly("Again", watched.lowercase()) }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.addViewOnly("Mine", keystore.address) }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(1, repository.vault.first().viewOnlyWallets.size)
    }

    @Test
    fun removeViewOnly_deselectsIt() = runTest {
        val repository = WalletRepository(FakeDataStore(WalletVault(listOf(keystore))))
        val added = repository.addViewOnly("vitalik.eth", watched)
        repository.setActive(added.id)
        repository.removeViewOnly(added)
        val vault = repository.vault.first()
        assertTrue(vault.viewOnlyWallets.isEmpty())
        assertEquals(listOf(keystore), vault.wallets)
        assertNull(vault.activeWalletId)
    }

    private class FakeDataStore(initial: WalletVault) : DataStore<WalletVault> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<WalletVault> = state

        override suspend fun updateData(transform: suspend (t: WalletVault) -> WalletVault): WalletVault =
            transform(state.value).also { state.value = it }
    }
}
