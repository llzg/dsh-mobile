package com.labteto.dshmobile.connection

/**
 * User-facing connection status for the chat screen's banner and composer.
 *
 * Deliberately distinct from [ConnectionPhase]: the UI must say "reconnecting" while the WS is
 * down, not "failed" — HTTP may still work perfectly (uploads, RPC), and the harness is not
 * unreachable, its event stream is. [statusFor] is pure so Case F of the recovery matrix is
 * testable without Android.
 */
enum class ConnectionUiStatus { CONNECTED, RECONNECTING, FAILED }

/**
 * Pure decisions for the reconnect/lifecycle policy (0.8.3).
 *
 * - [shouldReconnect]: a host is configured and the connection is not healthy — used when the
 *   app returns to the foreground or the network comes back, so recovery starts immediately
 *   instead of waiting for the next backoff tick.
 * - [statusFor]: what the chat screen should show for a phase. A connection that has previously
 *   been healthy and is now retrying is RECONNECTING ("重新连接中…"), never FAILED, even when
 *   HTTP is fine — the banner describes the event stream, not the harness.
 */
object ReconnectPolicy {

    fun shouldReconnect(phase: ConnectionPhase, hasHost: Boolean): Boolean =
        hasHost && phase != ConnectionPhase.CONNECTED

    fun statusFor(phase: ConnectionPhase, hasConnected: Boolean): ConnectionUiStatus = when (phase) {
        ConnectionPhase.CONNECTED -> ConnectionUiStatus.CONNECTED
        ConnectionPhase.RECONNECTING -> ConnectionUiStatus.RECONNECTING
        // The first handshake of a process has nothing to reconnect to; a failure there is
        // CONNECTING, and the connect screen owns the story.
        ConnectionPhase.CONNECTING ->
            if (hasConnected) ConnectionUiStatus.RECONNECTING else ConnectionUiStatus.FAILED
        ConnectionPhase.DISCONNECTED -> ConnectionUiStatus.FAILED
    }
}
