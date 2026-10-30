package com.vini.miniprinter.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonoImageTest {

    @Test
    fun msbIsLeftmostPixel() {
        val img = MonoImage(16, 1)
        img[0, 0] = true
        img[9, 0] = true
        assertEquals(0x80.toByte(), img.data[0])
        assertEquals(0x40.toByte(), img.data[1])
        img[0, 0] = false
        assertEquals(0, img.data[0].toInt())
    }

    @Test
    fun borderKeepsPrinterWidth() {
        val border = BorderSettings(enabled = true, thickness = 2, margin = 4)
        val content = MonoImage(384 - 2 * border.pad, 10)
        val out = content.withBorder(border.thickness, border.margin)
        assertEquals(384, out.width)
        assertEquals(10 + 12, out.height)
        assertTrue(out[0, 0] && out[1, 5] && out[383, 21])
        assertFalse(out[2, 2]) // margin is white
        assertFalse(out[6, 6]) // content is white
    }

    @Test
    fun polaroidAddsBottomSpaceAndZeroLineDrawsNoFrame() {
        val content = MonoImage(352, 10)
        val polaroid = content.withBorder(0, 16, 100)
        assertEquals(384, polaroid.width)
        assertEquals(10 + 32 + 100, polaroid.height)
        assertFalse(polaroid[0, 0] || polaroid[383, polaroid.height - 1])
        val framed = content.withBorder(2, 14, 100)
        assertTrue(framed[0, framed.height - 1])
    }

    @Test
    fun stackCentersNarrowerImage() {
        val a = MonoImage(16, 2)
        val b = MonoImage(8, 3).also { it[0, 0] = true }
        val s = MonoImage.stack(a, b, 4)
        assertEquals(16, s.width)
        assertEquals(9, s.height)
        assertTrue(s[4, 6])
    }

    @Test
    fun cropRotationRoundTrips() {
        val r = NormRect(0.1f, 0.2f, 0.5f, 0.9f)
        val back = r.rotatedCw().rotatedCcw()
        assertEquals(r.left, back.left, 1e-6f)
        assertEquals(r.bottom, back.bottom, 1e-6f)
    }

    @Test
    fun aspectDragStaysInsideImage() {
        val r = NormRect(0.2f, 0.2f, 0.6f, 0.6f).drag(NormRect.Handle.BOTTOM_RIGHT, 0.9f, 0f, 1f)
        assertTrue(r.right <= 1f && r.bottom <= 1f)
        assertEquals(r.width, r.height, 1e-5f)
    }
}
