package com.labteto.dshmobile.ui.screens.main

import com.labteto.dshmobile.core.session.ToolCallNode
import com.labteto.dshmobile.core.session.ToolResultNode
import com.labteto.dshmobile.core.wire.dto.ContentBlock
import com.labteto.dshmobile.core.wire.dto.DiffView
import com.labteto.dshmobile.core.wire.dto.GenericView
import com.labteto.dshmobile.core.wire.dto.ReadView
import com.labteto.dshmobile.core.wire.dto.SearchView
import com.labteto.dshmobile.core.wire.dto.TerminalView
import com.labteto.dshmobile.core.wire.dto.ToolEventFor
import com.labteto.dshmobile.core.wire.dto.ToolEventView
import com.labteto.dshmobile.core.wire.dto.ToolView
import com.labteto.dshmobile.core.wire.dto.UnknownView
import com.labteto.dshmobile.core.wire.dto.WebView
import com.labteto.dshmobile.ui.components.ContentBlockView
import com.labteto.dshmobile.ui.components.DiffHunk
import com.labteto.dshmobile.ui.components.ReadLine
import com.labteto.dshmobile.ui.components.SearchFile
import com.labteto.dshmobile.ui.components.SearchMatch
import com.labteto.dshmobile.ui.components.SearchMatches
import com.labteto.dshmobile.ui.components.ToolCardView
import com.labteto.dshmobile.ui.components.WebCardKind
import com.labteto.dshmobile.ui.components.WebSource

/**
 * Host tool-render intent -> renderable card. The result view wins when it has arrived, because it
 * carries the outcome; otherwise the call view drives a running card. A tool with no view at all
 * still renders, as a generic card over its raw arguments.
 */
internal fun buildToolCardView(
    call: ToolCallNode,
    result: ToolResultNode?,
    callView: ToolEventView?,
    resultView: ToolEventView?,
    running: Boolean,
): ToolCardView {
    resultView
        ?.takeIf { it.for_ == ToolEventFor.RESULT }
        ?.view
        ?.let { return mapToolView(it, running = false) }
    callView
        ?.takeIf { it.for_ == ToolEventFor.CALL }
        ?.view
        ?.let { return mapToolView(it, running = running) }
    return ToolCardView.GenericCard(title = call.name, rawInput = call.arguments)
}

/** The presenter title a view carries, used as the tool row's verb when present. */
internal fun ToolEventView?.titleOrNull(): String? = when (val view = this?.view) {
    is GenericView -> view.title
    is TerminalView -> view.title
    is DiffView -> view.title
    is SearchView -> view.title
    is ReadView -> view.title
    is WebView -> view.title
    is UnknownView, null -> null
}

internal fun mapToolView(view: ToolView, running: Boolean = false): ToolCardView = when (view) {
    is GenericView -> ToolCardView.GenericCard(
        title = view.title,
        kind = view.kind,
        rawInput = view.rawInput?.toString(),
        locations = view.locations?.map { it.path },
        content = view.content?.map(::mapContentBlock),
    )
    is TerminalView -> ToolCardView.TerminalCard(
        title = view.title,
        description = view.description,
        cwd = view.cwd,
        output = view.output,
        exitCode = view.exitCode,
        signal = view.signal,
        running = view.running ?: running,
    )
    is DiffView -> ToolCardView.DiffCard(
        title = view.title,
        diffs = view.diffs.map { DiffHunk(it.path, it.oldText, it.newText) },
    )
    is SearchView -> ToolCardView.SearchCard(
        title = view.title,
        matches = if (view.shape == "paths") {
            SearchMatches.PathList(view.paths.orEmpty())
        } else {
            SearchMatches.FileMatches(
                view.files.orEmpty().map { file ->
                    SearchFile(
                        path = file.path,
                        matches = file.matches.map { SearchMatch(it.lineNumber, it.line) },
                    )
                },
            )
        },
        truncated = view.truncated,
        total = view.total,
    )
    is ReadView -> ToolCardView.ReadCard(
        label = view.title ?: view.path,
        path = view.path,
        lines = view.lines.map { ReadLine(it.number, it.text) },
        totalLines = view.totalLines,
        lang = view.lang,
    )
    is WebView -> ToolCardView.WebCard(
        title = view.title,
        kind = if (view.kind == "fetch") {
            WebCardKind.Fetch(view.url.orEmpty(), view.statusCode)
        } else {
            WebCardKind.Search(
                answer = view.answer,
                sources = view.sources.orEmpty().map { WebSource(it.url, it.title, it.snippet) },
            )
        },
    )
    is UnknownView -> ToolCardView.GenericCard(title = view.card, rawInput = view.raw.toString())
}

/**
 * One content block of a generic card. Images become a real raster: a tool that returns a
 * screenshot delivers it through this path, not through the message path.
 */
private fun mapContentBlock(block: ContentBlock): ContentBlockView = when (block) {
    is ContentBlock.Text -> ContentBlockView.TextBlock(block.text)
    is ContentBlock.Reasoning -> ContentBlockView.ReasoningBlock(block.text)
    is ContentBlock.Image -> ContentBlockView.ImageBlock(
        attachmentId = block.attachment.attachmentId,
        mediaType = block.attachment.mediaType,
        width = block.attachment.width,
        height = block.attachment.height,
        name = block.attachment.name,
    )
    is ContentBlock.ToolCall -> ContentBlockView.TextBlock("↳ ${block.name}")
    is ContentBlock.ToolResult -> ContentBlockView.TextBlock("↳ result")
    else -> ContentBlockView.TextBlock(block.toString())
}
