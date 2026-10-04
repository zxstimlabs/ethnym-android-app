package com.ethnym.core.eth

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.math.BigInteger

/**
 * A transaction pasted as JSON into the Sign tab (the shape wagmi's `prepareTransactionRequest`
 * produces). Quantities are hex strings, `chainId` and `nonce` are numbers.
 */
data class PreparedTransaction(
    val to: String,
    val chainId: Long,
    val from: String? = null,
    val value: BigInteger? = null,
    val data: String? = null,
    val type: String? = null,
    val gas: BigInteger? = null,
    val nonce: BigInteger? = null,
    val maxFeePerGas: BigInteger? = null,
    val maxPriorityFeePerGas: BigInteger? = null,
)

sealed interface TransactionJson {
    data class Valid(val transaction: PreparedTransaction) : TransactionJson

    data class Invalid(val message: String) : TransactionJson
}

/** Same checks and messages as the web wallet's `validateTransaction`. */
fun validateTransactionJson(value: String): TransactionJson {
    if (value.isEmpty()) return TransactionJson.Invalid("Please enter the transaction JSON")
    val element = try {
        Json.parseToJsonElement(value)
    } catch (_: SerializationException) {
        return TransactionJson.Invalid("Invalid JSON format")
    }
    val obj = element as? JsonObject ?: return TransactionJson.Invalid("Missing 'to' address")
    val to = (obj["to"] as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotEmpty() }?.content
        ?: return TransactionJson.Invalid("Missing 'to' address")
    val chainId = (obj["chainId"] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull
        ?: return TransactionJson.Invalid("Missing or invalid 'chainId' (must be a number)")
    return try {
        TransactionJson.Valid(
            PreparedTransaction(
                to = to,
                chainId = chainId,
                from = obj.string("from") ?: obj.string("account"),
                value = obj.quantity("value"),
                data = obj.string("data"),
                type = obj.string("type"),
                gas = obj.quantity("gas"),
                nonce = obj.quantity("nonce"),
                maxFeePerGas = obj.quantity("maxFeePerGas"),
                maxPriorityFeePerGas = obj.quantity("maxPriorityFeePerGas"),
            ),
        )
    } catch (e: NumberFormatException) {
        TransactionJson.Invalid(e.message ?: "Invalid number")
    }
}

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

/** A hex string (`"0x5208"`), a decimal string or a JSON number. */
private fun JsonObject.quantity(key: String): BigInteger? {
    val element: JsonElement = this[key] ?: return null
    val primitive = element as? JsonPrimitive ?: throw NumberFormatException("'$key' must be a number or hex string")
    val text = primitive.content
    if (text == "null") return null
    return when {
        text.startsWith("0x") || text.startsWith("0X") -> BigInteger(text.drop(2).ifEmpty { "0" }, 16)
        else -> text.toBigIntegerOrNull() ?: throw NumberFormatException("'$key' must be a number or hex string")
    }
}
