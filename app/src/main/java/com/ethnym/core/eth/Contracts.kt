package com.ethnym.core.eth

import org.web3j.abi.FunctionEncoder
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.Address
import org.web3j.abi.datatypes.Bool
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.DynamicBytes
import org.web3j.abi.datatypes.DynamicStruct
import org.web3j.abi.datatypes.Function
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String
import org.web3j.abi.datatypes.generated.Uint256
import org.web3j.abi.datatypes.generated.Uint8
import org.web3j.utils.Numeric
import java.math.BigInteger

/** Calldata for the ERC-20 and ERC-721 functions the wallet calls, built with web3j's ABI encoder. */
object Erc20 {
    fun transfer(to: String, amount: BigInteger): String =
        FunctionEncoder.encode(Function("transfer", listOf(Address(to), Uint256(amount)), emptyList()))

    fun balanceOf(owner: String): String =
        FunctionEncoder.encode(Function("balanceOf", listOf(Address(owner)), emptyList()))

    val name: String = FunctionEncoder.encode(Function("name", emptyList(), emptyList()))
    val symbol: String = FunctionEncoder.encode(Function("symbol", emptyList(), emptyList()))
    val decimals: String = FunctionEncoder.encode(Function("decimals", emptyList(), emptyList()))

    fun decodeUint(returnData: ByteArray): BigInteger? = decodeSingle(returnData, uint256Ref)?.value as BigInteger?

    fun decodeDecimals(returnData: ByteArray): Int? = (decodeSingle(returnData, uint8Ref)?.value as BigInteger?)?.toInt()

    /**
     * Decodes a `string` return value. Some early tokens (MKR, SAI) return `bytes32` instead,
     * which is accepted as a fallback.
     */
    fun decodeString(returnData: ByteArray): String? {
        if (returnData.size == 32) {
            return returnData.takeWhile { it != 0.toByte() }.toByteArray().toString(Charsets.UTF_8).ifEmpty { null }
        }
        return decodeSingle(returnData, stringRef)?.value as String?
    }

    private val uint256Ref = object : TypeReference<Uint256>() {}
    private val uint8Ref = object : TypeReference<Uint8>() {}
    private val stringRef = object : TypeReference<Utf8String>() {}
}

object Erc721 {
    fun safeTransferFrom(from: String, to: String, tokenId: BigInteger): String = FunctionEncoder.encode(
        Function("safeTransferFrom", listOf(Address(from), Address(to), Uint256(tokenId)), emptyList()),
    )

    fun ownerOf(tokenId: BigInteger): String =
        FunctionEncoder.encode(Function("ownerOf", listOf(Uint256(tokenId)), emptyList()))

    fun balanceOf(owner: String): String = Erc20.balanceOf(owner)

    fun tokenOfOwnerByIndex(owner: String, index: BigInteger): String = FunctionEncoder.encode(
        Function("tokenOfOwnerByIndex", listOf(Address(owner), Uint256(index)), emptyList()),
    )

    fun decodeAddress(returnData: ByteArray): String? =
        (decodeSingle(returnData, object : TypeReference<Address>() {}) as Address?)?.toString()
}

/**
 * Multicall3's `aggregate3`, so many read calls go out as a single `eth_call`, the way wagmi
 * batches them in the web wallet. Deployed at the same address on every major chain.
 */
object Multicall3 {
    const val ADDRESS = "0xcA11bde05977b3631167028862bE2a173976CA11"

    data class Call(val target: String, val callData: String)

    /** [success] is false when the call reverted, or when it was never answered ([delivered] false). */
    data class Result(val success: Boolean, val returnData: ByteArray, val delivered: Boolean = true)

    class Call3(target: Address, allowFailure: Bool, callData: DynamicBytes) :
        DynamicStruct(target, allowFailure, callData)

    class Call3Result(val success: Bool, val returnData: DynamicBytes) : DynamicStruct(success, returnData)

    fun encodeAggregate3(calls: List<Call>): String = FunctionEncoder.encode(
        Function(
            "aggregate3",
            listOf(
                DynamicArray(
                    Call3::class.java,
                    calls.map { Call3(Address(it.target), Bool(true), DynamicBytes(Numeric.hexStringToByteArray(it.callData))) },
                ),
            ),
            emptyList(),
        ),
    )

    @Suppress("UNCHECKED_CAST")
    fun decodeAggregate3(returnData: String): List<Result> {
        val ref = object : TypeReference<DynamicArray<Call3Result>>() {}
        val decoded = FunctionReturnDecoder.decode(returnData, listOf(ref as TypeReference<Type<*>>))
        val results = (decoded.single() as DynamicArray<Call3Result>).value
        return results.map { Result(it.success.value, it.returnData.value) }
    }
}

@Suppress("UNCHECKED_CAST")
private fun decodeSingle(returnData: ByteArray, ref: TypeReference<out Type<*>>): Type<*>? {
    if (returnData.isEmpty()) return null
    return runCatching {
        FunctionReturnDecoder.decode(Numeric.toHexString(returnData), listOf(ref as TypeReference<Type<*>>)).firstOrNull()
    }.getOrNull()
}
