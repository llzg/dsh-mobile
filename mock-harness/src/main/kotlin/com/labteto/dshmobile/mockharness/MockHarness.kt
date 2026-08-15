package com.labteto.dshmobile.mockharness

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.host
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.send
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A scriptable stand-in for the DeepSeek Harness HTTP/WebSocket protocol.
 *
 * The harness exposes a JSON-RPC-flavored protocol on plain HTTP:
 *  - unary calls: `POST /api/<method>` with a `client-request` envelope, answered with a
 *    `server-response` envelope carrying `{"ok": true, "value": ...}` or
 *    `{"ok": false, "error": {"code", "message", "details"}}`;
 *  - answers: `POST /api/respond` with a `client-response` envelope -> `{"accepted": true}`;
 *  - downlink-only WebSockets `/api/events.mux` and `/api/events.host`, where the server
 *    pushes `server-request` frames and the client must not send.
 *
 * A trust fence rejects every POST whose Host header is neither loopback nor listed in
 * [trustedHosts] with HTTP 403, replicated before any dispatch.
 */
class MockHarness(
    private val trustedHosts: List<String> = emptyList(),
    private val port: Int = 0,
) {
    private val okHandlers = ConcurrentHashMap<String, (JsonElement) -> JsonElement>()
    private val failHandlers = ConcurrentHashMap<String, (JsonElement) -> RpcErrorData>()
    private val asyncHandlers = ConcurrentHashMap<String, suspend (JsonElement) -> JsonElement>()
    private val muxSockets = ConcurrentHashMap.newKeySet<WebSocketSession>()
    private val hostSockets = ConcurrentHashMap.newKeySet<WebSocketSession>()

    @Volatile
    private var describeTransform: ((JsonObject) -> JsonObject)? = null

    /** Body served by `GET /api/session.export`; tests set this to assert on the streamed bytes. */
    @Volatile
    var sessionExportBytes: ByteArray = ByteArray(0)

    private val normalizedTrustedHosts: Set<String> =
        trustedHosts.mapTo(mutableSetOf()) { normalizeHost(it) }

    private var server: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration>? = null

    init {
        on("host.describe") { describeValue() }
    }

    /**
     * Starts the server on [port] (0 lets the OS assign one) and returns the bound port.
     */
    suspend fun start(): Int {
        val newServer = embeddedServer(Netty, port = port, host = "127.0.0.1") {
            install(WebSockets)
            routing {
                get("/api/session.export") {
                    call.handleSessionExport()
                }
                post("/api/{method}") {
                    call.handleApi()
                }
                // The typert Remote gateway lives on a second path segment
                // (`/api/commands/execute`) but shares the ordinary envelope, so it maps onto the
                // same handler under the composed method name.
                post("/api/{namespace}/{method}") {
                    val namespace = call.parameters["namespace"].orEmpty()
                    val method = call.parameters["method"].orEmpty()
                    call.handleApi("$namespace/$method")
                }
                webSocket("/api/events.mux") {
                    handleMuxSocket()
                }
                webSocket("/api/events.host") {
                    handleHostSocket()
                }
            }
        }
        newServer.start(wait = false)
        server = newServer
        return newServer.engine.resolvedConnectors().first().port
    }

    /**
     * Stops the server. Safe to call even if [start] was never called.
     */
    suspend fun stop() {
        val current = server ?: return
        server = null
        withContext(Dispatchers.IO) {
            current.stop(gracePeriodMillis = 0, timeoutMillis = 0)
        }
    }

    /**
     * Registers a synchronous handler for [method]; the handler maps the request payload
     * to the `ok` value of the response. Replaces any previous handler for the method.
     */
    fun on(method: String, handler: (JsonElement) -> JsonElement) {
        okHandlers[method] = handler
        failHandlers.remove(method)
        asyncHandlers.remove(method)
    }

    /**
     * Registers a failing handler for [method]; the handler maps the request payload to the
     * `ok: false` error of the response. Replaces any previous handler for the method.
     */
    fun onFail(method: String, handler: (JsonElement) -> RpcErrorData) {
        failHandlers[method] = handler
        okHandlers.remove(method)
        asyncHandlers.remove(method)
    }

    /**
     * Registers a suspend handler for [method] (e.g. one that awaits an asynchronous answer).
     * Replaces any previous handler for the method.
     */
    fun sessionHistory(method: String, handler: suspend (JsonElement) -> JsonElement) {
        asyncHandlers[method] = handler
        okHandlers.remove(method)
        failHandlers.remove(method)
    }

    /**
     * Overrides the built-in `host.describe` handler: [transform] receives the default
     * describe value and returns the value that will be served.
     */
    fun describe(transform: (JsonObject) -> JsonObject) {
        describeTransform = transform
    }

    /**
     * Broadcasts a `server-request` frame to every connected `/api/events.mux` socket.
     *
     * The [frame] argument is the frame payload. If it is a JSON object carrying a string
     * `method` member, that member becomes the frame method and its `payload` member (when
     * present) the frame payload; otherwise the whole [frame] becomes the payload and the
     * method defaults to `"frame"`. Every broadcast gets a fresh `rpcId`.
     */
    suspend fun pushMux(frame: JsonElement) = broadcast(muxSockets, frame)

    /**
     * Broadcasts a `server-request` frame to every connected `/api/events.host` socket;
     * see [pushMux] for the frame shape.
     */
    suspend fun pushHost(frame: JsonElement) = broadcast(hostSockets, frame)

    /**
     * The session-log download: a plain `GET` answered with an attachment, not an RPC. Serves
     * whatever [sessionExportBytes] holds so a test can assert on the streamed content.
     */
    private suspend fun ApplicationCall.handleSessionExport() {
        if (!isTrustedHost(request.hostHeader())) {
            respondText("Forbidden", status = HttpStatusCode.Forbidden)
            return
        }
        val sessionId = request.queryParameters["sessionId"]
        if (sessionId.isNullOrBlank()) {
            respondText("missing sessionId", status = HttpStatusCode.BadRequest)
            return
        }
        response.header("Content-Disposition", "attachment; filename=\"dsh-session-$sessionId.zip\"")
        respondBytes(sessionExportBytes, ContentType.Application.Zip)
    }

    private suspend fun ApplicationCall.handleApi(pathMethodOverride: String? = null) {
        if (!isTrustedHost(request.hostHeader())) {
            respondText("Forbidden", status = HttpStatusCode.Forbidden)
            return
        }
        val pathMethod = pathMethodOverride ?: parameters["method"] ?: ""
        if (pathMethod == "respond") {
            respondJson("""{"accepted":true}""")
            return
        }
        val body = runCatching { receiveText() }.getOrDefault("")
        val envelope = runCatching { Json.parseToJsonElement(body) }.getOrNull() as? JsonObject
        val rpcId = (envelope?.get("rpcId") as? JsonPrimitive)?.contentOrNull
        val type = (envelope?.get("type") as? JsonPrimitive)?.contentOrNull
        if (rpcId == null || type != "client-request") {
            respondJson(errorEnvelope(rpcId.orEmpty(), "internal", "invalid client-request envelope"))
            return
        }
        val method = (envelope?.get("method") as? JsonPrimitive)?.contentOrNull ?: pathMethod
        val payload = envelope?.get("payload") ?: JsonNull
        when {
            asyncHandlers.containsKey(method) ->
                respondJson(okEnvelope(rpcId, asyncHandlers[method]!!(payload)))
            okHandlers.containsKey(method) ->
                respondJson(okEnvelope(rpcId, okHandlers[method]!!(payload)))
            failHandlers.containsKey(method) -> {
                val error = failHandlers[method]!!(payload)
                respondJson(errorEnvelope(rpcId, error.code, error.message, error.details))
            }
            else -> respondJson(errorEnvelope(rpcId, "internal", "unregistered $method"))
        }
    }

    private suspend fun WebSocketSession.handleMuxSocket() {
        muxSockets += this
        try {
            send(muxSubscribedHello())
            while (incoming.receiveCatching().isSuccess) {
                // The DSH protocol is downlink-only; frames from the client are discarded.
            }
        } finally {
            muxSockets -= this
        }
    }

    private suspend fun WebSocketSession.handleHostSocket() {
        hostSockets += this
        try {
            while (incoming.receiveCatching().isSuccess) {
                // The DSH protocol is downlink-only; frames from the client are discarded.
            }
        } finally {
            hostSockets -= this
        }
    }

    private suspend fun broadcast(sockets: MutableSet<WebSocketSession>, frame: JsonElement) {
        val frameObject = frame as? JsonObject
        val method = (frameObject?.get("method") as? JsonPrimitive)?.contentOrNull ?: "frame"
        val payload = frameObject?.get("payload") ?: frame
        val envelope = buildJsonObject {
            put("type", "server-request")
            put("rpcId", UUID.randomUUID().toString())
            put("method", method)
            put("payload", payload)
        }.toString()
        for (session in sockets) {
            try {
                session.send(envelope)
            } catch (ignored: Exception) {
                // A disconnected client must not abort the broadcast.
            }
        }
    }

    private fun describeValue(): JsonObject {
        val base = buildJsonObject {
            put("version", "0.1.0-rc.5")
            put("cwd", "C:\\demo")
            put("attachedSessions", 0)
            put("canOpenPath", true)
        }
        return describeTransform?.invoke(base) ?: base
    }

    private fun muxSubscribedHello(): String = buildJsonObject {
        put("type", "server-request")
        put("rpcId", UUID.randomUUID().toString())
        put("method", "session/subscribed")
        put(
            "payload",
            buildJsonObject {
                put("sessionId", "demo")
                put("lastSeq", -1)
            },
        )
    }.toString()

    private fun ApplicationRequest.hostHeader(): String =
        headers["Host"] ?: host()

    private fun isTrustedHost(rawHost: String): Boolean {
        val normalized = normalizeHost(rawHost)
        return normalized in LOOPBACK_HOSTS || normalized in normalizedTrustedHosts
    }

    private companion object {
        val LOOPBACK_HOSTS: Set<String> =
            setOf("localhost", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1")

        /** Normalizes a raw Host header value to a bare hostname/IP. */
        fun normalizeHost(raw: String): String {
            var value = raw.trim().lowercase()
            value = value.removePrefix("http://").removePrefix("https://")
            val at = value.lastIndexOf('@')
            if (at >= 0) {
                value = value.substring(at + 1)
            }
            return if (value.startsWith("[")) {
                val close = value.indexOf(']')
                if (close >= 0) value.substring(1, close) else value
            } else {
                val colon = value.indexOf(':')
                if (colon >= 0) value.substring(0, colon) else value
            }.trim()
        }
    }
}

private suspend fun ApplicationCall.respondJson(json: String) {
    respondText(json, ContentType.Application.Json, HttpStatusCode.OK)
}

private fun okEnvelope(rpcId: String, value: JsonElement): String = buildJsonObject {
    put("type", "server-response")
    put("rpcId", rpcId)
    put(
        "result",
        buildJsonObject {
            put("ok", true)
            put("value", value)
        },
    )
}.toString()

private fun errorEnvelope(
    rpcId: String,
    code: String,
    message: String,
    details: JsonObject = buildJsonObject { },
): String = buildJsonObject {
    put("type", "server-response")
    put("rpcId", rpcId)
    put(
        "result",
        buildJsonObject {
            put("ok", false)
            put(
                "error",
                buildJsonObject {
                    put("code", code)
                    put("message", message)
                    put("details", details)
                },
            )
        },
    )
}.toString()
