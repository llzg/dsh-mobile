package com.labteto.dshmobile.ui.screens.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.labteto.dshmobile.connection.ConnectionManager
import com.labteto.dshmobile.connection.DiscoveredHost
import com.labteto.dshmobile.connection.DiscoveryEngine
import com.labteto.dshmobile.connection.HostConfig
import com.labteto.dshmobile.connection.HostsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUiState(
    val remembered: List<HostConfig> = emptyList(),
    val discovered: List<DiscoveredHost> = emptyList(),
    val scanning: Boolean = false,
    val connecting: Boolean = false,
    val error: String? = null,
    val autoConnectLast: Boolean = true,
    val autoConnectLan: Boolean = false,
    val autoConnectLoopback: Boolean = true,
    val showAdvanced: Boolean = false,
)

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val connectionManager: ConnectionManager,
    private val discoveryEngine: DiscoveryEngine,
    private val hostsStore: HostsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(ConnectUiState())
    val state: StateFlow<ConnectUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = hostsStore.settingsOnce()
            _state.update {
                it.copy(
                    autoConnectLast = settings.autoConnectLast,
                    autoConnectLan = settings.autoConnectLan,
                    autoConnectLoopback = settings.autoConnectLoopback,
                )
            }
            hostsStore.hosts.collect { hosts ->
                _state.update { it.copy(remembered = hosts) }
            }
        }
        viewModelScope.launch { autoConnect() }
    }

    private suspend fun autoConnect() {
        val settings = hostsStore.settingsOnce()
        // 1. Last used host.
        if (settings.autoConnectLast) {
            val last = hostsStore.hosts.first().firstOrNull()
            if (last != null) {
                val desc = discoveryEngine.probe(last.host, last.port)
                if (desc != null) {
                    connectTo(last)
                    return
                }
            }
        }
        // 2. LAN discovery.
        if (settings.autoConnectLan) {
            val found = discoveryEngine.scan(settings.knownPorts)
            val first = found.firstOrNull()
            if (first != null) {
                val config = hostsStore.rememberHost(first.host, first.host, first.port, isLoopback = false)
                connectTo(config)
                return
            }
        }
        // 3. Same-device loopback.
        if (settings.autoConnectLoopback) {
            val desc = discoveryEngine.probe("127.0.0.1", 3080)
            if (desc != null) {
                val config = hostsStore.rememberHost("This device", "127.0.0.1", 3080, isLoopback = true)
                connectTo(config)
            }
        }
    }

    fun scan() {
        if (_state.value.scanning) return
        _state.update { it.copy(scanning = true, error = null) }
        viewModelScope.launch {
            val settings = hostsStore.settingsOnce()
            val found = discoveryEngine.scan(settings.knownPorts)
            _state.update { it.copy(scanning = false, discovered = found) }
        }
    }

    fun connectManual(host: String, port: String) {
        val portInt = port.trim().toIntOrNull()
        if (host.isBlank() || portInt == null || portInt !in 1..65535) {
            _state.update { it.copy(error = "connect_failed") }
            return
        }
        val isLoopback = host == "127.0.0.1" || host == "localhost"
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            val desc = discoveryEngine.probe(host.trim(), portInt)
            if (desc == null) {
                _state.update { it.copy(connecting = false, error = "connect_failed") }
                return@launch
            }
            hostsStore.addKnownPort(portInt)
            val config = hostsStore.rememberHost(
                name = if (isLoopback) "This device" else host.trim(),
                host = host.trim(),
                port = portInt,
                isLoopback = isLoopback,
            )
            connectTo(config)
        }
    }

    fun connectTo(host: HostConfig) {
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            connectionManager.connect(host) { _ ->
                _state.update { it.copy(connecting = false) }
            }
        }
    }

    fun connectDiscovered(discovered: DiscoveredHost) {
        _state.update { it.copy(connecting = true, error = null) }
        viewModelScope.launch {
            hostsStore.addKnownPort(discovered.port)
            val config = hostsStore.rememberHost(
                name = discovered.host,
                host = discovered.host,
                port = discovered.port,
                isLoopback = false,
            )
            connectionManager.connect(config) { _ ->
                _state.update { it.copy(connecting = false) }
            }
        }
    }

    fun forget(host: HostConfig) {
        viewModelScope.launch { hostsStore.removeHost(host.id) }
    }

    fun setAuto(key: String, value: Boolean) {
        viewModelScope.launch {
            hostsStore.setSetting { current ->
                when (key) {
                    "last" -> current.copy(autoConnectLast = value)
                    "lan" -> current.copy(autoConnectLan = value)
                    else -> current.copy(autoConnectLoopback = value)
                }
            }
            val settings = hostsStore.settingsOnce()
            _state.update {
                it.copy(
                    autoConnectLast = settings.autoConnectLast,
                    autoConnectLan = settings.autoConnectLan,
                    autoConnectLoopback = settings.autoConnectLoopback,
                )
            }
        }
    }

    fun clearError() = _state.update { it.copy(error = null) }
}
