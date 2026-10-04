package com.ethnym.data.assets

import com.ethnym.core.eth.Addresses
import com.ethnym.data.AppJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Catches typos in the bundled lists, such as an address with an extra hex digit. */
class BundledAssetListsTest {

    @Test
    fun tokenList_addressesAreValid() = assertValidAddresses("token-list.json", "tokens")

    @Test
    fun nftList_addressesAreValid() = assertValidAddresses("nft-list.json", "collections")

    private fun assertValidAddresses(file: String, key: String) {
        val entries = AppJson.default.parseToJsonElement(File("src/main/assets/$file").readText()).jsonObject.getValue(key).jsonArray
        val addresses = entries.map { it.jsonObject.getValue("address").jsonPrimitive.content }
        assertTrue("$file has no entries", addresses.isNotEmpty())
        assertEquals("Invalid addresses in $file", emptyList<String>(), addresses.filterNot(Addresses::isValid))
    }
}
