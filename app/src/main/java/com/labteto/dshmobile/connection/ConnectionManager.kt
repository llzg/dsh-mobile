package com.labteto.dshmobile.connection

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.labteto.dshmobile.core.wire.ConnectionLoop
import com.labteto.dshmobile.core.wire.ConnectionState
import com.labteto.dshmobile.core.wire.DshApiClient
import com.labteto.dshmobile.core.wire.dto.HostDescription
import com.labteto.dshmobile.core.wire.LoopConfig
import com.labteto.dshmobile.core.wire.LoopSinks
import com.labteto.dshmobile.core.wire.OkHttpRpcTransport
import com.labteto.dshmobile.core.wire.ServerRequest
import com.labteto.dshmobile.core.wire.WsDownlink
import com.labteto.dshmobile.core.wire.WsDownlinkSink
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/** UI-facing connection state. */
enum class ConnectionPhase { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

data class ConnectionUiState(
    val phase: ConnectionPhase = ConnectionPhase.DISCONNECTED,
    val host: HostConfig? = null,
    val description: HostDescription? = null,
    val error: String? = null,
    /** True once at least one generation completed the readiness handshake. */
    val hasConnected: Boolean = false,
)

/**
 * Owns the live connection to one harness: the ConnectionLoop (readiness
 * handshake + reconnect/backoff), the foreground service binding for
 * background operation, and the UI state mirror. Single active host at a time.
 */
@Singleton
class ConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val hostsStore: HostsStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _state = MutableStateFlow(ConnectionUiState())
    val state: StateFlow<ConnectionUiState> = _state.asStateFlow()

    private var loop: ConnectionLoop? = null
    private var api: DshApiClient? = null
    private var activeHost: HostConfig? = null

    /** Downlink frame consumers (screens subscribe here). */
    val muxFrames = kotlinx.coroutines.flow.MutableSharedFlow<ServerRequest>(extraBufferCapacity = 256)
    val hostFrames = kotlinx.coroutines.flow.MutableSharedFlow<ServerRequest>(extraBufferCapacity = 64)

    private val sinks = object : LoopSinks {
        override fun onMuxFrame(frame: ServerRequest) {
            muxFrames.tryEmit(frame)
        }

        override fun onHostFrame(frame: ServerRequest) {
            hostFrames.tryEmit(frame)
        }

        override fun onConnected(description: HostDescription) {
            val host = activeHost
            if (host != null) scope.launch { hostsStore.touchHost(host.host, host.port) }
            _state.value = ConnectionUiState(ConnectionPhase.CONNECTED, activeHost, description, null, hasConnected = true)
            maybeStartService()
        }

        override fun onStateChange(state: ConnectionState) {
            val phase = when (state) {
                ConnectionState.CONNECTED -> ConnectionPhase.CONNECTED
                ConnectionState.RECONNECTING -> ConnectionPhase.RECONNECTING
            }
            _state.value = _state.value.copy(phase = phase, error = null)
        }
    }

    /** Build a client for manual probing/prompting without the full loop. */
    fun probeClient(host: String, port: Int): DshApiClient = DshApiClient(
        transport = OkHttpRpcTransport("http://$host:$port", okHttpClient),
        wsFactory = { path, sink -> WsDownlink("http://$host:$port$path", okHttpClient, sink) },
    )

    val connectedApi: DshApiClient? get() = api

    suspend fun connect(config: HostConfig, failure: (String) -> Unit) {
        disconnect()
        activeHost = config
        _state.value = ConnectionUiState(ConnectionPhase.CONNECTING, config, null, null)
        val client = probeClient(config.host, config.port)
        api = client
        val loop = ConnectionLoop(client, sinks, LoopConfig())
        this.loop = loop
        loop.start()
        hostsStore.upsertHost(config)
        // The loop publishes CONNECTED/RECONNECTING on its own; a hard failure
        // (e.g. trust-fence 403) surfaces here through describe errors — poll once.
        scope.launch {
            kotlinx.coroutines.delay(2500)
            if (_state.value.phase == ConnectionPhase.CONNECTING) {
                // Loop is stuck (unreachable host keeps backing off). Keep trying
                // but surface a hint once.
                failure("")
            }
        }
    }

    fun disconnect() {
        loop?.stop()
        loop = null
        api = null
        activeHost = null
        stopService()
        _state.value = ConnectionUiState()
    }

    fun reconnectIfNeeded() {
        val host = activeHost ?: return
        loop?.stop()
        val client = api ?: probeClient(host.host, host.port).also { api = it }
        loop = ConnectionLoop(client, sinks, LoopConfig()).also { it.start() }
    }

    private fun maybeStartService() {
        val settings = runBlockingRead { hostsStore.settingsOnce() }
        if (settings.keepConnectedInBackground) startService()
    }

    private fun startService() {
        val intent = Intent(context, ConnectionService::class.java)
        ContextCompat.startForegroundService(context, intent)
    }

    private fun stopService() {
        context.stopService(Intent(context, ConnectionService::class.java))
    }

    private fun <T> runBlockingRead(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
