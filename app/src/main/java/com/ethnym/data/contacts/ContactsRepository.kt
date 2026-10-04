package com.ethnym.data.contacts

import androidx.datastore.core.DataStore
import com.ethnym.data.model.Contact
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** The address book. */
@Singleton
class ContactsRepository @Inject constructor(
    private val dataStore: DataStore<List<Contact>>,
) {
    val contacts: Flow<List<Contact>> = dataStore.data

    suspend fun add(contact: Contact) {
        dataStore.updateData { it + contact }
    }

    suspend fun delete(id: String) {
        dataStore.updateData { contacts -> contacts.filterNot { it.id == id } }
    }
}
