package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.wire.dto.CommandDescriptor

/**
 * What the composer's send button should actually do with the draft.
 *
 * A slash line is not a prompt: `session.prompt` hands its content straight to the model, so a
 * command has to be recognised here and written through `commands/execute` instead.
 */
internal sealed interface Submission {
    /** A registered command line; goes to the command gateway, never to the model. */
    data class Command(val line: String) : Submission

    /** Ordinary user text — including a `/skill-name` line, which the host resolves itself. */
    data class Prompt(val text: String) : Submission
}

/**
 * Decide whether a draft is a command or a prompt — the port of the harness web client's
 * `matchEnter` (`packages/client/ui-commands/src/client/service.ts`).
 *
 * The order matters, and the misses matter as much as the hits: a `/` line that names nothing in
 * the catalog falls through to [Submission.Prompt] on purpose, because that is how skills are
 * invoked — the host's pre-step boundary recognises the leading `/name` and injects the skill body.
 * Adjudicating against the catalog first is what keeps a skill that shares a name with a command
 * resolving to the command.
 *
 * @param draft the composer's raw text.
 * @param catalog the session's command catalog (`commands/list`); empty means every line is a prompt.
 * @param hasAttachments true when images ride along — a command line takes no attachments.
 */
internal fun adjudicate(
    draft: String,
    catalog: List<CommandDescriptor>,
    hasAttachments: Boolean,
): Submission {
    val trimmed = draft.trim()
    if (hasAttachments || !trimmed.startsWith("/")) return Submission.Prompt(draft)

    val separator = trimmed.indexOfFirst { it.isWhitespace() }
    val bare = separator == -1
    val name = (if (bare) trimmed else trimmed.substring(0, separator)).substring(1)
    if (name.isEmpty()) return Submission.Prompt(draft)

    // Case-sensitive on purpose: the host parses command names with `^/([a-z][a-z0-9_-]*)`, so a
    // catalog name is always lowercase and `/Compact` is not a command.
    val descriptor = catalog.firstOrNull { it.name == name } ?: return Submission.Prompt(draft)

    // An argument-taking command claims the whole line; one that takes none only answers to a bare
    // invocation, so `/compact and then some` stays a prompt rather than silently dropping the tail.
    if (descriptor.input != null) return Submission.Command(trimmed)
    if (!bare) return Submission.Prompt(draft)
    return Submission.Command(trimmed)
}
