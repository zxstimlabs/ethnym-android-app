package com.ethnym.data.files

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Reads and writes files the user picked with the system file picker (Storage Access Framework). */
@Singleton
class DocumentRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openInputStream(uri)) { "Could not open the file" }
            .bufferedReader().use { it.readText() }
    }

    suspend fun displayName(uri: Uri): String? = withContext(Dispatchers.IO) {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    suspend fun writeText(uri: Uri, text: String) = withContext(Dispatchers.IO) {
        requireNotNull(context.contentResolver.openOutputStream(uri, "wt")) { "Could not open the file" }
            .bufferedWriter().use { it.write(text) }
    }
}
