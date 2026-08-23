package com.labteto.dshmobile.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.labteto.dshmobile.data.SessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground/network lifecycle nudges for the connection (0.8.3).
 *
 * The connection loop already reconnects with backoff on its own, but it can only observe a
 * dead socket — it has no idea the phone came back from a locked screen or hopped from Wi-Fi to
 * 5G. When that happens this nudge starts a reconnect immediately (instead of waiting out a
 * backoff tick) and then reconciles session state over HTTP, so a stale running flag — and a
 * composer stuck on the stop button — is corrected without waiting for a lost event to never
 * arrive.
 */
@Singleton
class ConnectionLifecycle @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: ConnectionManager,
    private val store: SessionStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = nudge("network-available")
        override fun onLost(network: Network) {
            // The socket will die on its own; nothing to do now except record it.
            Log.w(TAG, "NETWORK_LOST")
        }
    }

    /** Starts observing process foreground/background and network availability. Idempotent. */
    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(observer)
        connectivity.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            networkCallback,
        )
        Log.w(TAG, "LIFECYCLE_OBSERVERS_REGISTERED")
    }

    private val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_START) nudge("foreground")
    }

    /**
     * If a host is configured but the connection is not healthy, reconnect now and reconcile.
     *
     * [ReconnectPolicy.shouldReconnect] is the pure decision; this applies it. A backgrounded
     * app with keep-alive off will have let the socket die — returning to the foreground is
     * exactly when the loop would otherwise be mid-backoff, so this skips the wait.
     */
    private fun nudge(reason: String) {
        val state = manager.state.value
        if (!ReconnectPolicy.shouldReconnect(state.phase, state.host != null)) {
            // Connected (or nothing configured): nothing to do, but a connected state after a
            // reconnect is still worth reconciling once — cheap and keeps UI state honest.
            if (state.phase == ConnectionPhase.CONNECTED && state.host != null) {
                scope.launch { store.requestReconcile() }
            }
            return
        }
        Log.w(TAG, "LIFECYCLE_NUDGE reason=" + reason + " phase=" + state.phase)
        manager.reconnectIfNeeded()
        scope.launch { store.requestReconcile() }
    }

    private companion object {
        const val TAG = "ConnectionLifecycle"
    }
}
