package com.vini.miniprinter.image

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tan

enum class DitherMode(val label: String) {
    FLOYD_STEINBERG("Pontilhado"),
    ATKINSON("Atkinson"),
    BAYER("Bayer"),
    THRESHOLD("Limiar"),
    ADAPTIVE("Documento"),
}

/**
 * @param brightness -1..1
 * @param contrast -1..1
 * @param gamma 0.3..3 (>1 clareia meios-tons)
 * @param threshold 0..255, usado em THRESHOLD; em ADAPTIVE vira a sensibilidade
 */
data class ToneSettings(
    val brightness: Float = 0f,
    val contrast: Float = 0f,
    val gamma: Float = 1f,
    val mode: DitherMode = DitherMode.FLOYD_STEINBERG,
    val threshold: Int = 128,
)

data class TonePreset(val name: String, val brightness: Float, val contrast: Float, val gamma: Float) {
    fun matches(t: ToneSettings) =
        abs(t.brightness - brightness) < 0.001f && abs(t.contrast - contrast) < 0.001f && abs(t.gamma - gamma) < 0.001f

    fun applyTo(t: ToneSettings) = t.copy(brightness = brightness, contrast = contrast, gamma = gamma)
}

object TonePresets {
    val DEFAULT = TonePreset("Claro", 0.10f, 0.10f, 1.4f)
    val all = listOf(
        DEFAULT,
        TonePreset("Mais claro", 0.25f, 0.05f, 1.7f),
        TonePreset("Contraste", 0.10f, 0.40f, 1.3f),
        TonePreset("Suave", 0.15f, -0.20f, 1.5f),
        TonePreset("Original", 0f, 0f, 1f),
    )
}

object MonoConverter {

    /** ARGB pixels -> luminance 0..255, transparent pixels composited over white. */
    fun luminance(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val c = argb[i]
        val a = (c ushr 24) / 255f
        val r = (c shr 16) and 0xFF
        val g = (c shr 8) and 0xFF
        val b = c and 0xFF
        val y = 0.299f * r + 0.587f * g + 0.114f * b
        y * a + 255f * (1f - a)
    }

    fun applyTone(gray: FloatArray, s: ToneSettings): FloatArray {
        val contrastFactor = tan((s.contrast.coerceIn(-0.99f, 0.99f) + 1f) * PI.toFloat() / 4f)
        val invGamma = 1f / s.gamma.coerceIn(0.1f, 10f)
        return FloatArray(gray.size) { i ->
            var v = gray[i] / 255f
            v = v.coerceIn(0f, 1f).pow(invGamma)
            v = (v - 0.5f) * contrastFactor + 0.5f
            v += s.brightness * 0.5f
            v.coerceIn(0f, 1f) * 255f
        }
    }

    fun toMono(argb: IntArray, width: Int, height: Int, s: ToneSettings): MonoImage {
        require(argb.size == width * height)
        val gray = applyTone(luminance(argb), s)
        return when (s.mode) {
            DitherMode.FLOYD_STEINBERG -> floydSteinberg(gray, width, height)
            DitherMode.ATKINSON -> atkinson(gray, width, height)
            DitherMode.BAYER -> bayer(gray, width, height)
            DitherMode.THRESHOLD -> threshold(gray, width, height, s.threshold)
            DitherMode.ADAPTIVE -> adaptive(gray, width, height, s.threshold)
        }
    }

    fun threshold(gray: FloatArray, w: Int, h: Int, t: Int): MonoImage {
        val out = MonoImage(w, h)
        for (y in 0 until h) for (x in 0 until w) if (gray[y * w + x] < t) out[x, y] = true
        return out
    }

    fun floydSteinberg(src: FloatArray, w: Int, h: Int): MonoImage {
        val g = src.copyOf()
        val out = MonoImage(w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = y * w + x
                val old = g[i]
                val black = old < 128f
                if (black) out[x, y] = true
                val err = old - if (black) 0f else 255f
                if (x + 1 < w) g[i + 1] += err * 7f / 16f
                if (y + 1 < h) {
                    if (x > 0) g[i + w - 1] += err * 3f / 16f
                    g[i + w] += err * 5f / 16f
                    if (x + 1 < w) g[i + w + 1] += err * 1f / 16f
                }
            }
        }
        return out
    }

    /** Atkinson spreads only 6/8 of the error: lighter, crisper look that suits thermal paper. */
    fun atkinson(src: FloatArray, w: Int, h: Int): MonoImage {
        val g = src.copyOf()
        val out = MonoImage(w, h)
        fun add(x: Int, y: Int, e: Float) {
            if (x in 0 until w && y < h) g[y * w + x] += e
        }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val old = g[y * w + x]
                val black = old < 128f
                if (black) out[x, y] = true
                val e = (old - if (black) 0f else 255f) / 8f
                add(x + 1, y, e); add(x + 2, y, e)
                add(x - 1, y + 1, e); add(x, y + 1, e); add(x + 1, y + 1, e)
                add(x, y + 2, e)
            }
        }
        return out
    }

    /** Classic 8x8 ordered-dither index matrix (values 0..63). */
    private val BAYER_8 = intArrayOf(
        0, 32, 8, 40, 2, 34, 10, 42,
        48, 16, 56, 24, 50, 18, 58, 26,
        12, 44, 4, 36, 14, 46, 6, 38,
        60, 28, 52, 20, 62, 30, 54, 22,
        3, 35, 11, 43, 1, 33, 9, 41,
        51, 19, 59, 27, 49, 17, 57, 25,
        15, 47, 7, 39, 13, 45, 5, 37,
        63, 31, 55, 23, 61, 29, 53, 21,
    )

    /** Ordered dither: regular cross-hatch pattern, no error spreading, so dots never clump. */
    fun bayer(gray: FloatArray, w: Int, h: Int): MonoImage {
        val out = MonoImage(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val t = (BAYER_8[(y and 7) * 8 + (x and 7)] + 0.5f) * 4f
            if (gray[y * w + x] < t) out[x, y] = true
        }
        return out
    }

    /**
     * Local-mean threshold (integral image). Good for photos of documents with uneven lighting:
     * a pixel is black when darker than its neighbourhood mean minus an offset.
     * [sensitivity] 0..255 maps to offset: 128 = default.
     */
    fun adaptive(gray: FloatArray, w: Int, h: Int, sensitivity: Int): MonoImage {
        val integral = DoubleArray((w + 1) * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0.0
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[(y + 1) * (w + 1) + x + 1] = integral[y * (w + 1) + x + 1] + rowSum
            }
        }
        val r = max(6, w / 24)
        val offset = 4f + (255 - sensitivity) / 255f * 24f
        val out = MonoImage(w, h)
        for (y in 0 until h) {
            val y0 = max(0, y - r); val y1 = min(h, y + r + 1)
            for (x in 0 until w) {
                val x0 = max(0, x - r); val x1 = min(w, x + r + 1)
                val sum = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] -
                    integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                val mean = (sum / ((x1 - x0) * (y1 - y0))).toFloat()
                if (gray[y * w + x] < mean - offset) out[x, y] = true
            }
        }
        return out
    }
}
