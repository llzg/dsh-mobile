package com.labteto.dshmobile.core.wire

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.SerializationException
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * One HTTP carrier exchange: the status code and raw body of a POST /api request.
 * HTTP status is carrier-only — business errors arrive as HTTP 200 with `ok: false` in the body.
 */
data class RpcHttpResponse(
    val status: Int,
    val body: String,
)

/** Thrown by [RpcTransport] on a non-2xx carrier response or a network-level failure. */
class RpcTransportException(
    val status: Int,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)

/** The HTTP carrier for unary RPCs: one `POST /api/<path>` exchange. */
interface RpcTransport {
    /**
     * POST [body] to [path] (e.g. "/api/session.list"). Returns the carrier response, or throws
     * [RpcTransportException] on a non-2xx status or transport failure.
     */
    suspend fun post(path: String, body: String): RpcHttpResponse
}

/** JSON media type used for every /api POST. */
private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()

/**
 * OkHttp-backed [RpcTransport]. Sends `Content-Type: application/json`, sets the `Host` header
 * from the base URL, and times out at 30s. Non-2xx responses throw [RpcTransportException]
 * (403 mentions the harness trust fence).
 */
class OkHttpRpcTransport(
    baseUrl: String,
    client: OkHttpClient = defaultClient(),
) : RpcTransport {

    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val hostHeader: String = run {
        val defaultPort = when (base.scheme) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
        if (base.port == defaultPort) base.host else "${base.host}:${base.port}"
    }
    private val httpClient: OkHttpClient = client.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    override suspend fun post(path: String, body: String): RpcHttpResponse =
        suspendCancellableCoroutine { continuation ->
            val target = base.resolve(path)
                ?: throw RpcTransportException(0, "cannot resolve $path against $base")
            val request = Request.Builder()
                .url(target)
                .header("Host", hostHeader)
                .header("Content-Type", "application/json")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            RpcTransportException(0, "transport failure: ${e.message}", e),
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val responseBody = resp.body?.string().orEmpty()
                        if (resp.isSuccessful) {
                            continuation.resume(RpcHttpResponse(resp.code, responseBody))
                        } else {
                            val message = if (resp.code == 403) {
                                "harness trust fence rejected the request (HTTP 403)"
                            } else {
                                "carrier returned HTTP ${resp.code}"
                            }
                            continuation.resumeWithException(RpcTransportException(resp.code, message))
                        }
                    }
                }
            })
        }

    private companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** Receives downlink WebSocket events; all callbacks may run on OkHttp's socket threads. */
interface WsDownlinkSink {
    /** One parsed downstream frame (`type: server-request`). */
    fun onFrame(frame: ServerRequest)

    /** The WebSocket handshake completed and the socket is ready. */
    fun onOpen()

    /**
     * The socket closed or failed. `cause` is non-null on a failure, null on a clean close.
     * The loop treats this as the end of the stream's generation.
     */
    fun onClosed(cause: Throwable?)
}

/**
 * A downlink-only WebSocket to `/api/events.mux` or `/api/events.host`. The client never sends
 * application data — sending any would make the server close the socket with code 1008 — so this
 * class only performs the handshake and reads frames.
 */
open class WsDownlink(
    private val url: String,
    private val client: OkHttpClient,
    private val sink: WsDownlinkSink,
) {
    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var started: Boolean = false

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            sink.onOpen()
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val frame = try {
                decodeServerRequest(text)
            } catch (e: SerializationException) {
                // Protocol drift on the downlink: terminate this generation so the loop reconnects.
                webSocket.cancel()
                sink.onClosed(e)
                return
            } catch (e: IllegalArgumentException) {
                webSocket.cancel()
                sink.onClosed(e)
                return
            }
            sink.onFrame(frame)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            sink.onClosed(null)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            sink.onClosed(t)
        }
    }

    /** Perform the RFC 6455 handshake and begin reading frames. Idempotent. */
    fun start() {
        if (started) return
        started = true
        val request = Request.Builder().url(url).build()
        webSocket = client.newWebSocket(request, listener)
    }

    /** Tear the socket down. Idempotent. */
    fun close() {
        webSocket?.cancel()
        webSocket = null
    }
}
