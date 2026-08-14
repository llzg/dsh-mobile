package com.labteto.dshmobile.connection

import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.dto.HostDescription
import com.labteto.dshmobile.core.wire.OkHttpRpcTransport
import com.labteto.dshmobile.core.wire.RpcResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Enumeration
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds DeepSeek Harness instances on the local Wi-Fi.
 *
 * The harness has no mDNS/health endpoint, so discovery is an active sweep:
 * the device's IPv4 subnet is scanned on the known ports and each candidate
 * must complete the [DshApiClient.hostDescribe] readiness probe.
 */
@Singleton
class DiscoveryEngine @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    /** This device's non-loopback IPv4 addresses (e.g. Wi-Fi). */
    fun localIpv4s(): List<String> = runCatching {
        val result = mutableListOf<String>()
        val interfaces: Enumeration<NetworkInterface> = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val iface = interfaces.nextElement()
            if (!iface.isUp || iface.isLoopback) continue
            val addresses = iface.inetAddresses
            while (addresses.hasMoreElements()) {
                val addr = addresses.nextElement()
                if (addr is Inet4Address && !addr.isLoopbackAddress) result.add(addr.hostAddress ?: "")
            }
        }
        result.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    /** /24 candidates for a device IPv4, e.g. 192.168.1.1..254 (host itself excluded). */
    fun subnetCandidates(ip: String): List<String> {
        val parts = ip.split('.')
        if (parts.size != 4) return emptyList()
        val prefix = parts.take(3).joinToString(".")
        return (1..254).map { "$prefix.$it" }.filter { it != ip }
    }

    /** Probe one authority; null when it is not a harness. */
    suspend fun probe(host: String, port: Int): HostDescription? = withContext(Dispatchers.IO) {
        val client = DshApiClient(
            transport = OkHttpRpcTransport(
                baseUrl = "http://$host:$port",
                client = okHttpClient.newBuilder()
                    .connectTimeout(700, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build(),
            ),
            wsFactory = { _, _ -> throw UnsupportedOperationException("probe does not open streams") },
        )
        when (val result = client.hostDescribe()) {
            is RpcResult.Ok -> result.value
            is RpcResult.Err -> null
        }
    }

    /**
     * Sweep the subnet(s) of this device on [ports], probing concurrently.
     * Returns discovered harnesses (probe failures are silently ignored).
     */
    suspend fun scan(ports: List<Int>): List<DiscoveredHost> = supervisorScope {
        val subnets = localIpv4s()
        if (subnets.isEmpty()) return@supervisorScope emptyList()
        val candidates = subnets.flatMap { subnetCandidates(it) }.distinct()
        val portsSafe = ports.ifEmpty { listOf(3080) }
        val discovered = mutableListOf<DiscoveredHost>()

        // Small bounded fan-out: 32 concurrent probes, chunked over the /24.
        candidates.chunked(32).forEach { chunk ->
            chunk.map { ip ->
                async {
                    var last: DiscoveredHost? = null
                    for (port in portsSafe) {
                        val desc = runCatching { probe(ip, port) }.getOrNull()
                        if (desc != null) {
                            last = DiscoveredHost(ip, port, desc.version, desc.cwd)
                            break
                        }
                    }
                    last
                }
            }.awaitAll().filterNotNull().forEach { discovered.add(it) }
        }
        discovered
    }
}
