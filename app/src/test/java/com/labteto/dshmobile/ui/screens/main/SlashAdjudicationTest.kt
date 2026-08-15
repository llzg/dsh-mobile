package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.wire.dto.CommandDescriptor
import com.labteto.dshmobile.core.wire.dto.CommandInputDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The composer's one decision: command or prompt.
 *
 * It matters both ways. A hit that is treated as a prompt reaches the model as text — which is how
 * picking a permission preset used to make the agent shell out and guess. A miss that is treated as
 * a command would break skills, which are invoked precisely by sending `/name` as a prompt.
 */
class SlashAdjudicationTest {

    private val catalog = listOf(
        CommandDescriptor(name = "compact", description = "Compact the conversation"),
        CommandDescriptor(
            name = "permission",
            description = "Switch the permission preset",
            input = CommandInputDescriptor(hint = "<preset>"),
        ),
        CommandDescriptor(
            name = "goal",
            description = "Set a goal",
            input = CommandInputDescriptor(hint = "<text>"),
        ),
    )

    private fun decide(draft: String, attachments: Boolean = false) =
        adjudicate(draft, catalog, attachments)

    @Test
    fun `ordinary text is a prompt`() {
        assertEquals(Submission.Prompt("build the thing"), decide("build the thing"))
    }

    @Test
    fun `a bare registered command is a command`() {
        assertEquals(Submission.Command("/compact"), decide("/compact"))
    }

    @Test
    fun `surrounding whitespace does not hide a command`() {
        assertEquals(Submission.Command("/compact"), decide("  /compact  "))
    }

    @Test
    fun `an argument-taking command claims the whole line`() {
        assertEquals(
            Submission.Command("/permission read-only"),
            decide("/permission read-only"),
        )
        assertEquals(Submission.Command("/goal ship it"), decide("/goal ship it"))
    }

    @Test
    fun `an argument-taking command invoked bare is still a command`() {
        assertEquals(Submission.Command("/permission"), decide("/permission"))
    }

    @Test
    fun `arguments on a command that takes none stay a prompt`() {
        // The host would drop the tail silently; better the model sees the sentence the user wrote.
        assertEquals(Submission.Prompt("/compact and summarise"), decide("/compact and summarise"))
    }

    @Test
    fun `an unregistered name falls through to the prompt path`() {
        // This is the skill path: the host's pre-step boundary resolves `/name` itself.
        assertEquals(Submission.Prompt("/artifact-design"), decide("/artifact-design"))
        assertEquals(Submission.Prompt("/my-skill do it"), decide("/my-skill do it"))
    }

    @Test
    fun `a bare slash is not a command`() {
        assertEquals(Submission.Prompt("/"), decide("/"))
        assertEquals(Submission.Prompt("/ compact"), decide("/ compact"))
    }

    @Test
    fun `names are matched case-sensitively, as the host parses them`() {
        assertEquals(Submission.Prompt("/Compact"), decide("/Compact"))
    }

    @Test
    fun `an empty catalog makes every line a prompt`() {
        assertEquals(Submission.Prompt("/compact"), adjudicate("/compact", emptyList(), false))
    }

    @Test
    fun `attachments force the prompt path`() {
        // A command line takes no images, and the prompt API is the only thing that carries them.
        assertEquals(Submission.Prompt("/compact"), decide("/compact", attachments = true))
    }
}
