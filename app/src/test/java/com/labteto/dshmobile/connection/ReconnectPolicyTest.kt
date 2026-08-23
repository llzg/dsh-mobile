package com.labteto.dshmobile.connection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lifecycle/network decisions (Case E) and the HTTP-ok-but-WS-down UI mapping (Case F) for 0.8.3.
 */
class ReconnectPolicyTest {

    // Case E — returning to the foreground (or the network coming back) must reconnect when a
    // host is configured and the connection is not healthy, and leave a healthy one alone.
    @Test
    fun `case E reconnects when a host exists and the connection is not healthy`() {
        assertTrue(ReconnectPolicy.shouldReconnect(ConnectionPhase.RECONNECTING, hasHost = true))
        assertTrue(ReconnectPolicy.shouldReconnect(ConnectionPhase.CONNECTING, hasHost = true))
        assertTrue(ReconnectPolicy.shouldReconnect(ConnectionPhase.DISCONNECTED, hasHost = true))
        assertFalse(ReconnectPolicy.shouldReconnect(ConnectionPhase.CONNECTED, hasHost = true))
        assertFalse(ReconnectPolicy.shouldReconnect(ConnectionPhase.RECONNECTING, hasHost = false))
    }

    // Case F — HTTP is fine but the WS stream is down: the UI must say RECONNECTING, not that
    // the harness is unreachable (FAILED is only for a never-connected first handshake).
    @Test
    fun `case F a dropped stream reads as reconnecting not harness-unreachable`() {
        assertEquals(ConnectionUiStatus.RECONNECTING, ReconnectPolicy.statusFor(ConnectionPhase.RECONNECTING, hasConnected = true))
        // First handshake of a process still retrying: the connect screen owns that story.
        assertEquals(ConnectionUiStatus.FAILED, ReconnectPolicy.statusFor(ConnectionPhase.CONNECTING, hasConnected = false))
        assertEquals(ConnectionUiStatus.CONNECTED, ReconnectPolicy.statusFor(ConnectionPhase.CONNECTED, hasConnected = true))
        assertEquals(ConnectionUiStatus.RECONNECTING, ReconnectPolicy.statusFor(ConnectionPhase.CONNECTING, hasConnected = true))
    }
}
