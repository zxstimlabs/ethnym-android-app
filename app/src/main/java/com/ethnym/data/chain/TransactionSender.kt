package com.ethnym.data.chain

import com.ethnym.core.eth.GasFees
import com.ethnym.core.eth.Mainnet
import com.ethnym.core.eth.SignedTransaction
import com.ethnym.core.eth.UnsignedTransaction
import com.ethnym.core.eth.sign
import com.ethnym.data.model.WalletKeystore
import com.ethnym.data.wallet.WalletRepository
import kotlinx.coroutines.delay
import org.web3j.protocol.core.methods.response.TransactionReceipt
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

/** What to send. Any field left null is filled from the network before signing. */
data class TransactionRequest(
    val to: String,
    val value: BigInteger = BigInteger.ZERO,
    val data: String = "0x",
    val chainId: Long = Mainnet.CHAIN_ID,
    val nonce: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val fees: FeeChoice = FeeChoice.Eip1559(),
)

sealed interface FeeChoice {
    /** Legacy transaction at a fixed gas price, as the Slow/Normal/Fast presets choose. */
    data class GasPrice(val gasPrice: BigInteger) : FeeChoice

    /**
     * EIP-1559. Missing values are estimated the way viem does: the node's priority fee, and a
     * max fee of 1.2 × the latest base fee plus that priority fee.
     */
    data class Eip1559(val maxFeePerGas: BigInteger? = null, val maxPriorityFeePerGas: BigInteger? = null) : FeeChoice
}

/** Unlocks a wallet, fills in, signs, broadcasts and tracks transactions. */
@Singleton
class TransactionSender @Inject constructor(
    private val client: EthereumClient,
    private val walletRepository: WalletRepository,
) {
    /** Signs after filling missing fields from the network. Decrypts the wallet with [password]. */
    suspend fun sign(wallet: WalletKeystore, password: String, request: TransactionRequest): SignedTransaction {
        val credentials = walletRepository.unlock(wallet, password)
        val fees = when (val choice = request.fees) {
            is FeeChoice.GasPrice -> GasFees.Legacy(choice.gasPrice)
            is FeeChoice.Eip1559 -> {
                val priority = choice.maxPriorityFeePerGas ?: client.maxPriorityFeePerGas()
                val maxFee = choice.maxFeePerGas ?: (client.baseFeePerGas() * BigInteger.valueOf(12) / BigInteger.TEN + priority)
                GasFees.Eip1559(maxFeePerGas = maxFee, maxPriorityFeePerGas = priority)
            }
        }
        return UnsignedTransaction(
            chainId = request.chainId,
            nonce = request.nonce ?: client.nonce(credentials.address),
            gasLimit = request.gasLimit ?: client.estimateGas(credentials.address, request.to, request.value, request.data),
            to = request.to,
            value = request.value,
            data = request.data,
            fees = fees,
        ).sign(credentials)
    }

    /** Signs without touching the network; every field must already be set. */
    suspend fun signOffline(wallet: WalletKeystore, password: String, request: TransactionRequest): SignedTransaction {
        val fees = request.fees as? FeeChoice.Eip1559
        val maxFee = fees?.maxFeePerGas
        val priority = fees?.maxPriorityFeePerGas
        require(request.nonce != null && request.gasLimit != null && maxFee != null && priority != null) {
            "Offline signing needs nonce, gas, maxFeePerGas and maxPriorityFeePerGas in the transaction JSON"
        }
        val credentials = walletRepository.unlock(wallet, password)
        return UnsignedTransaction(
            chainId = request.chainId,
            nonce = request.nonce,
            gasLimit = request.gasLimit,
            to = request.to,
            value = request.value,
            data = request.data,
            fees = GasFees.Eip1559(maxFeePerGas = maxFee, maxPriorityFeePerGas = priority),
        ).sign(credentials)
    }

    /** @return the transaction hash reported by the node. */
    suspend fun broadcast(signed: SignedTransaction): String = client.sendRawTransaction(signed.raw)

    /** Polls until the transaction is mined. Cancel the calling coroutine to stop waiting. */
    suspend fun awaitReceipt(hash: String): TransactionReceipt {
        while (true) {
            client.receipt(hash)?.let { return it }
            delay(RECEIPT_POLL_MILLIS)
        }
    }

    private companion object {
        const val RECEIPT_POLL_MILLIS = 4_000L
    }
}
