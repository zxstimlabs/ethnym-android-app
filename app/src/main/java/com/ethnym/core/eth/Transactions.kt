package com.ethnym.core.eth

import org.web3j.crypto.Credentials
import org.web3j.crypto.Hash
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.TransactionEncoder
import org.web3j.utils.Numeric
import java.math.BigInteger

/** How a transaction pays for gas. */
sealed interface GasFees {
    /** Type-0 transaction with a fixed gas price (what the gas presets produce). */
    data class Legacy(val gasPrice: BigInteger) : GasFees

    /** EIP-1559 (type-2) transaction. */
    data class Eip1559(val maxFeePerGas: BigInteger, val maxPriorityFeePerGas: BigInteger) : GasFees
}

data class UnsignedTransaction(
    val chainId: Long,
    val nonce: BigInteger,
    val gasLimit: BigInteger,
    val to: String,
    val value: BigInteger,
    val data: String,
    val fees: GasFees,
)

data class SignedTransaction(val raw: String, val hash: String)

/** Signs with web3j: EIP-155 replay protection for legacy transactions, typed envelope for EIP-1559. */
fun UnsignedTransaction.sign(credentials: Credentials): SignedTransaction {
    val signed = when (fees) {
        is GasFees.Legacy -> TransactionEncoder.signMessage(
            RawTransaction.createTransaction(nonce, fees.gasPrice, gasLimit, to, value, data),
            chainId,
            credentials,
        )
        is GasFees.Eip1559 -> TransactionEncoder.signMessage(
            RawTransaction.createTransaction(
                chainId, nonce, gasLimit, to, value, data, fees.maxPriorityFeePerGas, fees.maxFeePerGas,
            ),
            credentials,
        )
    }
    return SignedTransaction(raw = Numeric.toHexString(signed), hash = Numeric.toHexString(Hash.sha3(signed)))
}
