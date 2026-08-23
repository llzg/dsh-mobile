package com.labteto.dshmobile.core.session

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Recovery cases for the stale-running fix (0.8.3): after a reconnect the client must adopt the
 * HTTP history's turn state, never a running flag learned from a WebSocket that may have missed
 * the turn/end event.
 */
class RunningReconcileTest {

    private fun event(type: String, seq: Long, data: JsonObject): SessionEventEnvelope =
        SessionEventEnvelope(type, seq, seq, data)

    private fun turnStart(seq: Long) = event("turn/start", seq, buildJsonObject { put("turn", 1) })

    private fun turnEnd(seq: Long) = event("turn/end", seq, buildJsonObject {
        put("turn", 1)
        putJsonObject("reason") { put("kind", "completed") }
    })

    // Case A — running=true → WS dies → the server's turn actually ends → reconnect → HTTP
    // history shows the turn/end → running becomes false → the send button recovers.
    @Test
    fun `case A a turn that ended while disconnected is no longer running after reconcile`() {
        // What the app believed from the stream before the socket died: still running.
        val liveRunning = true
        // What the HTTP history says the server recorded: the turn ended.
        val history = listOf(turnStart(1), turnEnd(2))
        val foldRunning = RunningReconcile.runningFromEvents(history)
        assertFalse(foldRunning)
        // Without the reconcile, the stale live value would win and keep the stop button up.
        assertTrue(RunningReconcile.live(liveRunning, foldRunning))
        // The reconcile adopts the authoritative history answer over the stale live value.
        val reconciled = RunningReconcile.afterHistoryRebuild(foldRunning)
        assertFalse(reconciled)
    }

    // Case B — same disconnect, but the server's turn is still running: history has turn/start
    // without turn/end → running stays true → the stop button is retained.
    @Test
    fun `case B a turn still running on the server stays running after reconcile`() {
        val history = listOf(turnStart(1))
        val foldRunning = RunningReconcile.runningFromEvents(history)
        assertTrue(foldRunning)
        assertTrue(RunningReconcile.afterHistoryRebuild(foldRunning))
    }

    // Case C — idle session: an empty (or turn-free) history folds to not-running, so a reconnect
    // leaves the composer usable.
    @Test
    fun `case C an idle session stays idle across a reconnect`() {
        assertFalse(RunningReconcile.runningFromEvents(emptyList()))
        assertFalse(RunningReconcile.afterHistoryRebuild(false))
        // Live path with nothing heard from the stream yet also folds to idle.
        assertFalse(RunningReconcile.live(null, false))
    }

    // The history order matters: turn/end after turn/start wins, and a later turn/start re-arms.
    @Test
    fun `history fold follows turn start and end order`() {
        val ended = listOf(turnStart(1), turnEnd(2))
        assertFalse(RunningReconcile.runningFromEvents(ended))
        val startedAgain = listOf(turnStart(1), turnEnd(2), turnStart(3))
        assertTrue(RunningReconcile.runningFromEvents(startedAgain))
    }
}
