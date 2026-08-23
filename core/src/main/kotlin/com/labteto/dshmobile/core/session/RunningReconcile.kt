package com.labteto.dshmobile.core.session

/**
 * The rule for the conversation's running flag across disconnects and reconnects.
 *
 * A WebSocket may miss the event that ends a turn, so the app must never let a running flag
 * learned from the stream outlive an authoritative HTTP rebuild. Pure so the recovery cases are
 * unit-testable without Android.
 */
object RunningReconcile {

    /**
     * The running flag a full event list implies — the server's own record.
     *
     * A turn/start without a matching turn/end means the turn is still in flight; a
     * turn/end means it is not. This is exactly what the HTTP history rebuild provides, so it is
     * the authoritative answer after a reconnect.
     */
    fun runningFromEvents(events: List<SessionEventEnvelope>): Boolean {
        var running = false
        for (event in events) {
            when (event.type) {
                "turn/start" -> running = true
                "turn/end" -> running = false
                else -> Unit
            }
        }
        return running
    }

    /**
     * Adopt the fold's answer after an authoritative HTTP rebuild of the current session.
     *
     * The stale value — "running" because the turn/end event was lost with the socket — is
     * discarded; the history is the server's own transcript.
     */
    fun afterHistoryRebuild(foldRunning: Boolean): Boolean = foldRunning

    /**
     * The live-path rule: an explicit live event (turn/start|end, session/status) wins; when the
     * stream has said nothing yet, the fold of whatever events exist is the truth.
     */
    fun live(liveRunning: Boolean?, foldRunning: Boolean): Boolean = liveRunning ?: foldRunning
}
