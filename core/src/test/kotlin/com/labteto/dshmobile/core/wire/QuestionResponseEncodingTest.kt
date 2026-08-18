package com.labteto.dshmobile.core.wire

import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswer
import com.labteto.dshmobile.core.wire.dto.AskUserQuestionAnswerItem
import com.labteto.dshmobile.core.wire.dto.QUESTION_CANCELLED
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two envelopes that settle a question request.
 *
 * Both are shapes the host parses strictly and then discards what it does not know: `custom`
 * written one level out of place was accepted, stripped, and lost, and a dismissal sent as an
 * ordinary answer is not a dismissal at all — the model reads an empty selection as "no
 * preference". Neither failure announces itself, so both are pinned here.
 */
class QuestionResponseEncodingTest {

    private class RecordingTransport : RpcTransport {
        var lastPath: String? = null
        var lastBody: String? = null

        override suspend fun post(path: String, body: String): RpcHttpResponse {
            lastPath = path
            lastBody = body
            return RpcHttpResponse(status = 200, body = """{"accepted":true}""")
        }

        override suspend fun <T> download(
            path: String,
            consume: (String?, String?, InputStream) -> T,
        ): T = consume(null, null, ByteArrayInputStream(ByteArray(0)))
    }

    private fun client(transport: RpcTransport) = DshApiClient(
        transport = transport,
        wsFactory = { _, _ -> error("not used") },
    )

    private fun body(transport: RecordingTransport) =
        Json.parseToJsonElement(transport.lastBody!!).jsonObject

    @Test
    fun `custom rides its own answer, not the list beside it`() = runTest {
        val transport = RecordingTransport()
        val answer = AskUserQuestionAnswer(
            listOf(
                AskUserQuestionAnswerItem("profile", listOf("Alpha")),
                AskUserQuestionAnswerItem("detail", emptyList(), "in my own words"),
            ),
        )
        val value = Json.parseToJsonElement(
            encodeToString(AskUserQuestionAnswer.serializer(), answer),
        )
        client(transport).respond("rpc-1", value)

        assertEquals("/api/respond", transport.lastPath)
        val envelope = body(transport)
        assertEquals("client-response", envelope["type"]!!.jsonPrimitive.content)
        assertEquals("rpc-1", envelope["rpcId"]!!.jsonPrimitive.content)
        val answers = envelope["result"]!!.jsonObject["value"]!!.jsonObject["answers"]!!.jsonArray
        assertNull(answers[0].jsonObject["custom"])
        assertEquals("in my own words", answers[1].jsonObject["custom"]!!.jsonPrimitive.content)
        // The batch itself carries nothing but the list; a sibling key here is what went missing.
        assertEquals(setOf("answers"), envelope["result"]!!.jsonObject["value"]!!.jsonObject.keys)
    }

    @Test
    fun `an answer with no free text omits the key rather than sending null`() {
        val json = encodeToString(
            AskUserQuestionAnswer.serializer(),
            AskUserQuestionAnswer(listOf(AskUserQuestionAnswerItem("a", listOf("Alpha")))),
        )
        assertFalse(json.contains("custom"))
        // `selected` is not optional the same way: an omitted list fails the host's schema.
        assertTrue(json.contains("\"selected\""))
    }

    @Test
    fun `a dismissal posts an ok-false result whose code is exactly cancelled`() = runTest {
        val transport = RecordingTransport()
        client(transport).respondError("rpc-2", QUESTION_CANCELLED)

        val result = body(transport)["result"]!!.jsonObject
        assertEquals(false, result["ok"]!!.jsonPrimitive.content.toBoolean())
        val error = result["error"]!!.jsonObject
        assertEquals("cancelled", error["code"]!!.jsonPrimitive.content)
        assertEquals("the user closed this question request", error["message"]!!.jsonPrimitive.content)
        // `details` is required by the schema even when there is nothing to put in it.
        assertTrue(error["details"]!!.jsonObject.isEmpty())
    }
}
