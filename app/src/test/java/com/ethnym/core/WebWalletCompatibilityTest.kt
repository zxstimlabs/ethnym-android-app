package com.ethnym.core

import com.ethnym.core.crypto.BackupCipher
import com.ethnym.core.crypto.KeystoreCrypto
import com.ethnym.core.crypto.Mnemonics
import com.ethnym.core.crypto.SecretKeystore
import com.ethnym.core.crypto.WrongPasswordException
import com.ethnym.core.eth.Erc20
import com.ethnym.core.eth.Erc721
import com.ethnym.core.eth.GasFees
import com.ethnym.core.eth.Multicall3
import com.ethnym.core.eth.UnsignedTransaction
import com.ethnym.core.eth.sign
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

/**
 * Vectors generated with the web wallet's own libraries (viem 2.45, ox 0.12), so these tests pin
 * byte-for-byte compatibility: same addresses, same keystores, same signed transactions.
 */
class WebWalletCompatibilityTest {

    private val mnemonic = "test test test test test test test test test test test junk"

    @Test
    fun mnemonic_derivesSameAddressAsViem() {
        assertEquals("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", Mnemonics.address(mnemonic))
        assertEquals(
            "0x58A57ed9d8d624cBD12e2C467D34787555bB1b25",
            Mnemonics.address("legal winner thank year wave sausage worth useful legal winner thank yellow"),
        )
    }

    @Test
    fun mnemonic_validation() {
        assertTrue(Mnemonics.isValid(mnemonic))
        assertFalse(Mnemonics.isValid("test test test test test test test test test test test test"))
        assertFalse(Mnemonics.isValid("not a phrase"))
        assertEquals(12, Mnemonics.generate().split(" ").size)
        assertTrue(Mnemonics.isValid(Mnemonics.generate()))
        assertEquals(mnemonic, Mnemonics.normalizeInput("  Test test\ntest  test test test test test test test test JUNK "))
    }

    private val oxKeystore = KeystoreCrypto(
        cipher = "aes-128-ctr",
        ciphertext = "3237e0165512771ba7b66d52ed520954b56c0892d22c658ba6f698418a08ab649f78a3bd94b474b987a52c352b000a1dcc806d22ff2c22bfef907f",
        cipherparams = KeystoreCrypto.CipherParams(iv = "22222222222222222222222222222222"),
        kdf = "pbkdf2",
        kdfparams = KeystoreCrypto.KdfParams(c = 262144, dklen = 32, prf = "hmac-sha256", salt = "11".repeat(32)),
        mac = "cbaea87246be194f001c49a24de715dc417a5a47923dda0a56d025a73e360f0a",
    )

    @Test
    fun keystore_decryptsOxKeystore() {
        val secret = SecretKeystore.decrypt(oxKeystore, "correct horse")
        assertEquals(mnemonic, secret.toString(Charsets.UTF_8))
    }

    @Test
    fun keystore_encryptsIdenticallyToOx() {
        val encrypted = SecretKeystore.encrypt(
            mnemonic.toByteArray(),
            "correct horse",
            salt = ByteArray(32) { 0x11 },
            iv = ByteArray(16) { 0x22 },
        )
        assertEquals(oxKeystore, encrypted)
    }

    @Test(expected = WrongPasswordException::class)
    fun keystore_rejectsWrongPassword() {
        SecretKeystore.decrypt(oxKeystore, "wrong")
    }

    @Test
    fun backup_decryptsWebCryptoOutput() {
        val plaintext = BackupCipher.decrypt(
            BackupCipher.Encrypted(
                kdfSalt = "MzMzMzMzMzMzMzMzMzMzMw==",
                iv = "RERERERERERERERE",
                data = "UQ/u1vtZDVBj3QY+VXE7ludClAB/iY/7HYMyT+FUVbuK",
            ),
            "backup pw ✓",
        )
        assertEquals("{\"hello\":\"world\"}", plaintext)
    }

    @Test
    fun backup_roundTrips() {
        val encrypted = BackupCipher.encrypt("payload", "pw")
        assertEquals("payload", BackupCipher.decrypt(encrypted, "pw"))
    }

    @Test
    fun eip1559_signsLikeViem() {
        val credentials = Mnemonics.credentials(mnemonic)
        val signed = UnsignedTransaction(
            chainId = 1,
            nonce = BigInteger.valueOf(7),
            gasLimit = BigInteger.valueOf(21000),
            to = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
            value = BigInteger("12300000000000000"),
            data = "",
            fees = GasFees.Eip1559(maxFeePerGas = BigInteger("31500000000"), maxPriorityFeePerGas = BigInteger("1250000000")),
        ).sign(credentials)
        assertEquals(
            "0x02f8720107844a817c808507558bdb008252089470997970c51812dc3a010c7d01b50e0d17dc79c8872bb2c8eabcc00080c080a0059d8e6f5fc19acb26afdbffff8078b5519d8971fe8562f08c0169752f98989da01800e0470c093c6035eb1561fed1eac1d26d735a20b4180ba12092744604f167",
            signed.raw,
        )
    }

    @Test
    fun legacy_signsLikeViem() {
        val credentials = Mnemonics.credentials(mnemonic)
        val signed = UnsignedTransaction(
            chainId = 1,
            nonce = BigInteger.ZERO,
            gasLimit = BigInteger.valueOf(65000),
            to = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            value = BigInteger.ZERO,
            data = Erc20.transfer("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", BigInteger.valueOf(123456789)),
            fees = GasFees.Legacy(gasPrice = BigInteger("12345678901")),
        ).sign(credentials)
        assertEquals(
            "0xf8a9808502dfdc1c3582fde894a0b86991c6218b36c1d19d4a2e9eb0ce3606eb4880b844a9059cbb00000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c800000000000000000000000000000000000000000000000000000000075bcd1525a08ffd6e81ad66963c62347933c3828180dbe487c80171d6003fbe6abcc5a5e175a06de1a3330a4aaeecefce506c0b5b3b2a91784eb7c6fdb0e2fd87ed89afb5493d",
            signed.raw,
        )
    }

    @Test
    fun calldata_matchesViem() {
        assertEquals(
            "0xa9059cbb00000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c800000000000000000000000000000000000000000000000000000000075bcd15",
            Erc20.transfer("0x70997970C51812dc3A010C7d01b50e0d17dc79C8", BigInteger.valueOf(123456789)),
        )
        assertEquals(
            "0x42842e0e000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb9226600000000000000000000000070997970c51812dc3a010c7d01b50e0d17dc79c8000000000000000000000000000000000000000000000000000000000000002a",
            Erc721.safeTransferFrom(
                "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266",
                "0x70997970C51812dc3A010C7d01b50e0d17dc79C8",
                BigInteger.valueOf(42),
            ),
        )
    }

    @Test
    fun multicall_encodesLikeViem() {
        val encoded = Multicall3.encodeAggregate3(
            listOf(
                Multicall3.Call("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48", Erc20.balanceOf("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266")),
                Multicall3.Call("0xdAC17F958D2ee523a2206206994597C13D831ec7", Erc20.name),
            ),
        )
        assertEquals(
            "0x82ad56cb0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000100000000000000000000000000a0b86991c6218b36c1d19d4a2e9eb0ce3606eb4800000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000002470a08231000000000000000000000000f39fd6e51aad88f6f4ce6ab8827279cfffb9226600000000000000000000000000000000000000000000000000000000000000000000000000000000dac17f958d2ee523a2206206994597c13d831ec700000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000060000000000000000000000000000000000000000000000000000000000000000406fdde0300000000000000000000000000000000000000000000000000000000",
            encoded,
        )
    }

    @Test
    fun multicall_decodesLikeViem() {
        val results = Multicall3.decodeAggregate3(
            "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000004000000000000000000000000000000000000000000000000000000000000000c000000000000000000000000000000000000000000000000000000000000000010000000000000000000000000000000000000000000000000000000000000040000000000000000000000000000000000000000000000000000000000000002000000000000000000000000000000000000000000000000000000000000f4240000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000400000000000000000000000000000000000000000000000000000000000000000",
        )
        assertEquals(2, results.size)
        assertTrue(results[0].success)
        assertEquals(BigInteger.valueOf(1_000_000), Erc20.decodeUint(results[0].returnData))
        assertFalse(results[1].success)
        assertArrayEquals(ByteArray(0), results[1].returnData)
    }
}
