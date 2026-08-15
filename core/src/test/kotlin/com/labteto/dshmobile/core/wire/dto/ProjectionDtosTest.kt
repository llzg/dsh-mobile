package com.labteto.dshmobile.core.wire.dto

import com.labteto.dshmobile.core.wire.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The projection payloads carry facts the UI would otherwise have to guess at, and several of them
 * are aggregates rather than the per-unit numbers they look like. These tests pin the arithmetic
 * and the tolerance for partial data.
 */
class ProjectionDtosTest {

    @Test
    fun `time to first token is a sum across steps, not an average`() {
        val stats = decodeFromString<SessionStatsView>(
            """{"turns":2,"steps":33,"llmMs":521603,"toolMs":50316,
               "ttftMs":50051,"ttftSteps":32,"decodeMs":471552,"decodeTokens":30127}""",
        )
        // Printing ttftMs raw would claim a 50-second time to first token; it is the total across
        // 32 steps, so the reading anyone wants is the mean.
        assertEquals(1564L, stats.meanTtftMs)
    }

    @Test
    fun `throughput is derived because the wire carries no per-second field`() {
        val stats = decodeFromString<SessionStatsView>(
            """{"decodeMs":471552,"decodeTokens":30127}""",
        )
        assertEquals(63.9, stats.tokensPerSecond!!, 0.1)
    }

    @Test
    fun `stats with no work yet report no rate rather than zero or infinity`() {
        val stats = decodeFromString<SessionStatsView>("""{"turns":0,"steps":0}""")
        assertNull(stats.tokensPerSecond)
        assertNull(stats.meanTtftMs)
    }

    @Test
    fun `context pressure is empty before the first request`() {
        val pressure = decodeFromString<ContextPressureView>("{}")
        assertNull(pressure.usedRatio)
    }

    @Test
    fun `context pressure clamps to the window`() {
        val pressure = decodeFromString<ContextPressureView>(
            """{"pressureTokens":109957,"projectedTokens":1250000,"contextWindow":1000000}""",
        )
        assertEquals(1f, pressure.usedRatio!!, 0.0001f)
    }

    @Test
    fun `token usage counts cache reads as input`() {
        val usage = decodeFromString<TokenUsageView>(
            """{"uncachedInputTokens":74786,"outputTokens":30127,
               "cacheReadTokens":1781376,"cacheWriteTokens":0}""",
        )
        assertEquals(1_856_162L, usage.inputTokens)
        assertEquals(0.96, usage.cacheHitRatio!!, 0.01)
    }

    @Test
    fun `image limits default to the shipped harness bounds when absent`() {
        val limits = decodeFromString<ImageLimitsView>("{}")
        assertEquals(5_242_880L, limits.maxImageBytes)
        assertTrue(limits.accepts("image/png", 1_000))
        assertTrue(!limits.accepts("image/tiff", 1_000))
        assertTrue(!limits.accepts("image/png", 6_000_000))
    }

    @Test
    fun `unknown projection fields are ignored rather than failing the decode`() {
        val plan = decodeFromString<PlanStateView>(
            """{"active":true,"pending":false,"somethingNewInTheHarness":{"a":1}}""",
        )
        assertTrue(plan.active)
    }
}

/** Permission presets are read from a projection and written through a slash command. */
class PermissionDtosTest {

    @Test
    fun `custom is never offered as a switch target`() {
        val select = decodeFromString<PermissionSelect>(
            """{"options":[{"value":"read-only","name":"read-only"},
                          {"value":"danger-full-access","name":"danger-full-access"},
                          {"value":"custom","name":"Custom"}],
               "currentValue":"custom"}""",
        )
        assertEquals(listOf("read-only", "danger-full-access"), select.selectable.map { it.value })
        assertEquals("Custom", select.current?.name)
    }

    @Test
    fun `labels are humanised the same way the desktop client does`() {
        assertEquals("Full access", displayPermissionPreset("danger-full-access", "danger-full-access"))
        assertEquals("Workspace Write", displayPermissionPreset("workspace-write", "workspace-write"))
        assertEquals("Read Only", displayPermissionPreset("read-only", "read-only"))
    }

    @Test
    fun `a deployment that renames a preset keeps its own label`() {
        // The preset table is configurable, so a local id-to-string map would mislabel any
        // deployment that renamed one. Only the full-access id gets special treatment.
        assertEquals("Sandboxed", displayPermissionPreset("workspace-write", "Sandboxed"))
    }
}
