package com.labteto.dshmobile.mockharness

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.WebSocket
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

class MockHarnessTest {

    companion object {
        private lateinit var harness: MockHarness
        private var port: Int = -1
        private lateinit var http: HttpClient

        @JvmStatic
        @BeforeClass
        fun setUp() {
            // java.net.http forbids setting the Host header by default; lift that
            // restriction so the trust-fence test can send a mismatched Host.
            System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host")
            http = HttpClient.newHttpClient()
            runBlocking {
                harness = MockHarness(port = 0)
                port = harness.start()
            }
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            if (::harness.isInitialized) {
                runBlocking { harness.stop() }
            }
        }
    }

    @Test
    fun hostDescribeReturnsVersion() {
        val response = post("/api/host.describe", envelope("host.describe"))
        assertEquals(200, response.statusCode())
        val body = Json.parseToJsonElement(response.body()).jsonObject
        assertEquals("server-response", body["type"]!!.jsonPrimitive.content)
        val result = body["result"]!!.jsonObject
        assertTrue(result["ok"]!!.jsonPrimitive.boolean)
        val value = result["value"]!!.jsonObject
        assertEquals("0.1.0-rc.5", value["version"]!!.jsonPrimitive.content)
        assertEquals("C:\\demo", value["cwd"]!!.jsonPrimitive.content)
        assertEquals(0, value["attachedSessions"]!!.jsonPrimitive.int)
        assertTrue(value["canOpenPath"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun unregisteredMethodReturnsInternalError() {
        val response = post("/api/no.such.method", envelope("no.such.method"))
        assertEquals(200, response.statusCode())
        val result = Json.parseToJsonElement(response.body()).jsonObject["result"]!!.jsonObject
        assertEquals(false, result["ok"]!!.jsonPrimitive.boolean)
        val error = result["error"]!!.jsonObject
        assertEquals("internal", error["code"]!!.jsonPrimitive.content)
        assertEquals("unregistered no.such.method", error["message"]!!.jsonPrimitive.content)
    }

    @Test
    fun untrustedHostHeaderIsRejectedWith403() {
        val response = post("/api/host.describe", envelope("host.describe"), hostHeader = "evil.example.com")
        assertEquals(403, response.statusCode())
    }

    @Test
    fun respondEndpointAccepts() {
        val rpcId = UUID.randomUUID().toString()
        val body = """{"type":"client-response","rpcId":"$rpcId","result":{"ok":true,"value":{}}}"""
        val response = post("/api/respond", body)
        assertEquals(200, response.statusCode())
        val json = Json.parseToJsonElement(response.body()).jsonObject
        assertEquals(true, json["accepted"]!!.jsonPrimitive.boolean)
    }

    @Test
    fun muxWebSocketReceivesSubscribedHello() {
        val received = CompletableFuture<String>()
        val listener = object : WebSocket.Listener {
            override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): java.util.concurrent.CompletionStage<*>? {
                received.complete(data.toString())
                webSocket.request(1)
                return null
            }
        }
        val webSocket = http.newWebSocketBuilder()
            .buildAsync(URI.create("ws://127.0.0.1:$port/api/events.mux"), listener)
            .get(5, TimeUnit.SECONDS)
        try {
            val frame = Json.parseToJsonElement(received.get(5, TimeUnit.SECONDS)).jsonObject
            assertEquals("server-request", frame["type"]!!.jsonPrimitive.content)
            assertTrue(frame["rpcId"]!!.jsonPrimitive.content.isNotBlank())
            assertEquals("session/subscribed", frame["method"]!!.jsonPrimitive.content)
            val payload = frame["payload"]!!.jsonObject
            assertEquals("demo", payload["sessionId"]!!.jsonPrimitive.content)
            assertEquals(-1, payload["lastSeq"]!!.jsonPrimitive.int)
        } finally {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "done")
        }
    }

    private fun post(path: String, body: String, hostHeader: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$path"))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .header("Content-Type", "application/json")
        if (hostHeader != null) {
            builder.header("Host", hostHeader)
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString())
    }

    private fun envelope(method: String, payload: String = "{}"): String =
        """{"type":"client-request","rpcId":"${UUID.randomUUID()}","method":"$method","payload":$payload}"""
}
