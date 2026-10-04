package com.ethnym.data.activity

import androidx.datastore.core.DataStore
import com.ethnym.data.model.ActivityRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Transactions this app sent, recorded on confirmation. Local only, as in the web wallet. */
@Singleton
class ActivityRepository @Inject constructor(
    private val dataStore: DataStore<List<ActivityRecord>>,
) {
    val all: Flow<List<ActivityRecord>> = dataStore.data

    /** Transactions sent from [address], newest first. */
    fun outgoing(address: String): Flow<List<ActivityRecord>> = dataStore.data.map { records ->
        records.filter { it.from.equals(address, ignoreCase = true) }.sortedByDescending { it.timestamp }
    }

    suspend fun record(record: ActivityRecord) {
        dataStore.updateData { records ->
            val id = (records.maxOfOrNull { it.id ?: 0 } ?: 0) + 1
            records + record.copy(id = id, timestamp = System.currentTimeMillis())
        }
    }
}
