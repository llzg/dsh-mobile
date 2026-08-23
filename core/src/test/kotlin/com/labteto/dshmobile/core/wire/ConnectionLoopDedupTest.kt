package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.HostDescription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * Case D of the 0.8.3 recovery matrix: repeated reconnects must never create duplicate
 * subscriptions. `start()` is idempotent, and a fresh connection replaces the old loop entirely.
 */
class ConnectionLoopDedupTest {

    private class FakeWs(private val sink: WsDownlinkSink) : WsDownlink(
        "http://stub/api/events.mux",
        OkHttpClient(),
        sink,
    ) {
        override fun start() = sink.onOpen()
        override fun close() = Unit
    }

    private class StubTransport : RpcTransport {
        override suspend fun post(path: String, body: String): RpcHttpResponse = RpcHttpResponse(
            200,
            """{"type":"server-response","rpcId":"r","result":{"ok":true,"value":""" +
                """{"version":"0.1.1-rc.2","cwd":"/tmp","attachedSessions":0,""" +
                """"home":"/home/demo","canOpenPath":false}}}""",
        )
        override suspend fun <T> download(path: String, consume: (String?, String?, InputStream) -> T): T =
            error("not used")
    }

    private class Recorder : LoopSinks {
        val connected = CopyOnWriteArrayList<HostDescription>()
        override fun onMuxFrame(frame: ServerRequest) = Unit
        override fun onHostFrame(frame: ServerRequest) = Unit
        override fun onConnected(description: HostDescription) {
            connected.add(description)
        }
        override fun onStateChange(state: ConnectionState) = Unit
    }

    private suspend fun await(predicate: () -> Boolean): Boolean =
        withTimeoutOrNull(5_000) {
            while (!predicate()) kotlinx.coroutines.delay(5)
            true
        } ?: false

    @Test
    fun `starting twice opens exactly one generation`() = runBlocking {
        val opens = AtomicInteger(0)
        val recorder = Recorder()
        val api = DshApiClient(
            transport = StubTransport(),
            wsFactory = { _, sink ->
                opens.incrementAndGet()
                FakeWs(sink)
            },
        )
        val loop = ConnectionLoop(
            api = api,
            sinks = recorder,
            config = LoopConfig(streamOpenTimeoutMs = 30, delay = { }),
        )
        loop.start()
        loop.start() // must be a no-op: same job, no second generation.
        assertTrue("loop never connected", await { recorder.connected.isNotEmpty() })
        // One generation = exactly two sockets (mux + host). A duplicate subscription would open four.
        assertEquals(2, opens.get())
        loop.stop()
    }
}
