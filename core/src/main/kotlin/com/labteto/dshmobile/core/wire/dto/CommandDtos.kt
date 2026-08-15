package com.labteto.dshmobile.core.wire.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Slash-command DTOs, ported from `packages/interaction/commands/src/types.ts` (v0.1.0-rc.5).
 *
 * The catalog is read through the typert remote `commands/list`; it is per-session and
 * deployment-dependent (a preset switch changes which commands an agent resolves), so it is
 * never hardcoded on the client.
 */

/** A command that takes a trailing argument, plus the hint a client shows for it. */
@Serializable
data class CommandInputDescriptor(
    @SerialName("hint") val hint: String = "",
)

/**
 * One entry of a session's command catalog. [input] is null for a bare command, which a client
 * may execute straight from the menu; a command with [input] prefills the composer instead.
 */
@Serializable
data class CommandDescriptor(
    @SerialName("name") val name: String,
    @SerialName("description") val description: String = "",
    @SerialName("input") val input: CommandInputDescriptor? = null,
) {
    /** The line a bare invocation submits. */
    val line: String get() = "/$name"

    /** The draft prefix an argument-taking invocation prefills. */
    val draftPrefix: String get() = "/$name "
}
