package com.labteto.dshmobile.core.wire.dto

import com.labteto.dshmobile.core.wire.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The attachment reference describes what the host *stored*, which since harness 0.1.1-rc.2 can
 * be a normalized re-encode of the upload. These tests pin that the pre-normalization size rides
 * along when present and that its absence stays an ordinary decode.
 */
class SessionsDtosTest {

    @Test
    fun `an attachment ref carries the upload's size when the host scaled it`() {
        val ref = decodeFromString<ImageAttachmentRef>(
            """{"attachmentId":"sha256:abc","mediaType":"image/webp","bytes":123456,
               "width":2048,"height":1536,
               "originalDimensions":{"width":8000,"height":6000}}""",
        )
        assertEquals(2048, ref.width)
        assertEquals(8000, ref.originalDimensions!!.width)
        assertEquals(6000, ref.originalDimensions!!.height)
    }

    @Test
    fun `a ref without originalDimensions decodes as before`() {
        val ref = decodeFromString<ImageAttachmentRef>(
            """{"attachmentId":"sha256:abc","mediaType":"image/png","bytes":10,
               "width":100,"height":100}""",
        )
        assertNull(ref.originalDimensions)
    }
}
