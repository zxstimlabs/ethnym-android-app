package com.ethnym.data

import com.ethnym.data.model.WalletKeystore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

@OptIn(ExperimentalSerializationApi::class)
object AppJson {
    /** Tolerates fields from newer versions; omits nulls so exports read like the web wallet's. */
    val default = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    /** Matches `JSON.stringify(value, null, 2)` for files the user saves. */
    val pretty = Json(default) {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

    /** Keystore JSON that holds either one wallet or an array of them, as the web wallet exports. */
    fun parseWallets(text: String): List<WalletKeystore> {
        val element = default.parseToJsonElement(text)
        return if (element is JsonArray) {
            default.decodeFromJsonElement(ListSerializer(WalletKeystore.serializer()), element)
        } else {
            listOf(default.decodeFromJsonElement(WalletKeystore.serializer(), element))
        }
    }
}
