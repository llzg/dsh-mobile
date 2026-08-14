package com.labteto.dshmobile.core.wire.dto

import com.labteto.dshmobile.core.wire.WireJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolViewSerializerTest {

    private fun decodeToolView(json: JsonObject): ToolView =
        WireJson.decodeFromJsonElement(ToolView.serializer(), json)

    @Test
    fun decodesReadResultViewPerHarnessSchema() {
        val view = decodeToolView(
            buildJsonObject {
                put("card", "read")
                put("path", "src/Foo.kt")
                put("offset", 12)
                putJsonArray("lines") {
                    add(buildJsonObject { put("number", 12); put("text", "fun foo() = 1") })
                }
                put("totalLines", 42)
                put("lang", "kotlin")
            },
        )
        val read = view as ReadView
        assertEquals("src/Foo.kt", read.path)
        assertEquals(12, read.offset)
        assertEquals(1, read.lines.size)
        assertEquals(12, read.lines.first().number)
        assertEquals(42, read.totalLines)
        assertEquals("kotlin", read.lang)
        assertEquals(null, read.title)
    }

    @Test
    fun readViewDriftDegradesToUnknownViewInsteadOfFailing() {
        // A pre-title/offset host shape (e.g. `label` without `path`/`offset`) must not
        // blow up the whole response decode: the card falls back to the raw passthrough.
        val view = decodeToolView(
            buildJsonObject {
                put("card", "read")
                put("label", "src/Foo.kt")
                putJsonArray("lines") {
                    add(buildJsonObject { put("number", 1); put("text", "x") })
                }
                put("totalLines", 9)
            },
        )
        assertTrue(view is UnknownView)
        assertEquals("read", (view as UnknownView).card)
    }

    @Test
    fun readCardWithTitleDecodesTitle() {
        val view = decodeToolView(
            buildJsonObject {
                put("card", "read")
                put("title", "Read Foo.kt")
                put("path", "src/Foo.kt")
                put("offset", 1)
                putJsonArray("lines") { }
                put("totalLines", 0)
            },
        ) as ReadView
        assertEquals("Read Foo.kt", view.title)
    }
}
