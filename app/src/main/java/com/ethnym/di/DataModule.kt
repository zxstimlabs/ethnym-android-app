package com.ethnym.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.ethnym.data.AppJson
import com.ethnym.data.model.ActivityRecord
import com.ethnym.data.model.Contact
import com.ethnym.data.model.CustomAssets
import com.ethnym.data.model.WalletSettings
import com.ethnym.data.model.WalletVault
import com.ethnym.data.settings.DataStoreUserPreferencesRepository
import com.ethnym.data.settings.UserPreferencesRepository
import com.ethnym.data.storage.EncryptedJsonSerializer
import com.ethnym.data.storage.KeystoreCipher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindUserPreferencesRepository(
        impl: DataStoreUserPreferencesRepository,
    ): UserPreferencesRepository

    companion object {
        @Provides
        @Singleton
        fun providePreferencesDataStore(
            @ApplicationContext context: Context,
        ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile("user_preferences") },
        )

        @Provides
        @Singleton
        fun provideKeystoreCipher(): KeystoreCipher = KeystoreCipher()

        // Wallet data is JSON encrypted with an Android Keystore key, one file per area.

        @Provides
        @Singleton
        fun provideWalletVaultStore(@ApplicationContext context: Context, cipher: KeystoreCipher): DataStore<WalletVault> =
            encryptedStore(context, cipher, "wallets", WalletVault(), WalletVault.serializer())

        @Provides
        @Singleton
        fun provideContactsStore(@ApplicationContext context: Context, cipher: KeystoreCipher): DataStore<List<Contact>> =
            encryptedStore(context, cipher, "contacts", emptyList(), ListSerializer(Contact.serializer()))

        @Provides
        @Singleton
        fun provideActivityStore(@ApplicationContext context: Context, cipher: KeystoreCipher): DataStore<List<ActivityRecord>> =
            encryptedStore(context, cipher, "activity", emptyList(), ListSerializer(ActivityRecord.serializer()))

        @Provides
        @Singleton
        fun provideSettingsStore(@ApplicationContext context: Context, cipher: KeystoreCipher): DataStore<WalletSettings> =
            encryptedStore(context, cipher, "wallet_settings", WalletSettings(), WalletSettings.serializer())

        @Provides
        @Singleton
        fun provideCustomAssetsStore(@ApplicationContext context: Context, cipher: KeystoreCipher): DataStore<CustomAssets> =
            encryptedStore(context, cipher, "custom_assets", CustomAssets(), CustomAssets.serializer())

        private fun <T> encryptedStore(
            context: Context,
            cipher: KeystoreCipher,
            name: String,
            default: T,
            serializer: KSerializer<T>,
        ): DataStore<T> = DataStoreFactory.create(
            serializer = EncryptedJsonSerializer(default, serializer, cipher, AppJson.default),
            produceFile = { context.dataStoreFile("$name.json.enc") },
        )
    }
}
