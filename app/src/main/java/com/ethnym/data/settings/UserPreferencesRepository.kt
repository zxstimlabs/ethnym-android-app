package com.ethnym.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

data class UserPreferences(
    val hideBalances: Boolean = false,
)

interface UserPreferencesRepository {
    val userPreferences: Flow<UserPreferences>

    suspend fun setHideBalances(hide: Boolean)
}

/**
 * Non-sensitive app preferences only. DataStore is plain, unencrypted storage:
 * keys, seeds and anything secret belong in Android Keystore-backed storage, never here.
 */
class DataStoreUserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : UserPreferencesRepository {

    override val userPreferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            UserPreferences(hideBalances = prefs[HIDE_BALANCES] ?: false)
        }

    override suspend fun setHideBalances(hide: Boolean) {
        dataStore.edit { it[HIDE_BALANCES] = hide }
    }

    private companion object {
        val HIDE_BALANCES = booleanPreferencesKey("hide_balances")
    }
}
