package com.vini.miniprinter.image

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonoConverterTest {

    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    private fun blackRatio(img: MonoImage): Float {
        var n = 0
        for (y in 0 until img.height) for (x in 0 until img.width) if (img[x, y]) n++
        return n.toFloat() / (img.width * img.height)
    }

    @Test
    fun ditherKeepsMidGrayDensity() {
        val px = IntArray(64 * 64) { gray(128) }
        for (mode in listOf(DitherMode.FLOYD_STEINBERG, DitherMode.ATKINSON, DitherMode.BAYER)) {
            val r = blackRatio(MonoConverter.toMono(px, 64, 64, ToneSettings(mode = mode)))
            assertTrue("ratio $r", r in 0.4f..0.6f)
        }
    }

    @Test
    fun brightnessLightensOutput() {
        val px = IntArray(64 * 64) { gray(100) }
        val normal = blackRatio(MonoConverter.toMono(px, 64, 64, ToneSettings()))
        val bright = blackRatio(MonoConverter.toMono(px, 64, 64, ToneSettings(brightness = 0.5f)))
        assertTrue(bright < normal)
    }

    @Test
    fun transparentIsWhite() {
        val img = MonoConverter.toMono(IntArray(4) { 0 }, 2, 2, ToneSettings(mode = DitherMode.THRESHOLD))
        assertEquals(0f, blackRatio(img))
    }

    @Test
    fun adaptiveFindsDarkTextOnUnevenBackground() {
        val w = 96; val h = 32
        val px = IntArray(w * h) { i -> gray(120 + (i % w)) } // gradient background
        for (y in 12..18) for (x in 40..44) px[y * w + x] = gray(20) // "ink"
        val img = MonoConverter.toMono(px, w, h, ToneSettings(mode = DitherMode.ADAPTIVE))
        assertTrue(img[42, 15])
        assertTrue(!img[10, 5] && !img[90, 25])
    }
}
