package com.ethnym.core

import com.ethnym.core.eth.Addresses
import com.ethnym.core.eth.TransactionJson
import com.ethnym.core.eth.Units
import com.ethnym.core.eth.parseQrAddress
import com.ethnym.core.eth.validateTransactionJson
import com.ethnym.data.settings.validateRpcUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/** Cases ported from the web wallet's test suite (qr, settings, validate-transaction). */
class WebWalletBehaviourTest {

    private val lower = "0x1234567890abcdef1234567890abcdef12345678"
    private val checksummed = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"

    @Test
    fun qr_acceptsAddressFormats() {
        assertEquals(lower, parseQrAddress(lower))
        assertEquals(checksummed, parseQrAddress(checksummed))
        assertEquals("0x" + lower.drop(2).uppercase(), parseQrAddress("0x" + lower.drop(2).uppercase()))
        assertEquals(lower, parseQrAddress("  $lower  "))
        listOf("eth", "arb1", "base", "matic").forEach { assertEquals(lower, parseQrAddress("$it:$lower")) }
        listOf("1", "137", "8453").forEach { assertEquals(lower, parseQrAddress("eip155:$it:$lower")) }
        assertEquals(lower, parseQrAddress("ethereum:$lower@1"))
        assertEquals(lower, parseQrAddress("ethereum:$lower/transfer?value=1000000"))
        assertEquals(lower, parseQrAddress("ethereum:$lower@1/transfer?uint256=1000"))
        assertEquals(lower, parseQrAddress("ethereum:$lower"))
        assertEquals(lower, parseQrAddress("  eth:$lower  "))
        assertEquals(lower, parseQrAddress("  eip155:1:$lower  "))
    }

    @Test
    fun qr_rejectsNonAddresses() {
        listOf(
            lower.dropLast(1),
            lower + "a",
            "",
            "not-an-address",
            "0x1234567890abcdef",
            "${lower}FF",
            "0xGGGG567890abcdef1234567890abcdef12345678",
            "vitalik.eth",
            "   ",
            "0x",
            lower.drop(2),
            "bc1qar0srrr7xfkvy5l643lydnw9re59gtzzwf5mdq",
        ).forEach { assertNull(it, parseQrAddress(it)) }
    }

    @Test
    fun rpcUrl_validation() {
        assertNull(validateRpcUrl("https://eth-mainnet.g.alchemy.com/v2/key"))
        assertNull(validateRpcUrl("http://localhost:8545"))
        assertNull(validateRpcUrl("https://rpc.example.com/v2?token=abc"))
        assertNull(validateRpcUrl("https://rpc.example.com/"))
        assertNull(validateRpcUrl("  https://rpc.example.com  "))
        assertEquals("RPC URL is required", validateRpcUrl(""))
        assertEquals("RPC URL is required", validateRpcUrl("   "))
        assertEquals("RPC URL must use http or https", validateRpcUrl("ws://rpc.example.com"))
        assertEquals("RPC URL must use http or https", validateRpcUrl("wss://rpc.example.com"))
        assertEquals("Invalid URL", validateRpcUrl("not a url"))
    }

    @Test
    fun transactionJson_acceptsValidTransactions() {
        val valid = validateTransactionJson("""{"to":"$lower","chainId":1}""") as TransactionJson.Valid
        assertEquals(lower, valid.transaction.to)
        assertEquals(1L, valid.transaction.chainId)

        val full = validateTransactionJson(
            """{"to":"$lower","chainId":137,"value":"0x38D7EA4C68000","gas":"0x5208","nonce":42}""",
        ) as TransactionJson.Valid
        assertEquals(BigInteger("1000000000000000"), full.transaction.value)
        assertEquals(BigInteger.valueOf(21000), full.transaction.gas)
        assertEquals(BigInteger.valueOf(42), full.transaction.nonce)

        listOf(
            """{"to":"$lower","chainId":1,"type":"eip1559"}""",
            """{"to":"$lower","chainId":1,"maxFeePerGas":"0x3B9ACA00"}""",
            """{"to":"$lower","chainId":1,"data":"0xabcdef"}""",
            """{"to":"$lower","chainId":1,"from":"$lower"}""",
            """{"to":"$lower","chainId":8453}""",
        ).forEach { assertTrue(it, validateTransactionJson(it) is TransactionJson.Valid) }
    }

    @Test
    fun transactionJson_rejectsWithWebWalletMessages() {
        fun message(input: String) = (validateTransactionJson(input) as TransactionJson.Invalid).message
        assertEquals("Please enter the transaction JSON", message(""))
        assertEquals("Invalid JSON format", message("{not json}"))
        assertEquals("Missing 'to' address", message("[1, 2, 3]"))
        assertEquals("Missing 'to' address", message("\"just a string\""))
        assertEquals("Missing 'to' address", message("42"))
        assertEquals("Missing 'to' address", message("""{"chainId":1}"""))
        assertEquals("Missing 'to' address", message("""{"to":12345,"chainId":1}"""))
        assertEquals("Missing 'to' address", message("""{"to":null,"chainId":1}"""))
        assertEquals("Missing 'to' address", message("""{"to":{},"chainId":1}"""))
        listOf("""{"to":"$lower"}""", """{"to":"$lower","chainId":"1"}""", """{"to":"$lower","chainId":null}""", """{"to":"$lower","chainId":true}""")
            .forEach { assertEquals("Missing or invalid 'chainId' (must be a number)", message(it)) }
        assertTrue(validateTransactionJson("null") is TransactionJson.Invalid)
        assertTrue(validateTransactionJson("   ") is TransactionJson.Invalid)
    }

    @Test
    fun units_parseAndFormatLikeViem() {
        assertEquals(BigInteger("1500000000000000000"), Units.parseEther("1.5"))
        assertEquals(BigInteger("500000000000000000"), Units.parseEther(".5"))
        assertEquals(BigInteger.valueOf(1_234_567), Units.parse("1.234567", 6))
        assertEquals("1", Units.formatEther(BigInteger("1000000000000000000")))
        assertEquals("0", Units.formatEther(BigInteger.ZERO))
        assertEquals("0.000000000000000001", Units.formatEther(BigInteger.ONE))
        assertEquals("12.345678901", Units.formatGwei(BigInteger("12345678901")))
        listOf("", "abc", "1e18", "-1", "1.2.3", ".").forEach { input ->
            assertThrows(input, IllegalArgumentException::class.java) { Units.parseEther(input) }
        }
        assertThrows(IllegalArgumentException::class.java) { Units.parse("1.1234567", 6) }
    }

    @Test
    fun addresses_followViemRules() {
        assertTrue(Addresses.isValid(lower))
        assertTrue(Addresses.isValid(checksummed))
        assertFalse(Addresses.isValid(checksummed.replace('F', 'f').replaceFirst('d', 'D')))
        assertFalse(Addresses.isValid("0x123"))
        assertEquals(checksummed, Addresses.checksum(checksummed.lowercase()))
        assertTrue(Addresses.isEnsName("vitalik.eth"))
        assertFalse(Addresses.isEnsName(".eth"))
    }
}
