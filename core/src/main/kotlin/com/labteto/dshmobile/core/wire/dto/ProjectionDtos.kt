package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Typed views over the session projections the harness publishes on `session/projection` frames
 * and in the `session.history` tail block. Every field carries a default: a projection may arrive
 * empty (`contextPressure` is `{}` before the first request) and the wire layer never crashes on
 * partial data.
 */

/**
 * The `sessionStats` projection.
 *
 * Two fields are aggregates, not averages: [ttftMs] is the *sum* of time-to-first-token across
 * [ttftSteps] steps, and throughput has to be derived from [decodeTokens] and [decodeMs]. There is
 * no per-second field on the wire.
 */
@Serializable
data class SessionStatsView(
    @SerialName("turns") val turns: Int = 0,
    @SerialName("steps") val steps: Int = 0,
    @SerialName("llmMs") val llmMs: Long = 0,
    @SerialName("toolMs") val toolMs: Long = 0,
    @SerialName("ttftMs") val ttftMs: Long = 0,
    @SerialName("ttftSteps") val ttftSteps: Int = 0,
    @SerialName("decodeMs") val decodeMs: Long = 0,
    @SerialName("decodeTokens") val decodeTokens: Long = 0,
) {
    /** Mean time to first token, or null before any step has reported one. */
    val meanTtftMs: Long? get() = if (ttftSteps > 0) ttftMs / ttftSteps else null

    /** Decode throughput, or null before any tokens have been decoded. */
    val tokensPerSecond: Double? get() =
        if (decodeMs > 0 && decodeTokens > 0) decodeTokens * 1000.0 / decodeMs else null
}

/** The `tokenUsage` projection: cumulative token accounting for the session. */
@Serializable
data class TokenUsageView(
    @SerialName("uncachedInputTokens") val uncachedInputTokens: Long = 0,
    @SerialName("outputTokens") val outputTokens: Long = 0,
    @SerialName("cacheReadTokens") val cacheReadTokens: Long = 0,
    @SerialName("cacheWriteTokens") val cacheWriteTokens: Long = 0,
) {
    /** Every token that entered the model, cached or not. */
    val inputTokens: Long get() = uncachedInputTokens + cacheReadTokens + cacheWriteTokens

    /** Share of input served from cache, or null when nothing has been sent yet. */
    val cacheHitRatio: Double? get() =
        inputTokens.takeIf { it > 0 }?.let { cacheReadTokens.toDouble() / it }
}

/** The `contextPressure` projection: how full the model's context window is. */
@Serializable
data class ContextPressureView(
    @SerialName("pressureTokens") val pressureTokens: Long? = null,
    @SerialName("projectedTokens") val projectedTokens: Long? = null,
    @SerialName("contextWindow") val contextWindow: Long? = null,
) {
    /** Projected occupancy in `0f..1f`, or null until both numbers are known. */
    val usedRatio: Float? get() {
        val window = contextWindow?.takeIf { it > 0 } ?: return null
        val used = projectedTokens ?: pressureTokens ?: return null
        return (used.toFloat() / window.toFloat()).coerceIn(0f, 1f)
    }
}

/** The `contextBreakdown` projection: what is occupying the context window. */
@Serializable
data class ContextBreakdownView(
    @SerialName("systemTokens") val systemTokens: Long = 0,
    @SerialName("toolsTokens") val toolsTokens: Long = 0,
    @SerialName("messageTokens") val messageTokens: Long = 0,
) {
    val total: Long get() = systemTokens + toolsTokens + messageTokens
}

/**
 * The `imageLimits` projection: the host's own attachment bounds. Defaults mirror the shipped
 * harness so a client that never receives the projection still refuses obviously-oversized images.
 */
@Serializable
data class ImageLimitsView(
    @SerialName("maxImageBytes") val maxImageBytes: Long = 5_242_880,
    @SerialName("maxImagesPerMessage") val maxImagesPerMessage: Int = 20,
    @SerialName("maxMessageImageBytes") val maxMessageImageBytes: Long = 104_857_600,
    @SerialName("maxImagePixels") val maxImagePixels: Long = 40_000_000,
    @SerialName("mediaTypes") val mediaTypes: List<String> =
        listOf("image/png", "image/jpeg", "image/webp", "image/gif"),
) {
    /** True when the host would accept an attachment of this media type and size. */
    fun accepts(mediaType: String, bytes: Int): Boolean =
        mediaType in mediaTypes && bytes <= maxImageBytes
}

/** The `plan` projection: whether plan mode is on, and whether a plan is awaiting review. */
@Serializable
data class PlanStateView(
    @SerialName("active") val active: Boolean = false,
    @SerialName("pending") val pending: Boolean = false,
)

/** The `sessionListMetadata` projection: list-shaping facts the summary would otherwise repeat. */
@Serializable
data class SessionListMetadataView(
    @SerialName("blank") val blank: Boolean = true,
    @SerialName("lastPromptAt") val lastPromptAt: Long? = null,
)
