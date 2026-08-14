@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.labteto.dshmobile.core.wire.dto

import com.labteto.dshmobile.core.wire.decodeFromJsonElement
import com.labteto.dshmobile.core.wire.encodeToJsonElement
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.buildSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tool render-intent vocabulary (tool call/result presentation cards), ported from the harness
 * `packages/core/tools/src/presentation.ts` render-intent vocabulary. A card-tagged union: a UI
 * switches on `card`. Unknown card types fall back to [UnknownView] carrying the raw JSON.
 */

/** Which side of a tool execution a [ToolEventView] describes. */
@Serializable
enum class ToolEventFor {
    @SerialName("call")
    CALL,

    @SerialName("result")
    RESULT,
}

/**
 * Host-computed render intent accompanying a `tool/call` or `tool/result` event. `for` names
 * which vocabulary applies without re-inspecting the event type.
 */
@Serializable
data class ToolEventView(
    @SerialName("for") val for_: ToolEventFor,
    @SerialName("view") val view: ToolView,
)

/** One card-tagged tool view; dispatch on [card]. */
@Serializable(with = ToolViewSerializer::class)
sealed class ToolView {
    /** The card discriminant ('generic' | 'terminal' | 'diff' | 'search' | 'read' | 'web'). */
    abstract val card: String
}

/** A file location a tool reads or modifies, for editor "follow-along". */
@Serializable
data class FileLocation(
    @SerialName("path") val path: String,
    /** Optional 1-based line to focus. */
    @SerialName("line") val line: Int? = null,
)

/** A single-file change a tool is about to make (or has made). */
@Serializable
data class FileDiff(
    @SerialName("path") val path: String,
    /** Prior content; absent/null for a new-file create or an overwrite. */
    @SerialName("oldText") val oldText: String? = null,
    /** Content after the change. */
    @SerialName("newText") val newText: String,
)

/** One numbered line of a file (the unit of a read card). */
@Serializable
data class ReadFileLine(
    /** 1-based line number in the file. */
    @SerialName("number") val number: Int,
    /** The line text without its trailing newline. */
    @SerialName("text") val text: String,
)

/** One matched line inside a search card's file group. */
@Serializable
data class SearchLineMatch(
    /** 1-based line number of the match within its file. */
    @SerialName("lineNumber") val lineNumber: Int,
    /** The matched line text. */
    @SerialName("line") val line: String,
)

/** One file's grouped content matches for a `shape: 'matches'` search card. */
@Serializable
data class SearchFileMatches(
    @SerialName("path") val path: String,
    @SerialName("matches") val matches: List<SearchLineMatch> = emptyList(),
)

/** One citeable source in a `kind: 'search'` web card. */
@Serializable
data class WebSource(
    @SerialName("url") val url: String,
    @SerialName("title") val title: String? = null,
    @SerialName("snippet") val snippet: String? = null,
    /** Provider-supplied ISO-8601 timestamp, when present. */
    @SerialName("publishedAt") val publishedAt: String? = null,
)

/** The default card: a titled row with an optional category, raw input, and content blocks. */
@Serializable
data class GenericView(
    @SerialName("card") override val card: String = "generic",
    @SerialName("title") val title: String? = null,
    /** Category for icon/treatment ('read' | 'search' | 'fetch' | 'execute' | 'other' | 'delete' | ...). */
    @SerialName("kind") val kind: String? = null,
    /** The salient input to show in a detail/expanded view. */
    @SerialName("rawInput") val rawInput: JsonElement? = null,
    /** Files this call reads/modifies, for editor follow-along. */
    @SerialName("locations") val locations: List<FileLocation>? = null,
    /** UI-facing content blocks to show on the card. */
    @SerialName("content") val content: List<ContentBlock>? = null,
) : ToolView()

/** A shell command card (call or completed result). */
@Serializable
data class TerminalView(
    @SerialName("card") override val card: String = "terminal",
    /** The command, or a replacement title for the completed call. */
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    /** Working directory the command runs in. */
    @SerialName("cwd") val cwd: String? = null,
    /** Captured command output (stdout+stderr as the tool chose to combine them). */
    @SerialName("output") val output: String? = null,
    /** Process exit code, when the run ended by exiting. */
    @SerialName("exitCode") val exitCode: Int? = null,
    /** Signal name that killed the process (e.g. `SIGTERM`). */
    @SerialName("signal") val signal: String? = null,
    /** True while the process is still running. */
    @SerialName("running") val running: Boolean? = null,
) : ToolView()

/** A file mutation rendered as an inline diff card. */
@Serializable
data class DiffView(
    @SerialName("card") override val card: String = "diff",
    @SerialName("title") val title: String? = null,
    /** One entry per file the call changes. */
    @SerialName("diffs") val diffs: List<FileDiff> = emptyList(),
) : ToolView()

/** A completed content/path search card; `shape` splits the two variants. */
@Serializable
data class SearchView(
    @SerialName("card") override val card: String = "search",
    /** 'matches' (grouped content matches) or 'paths' (flat path list). */
    @SerialName("shape") val shape: String,
    @SerialName("title") val title: String? = null,
    /** Present when `shape` is 'matches'. */
    @SerialName("files") val files: List<SearchFileMatches>? = null,
    /** Present when `shape` is 'paths'. */
    @SerialName("paths") val paths: List<String>? = null,
    /** Whether the tool capped the inline result. */
    @SerialName("truncated") val truncated: Boolean,
    /** Total matches/paths the search found before capping. */
    @SerialName("total") val total: Int,
) : ToolView()

/** A completed file read rendered as a line-numbered code view. */
@Serializable
data class ReadView(
    @SerialName("card") override val card: String = "read",
    @SerialName("label") val label: String,
    @SerialName("path") val path: String? = null,
    /** The returned window's lines, in file order. */
    @SerialName("lines") val lines: List<ReadFileLine> = emptyList(),
    /** Exact total line count in the file. */
    @SerialName("totalLines") val totalLines: Int,
    /** A syntax-highlighting language hint derived from the file extension. */
    @SerialName("lang") val lang: String? = null,
) : ToolView()

/** A completed web retrieval card; `kind` splits search ('search') from fetch ('fetch'). */
@Serializable
data class WebView(
    @SerialName("card") override val card: String = "web",
    /** 'search' | 'fetch'. */
    @SerialName("kind") val kind: String,
    @SerialName("title") val title: String? = null,
    /** Present when `kind` is 'search': the provider-generated answer. */
    @SerialName("answer") val answer: String? = null,
    /** Present when `kind` is 'search': the structured sources. */
    @SerialName("sources") val sources: List<WebSource>? = null,
    /** Present when `kind` is 'fetch': the final URL after allowed redirects. */
    @SerialName("url") val url: String? = null,
    /** Present when `kind` is 'fetch': HTTP status code of the fetched response. */
    @SerialName("statusCode") val statusCode: Int? = null,
    /** Present when `kind` is 'fetch': the fetched content. */
    @SerialName("content") val content: List<ContentBlock>? = null,
    @SerialName("truncated") val truncated: Boolean? = null,
) : ToolView()

/** An unknown card type preserved verbatim (loose interior passthrough). */
data class UnknownView(
    override val card: String,
    val raw: JsonElement,
) : ToolView()

/** Custom card-dispatching serializer for [ToolView]; unknown cards pass through raw. */
object ToolViewSerializer : KSerializer<ToolView> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ToolView") {
        element("card", buildSerialDescriptor("kotlin.String", PrimitiveKind.STRING))
    }

    override fun serialize(encoder: Encoder, value: ToolView) {
        val json: JsonElement = when (value) {
            is GenericView -> encodeToJsonElement(GenericView.serializer(), value)
            is TerminalView -> encodeToJsonElement(TerminalView.serializer(), value)
            is DiffView -> encodeToJsonElement(DiffView.serializer(), value)
            is SearchView -> encodeToJsonElement(SearchView.serializer(), value)
            is ReadView -> encodeToJsonElement(ReadView.serializer(), value)
            is WebView -> encodeToJsonElement(WebView.serializer(), value)
            is UnknownView -> value.raw
        }
        (encoder as JsonEncoder).encodeJsonElement(json)
    }

    override fun deserialize(decoder: Decoder): ToolView {
        val json = (decoder as JsonDecoder).decodeJsonElement().jsonObject
        val card = json["card"]?.jsonPrimitive?.contentOrNull ?: "generic"
        return when (card) {
            "generic" -> decodeFromJsonElement(GenericView.serializer(), json)
            "terminal" -> decodeFromJsonElement(TerminalView.serializer(), json)
            "diff" -> decodeFromJsonElement(DiffView.serializer(), json)
            "search" -> decodeFromJsonElement(SearchView.serializer(), json)
            "read" -> decodeFromJsonElement(ReadView.serializer(), json)
            "web" -> decodeFromJsonElement(WebView.serializer(), json)
            else -> UnknownView(card, json)
        }
    }
}

