package com.labteto.dshmobile.connection

import kotlinx.serialization.Serializable

/** One remembered harness endpoint. */
@Serializable
data class HostConfig(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val isLoopback: Boolean = false,
    val lastConnectedAt: Long = 0L,
) {
    val authority: String get() = "$host:$port"
    val baseUrl: String get() = "http://$authority"
}

/** A harness found by the active LAN scan. */
data class DiscoveredHost(
    val host: String,
    val port: Int,
    val version: String,
    val cwd: String,
) {
    val authority: String get() = "$host:$port"
}

/** App-level persisted settings (DataStore). */
data class AppSettings(
    val autoConnectLast: Boolean = true,
    val autoConnectLan: Boolean = false,
    val autoConnectLoopback: Boolean = true,
    val keepConnectedInBackground: Boolean = false,
    val notifyTurnComplete: Boolean = true,
    val notifyGoal: Boolean = true,
    val notifyNeedsAction: Boolean = true,
    val themePreference: String = "system", // light | dark | system
    val localeOverride: String? = null, // null = system
    val knownPorts: List<Int> = listOf(3080),
)
