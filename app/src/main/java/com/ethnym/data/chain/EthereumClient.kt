package com.ethnym.data.chain

import com.ethnym.BuildConfig
import com.ethnym.core.eth.Addresses
import com.ethnym.core.eth.Multicall3
import com.ethnym.data.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.web3j.ens.EnsResolutionException
import org.web3j.ens.EnsResolver
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.TransactionReceipt
import org.web3j.protocol.http.HttpService
import org.web3j.utils.Numeric
import java.io.IOException
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

class OfflineModeException : IOException("Offline mode is on. Turn it off in Settings to use the network.")

class RpcException(message: String) : IOException(message)

/**
 * Ethereum mainnet over JSON-RPC, through web3j. Every call goes to the RPC currently selected
 * in Settings (or the built-in default), and fails fast with [OfflineModeException] while offline
 * mode is on.
 */
@Singleton
class EthereumClient @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    private val mutex = Mutex()
    private var connection: Pair<String, Web3j>? = null

    suspend fun balance(address: String): BigInteger =
        request { it.ethGetBalance(address, DefaultBlockParameterName.LATEST) }.balance

    suspend fun gasPrice(): BigInteger = request { it.ethGasPrice() }.gasPrice

    suspend fun maxPriorityFeePerGas(): BigInteger = request { it.ethMaxPriorityFeePerGas() }.maxPriorityFeePerGas

    suspend fun baseFeePerGas(): BigInteger =
        requireNotNull(request { it.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false) }.block.baseFeePerGas) {
            "The RPC did not return a base fee"
        }

    /** Next nonce, counting pending transactions. */
    suspend fun nonce(address: String): BigInteger =
        request { it.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING) }.transactionCount

    suspend fun estimateGas(from: String, to: String, value: BigInteger, data: String): BigInteger =
        request { it.ethEstimateGas(Transaction(from, null, null, null, to, value, data)) }.amountUsed

    /** Read-only call; returns the raw return data. */
    suspend fun call(to: String, data: String): ByteArray {
        val response = request { it.ethCall(Transaction.createEthCallTransaction(null, to, data), DefaultBlockParameterName.LATEST) }
        if (response.isReverted) throw RpcException(response.revertReason ?: "Execution reverted")
        return Numeric.hexStringToByteArray(response.value)
    }

    /**
     * Many read calls in few requests, through Multicall3, sent in parallel. Results keep the
     * order of [calls]. Failures stay with the calls they hit: a call whose target is not a valid
     * address is never sent (say, a typo in a bundled list), and a request that fails marks only
     * its own batch; both come back undelivered. Throws only when no request got through at all
     * (offline, RPC down), so the caller can say why.
     */
    suspend fun multicall(calls: List<Multicall3.Call>): List<Multicall3.Result> {
        val sendable = calls.map { Addresses.isValid(it.target) }
        val batches = coroutineScope {
            calls.filterIndexed { index, _ -> sendable[index] }.chunked(MULTICALL_CHUNK)
                .map { chunk -> async { aggregate3(chunk) } }
                .awaitAll()
        }
        val errors = batches.mapNotNull { it.error }
        if (errors.isNotEmpty() && errors.size == batches.size) throw errors.first()
        val results = batches.flatMap { it.results }.iterator()
        return sendable.map { if (it) results.next() else UNDELIVERED }
    }

    /** One Multicall3 request. If it fails, every call in [chunk] comes back undelivered. */
    private suspend fun aggregate3(chunk: List<Multicall3.Call>): Batch = try {
        val response = request {
            it.ethCall(
                Transaction.createEthCallTransaction(null, Multicall3.ADDRESS, Multicall3.encodeAggregate3(chunk)),
                DefaultBlockParameterName.LATEST,
            )
        }
        if (response.isReverted) throw RpcException(response.revertReason ?: "Multicall reverted")
        val results = Multicall3.decodeAggregate3(response.value)
        check(results.size == chunk.size) { "Multicall answered ${results.size} of ${chunk.size} calls" }
        Batch(results)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Batch(List(chunk.size) { UNDELIVERED }, error = e)
    }

    private class Batch(val results: List<Multicall3.Result>, val error: Exception? = null)

    /** @return the transaction hash. */
    suspend fun sendRawTransaction(signed: String): String = request { it.ethSendRawTransaction(signed) }.transactionHash

    suspend fun receipt(hash: String): TransactionReceipt? =
        request { it.ethGetTransactionReceipt(hash) }.transactionReceipt.orElse(null)

    /**
     * Resolves an ENS name with web3j's resolver (ENSIP-15 normalisation, wildcard resolvers and
     * CCIP-Read offchain lookups). Returns null when the name has no address.
     */
    suspend fun resolveEns(name: String): String? = withContext(Dispatchers.IO) {
        val web3j = web3j()
        try {
            EnsResolver(web3j).resolve(name)
                ?.takeIf { Addresses.isValid(it) && !it.equals(Addresses.ZERO, ignoreCase = true) }
                ?.let(Addresses::checksum)
        } catch (_: EnsResolutionException) {
            null
        }
    }

    private suspend fun <T : Response<*>> request(build: (Web3j) -> Request<*, T>): T = withContext(Dispatchers.IO) {
        val response = build(web3j()).send()
        response.error?.let { throw RpcException(it.message ?: "RPC error ${it.code}") }
        response
    }

    private suspend fun web3j(): Web3j {
        val settings = settingsRepository.settings.first()
        if (settings.offlineMode) throw OfflineModeException()
        val url = settings.activeRpc?.url ?: BuildConfig.MAINNET_RPC_URL
        return mutex.withLock {
            connection?.takeIf { it.first == url }?.second
                ?: Web3j.build(HttpService(url)).also { web3j ->
                    connection?.second?.shutdown()
                    connection = url to web3j
                }
        }
    }

    private companion object {
        /** Calls per Multicall3 request; keeps each eth_call well under common RPC payload limits. */
        const val MULTICALL_CHUNK = 100

        val UNDELIVERED = Multicall3.Result(success = false, returnData = ByteArray(0), delivered = false)
    }
}
