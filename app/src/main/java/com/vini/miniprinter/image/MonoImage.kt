package com.vini.miniprinter.image

/**
 * 1-bit image packed the way thermal printers expect it:
 * rows of (width + 7) / 8 bytes, MSB = leftmost pixel, bit set = black dot.
 */
class MonoImage(
    val width: Int,
    val height: Int,
    val data: ByteArray = ByteArray(((width + 7) / 8) * height),
) {
    val bytesPerRow: Int get() = (width + 7) / 8

    init {
        require(width > 0 && height >= 0) { "invalid size ${width}x$height" }
        require(data.size == bytesPerRow * height) { "data size mismatch" }
    }

    operator fun get(x: Int, y: Int): Boolean =
        (data[y * bytesPerRow + (x shr 3)].toInt() and (0x80 ushr (x and 7))) != 0

    operator fun set(x: Int, y: Int, black: Boolean) {
        val i = y * bytesPerRow + (x shr 3)
        val mask = 0x80 ushr (x and 7)
        val v = data[i].toInt()
        data[i] = (if (black) v or mask else v and mask.inv()).toByte()
    }

    /**
     * Draws a black frame of [thickness] dots (0 = no line) with [margin] white dots between frame
     * and content, plus [bottomExtra] white rows under the content (polaroid style).
     */
    fun withBorder(thickness: Int, margin: Int, bottomExtra: Int = 0): MonoImage {
        val pad = thickness + margin
        if (pad <= 0 && bottomExtra <= 0) return this
        val out = MonoImage(width + 2 * pad, height + 2 * pad + bottomExtra)
        blit(this, out, pad, pad)
        if (thickness <= 0) return out
        for (y in 0 until out.height) {
            for (x in 0 until out.width) {
                val inFrame = x < thickness || y < thickness ||
                    x >= out.width - thickness || y >= out.height - thickness
                if (inFrame) out[x, y] = true
            }
        }
        return out
    }

    /** Centers (or center-crops) horizontally so the result is exactly [targetWidth] wide. */
    fun fitWidth(targetWidth: Int): MonoImage {
        if (targetWidth == width) return this
        val out = MonoImage(targetWidth, height)
        blit(this, out, (targetWidth - width) / 2, 0)
        return out
    }

    companion object {
        /** Vertically joins [top] and [bottom] (centered horizontally) with [gap] white rows between. */
        fun stack(top: MonoImage, bottom: MonoImage, gap: Int): MonoImage {
            val w = maxOf(top.width, bottom.width)
            val out = MonoImage(w, top.height + gap + bottom.height)
            blit(top, out, (w - top.width) / 2, 0)
            blit(bottom, out, (w - bottom.width) / 2, top.height + gap)
            return out
        }

        fun blit(src: MonoImage, dst: MonoImage, dx: Int, dy: Int) {
            for (y in 0 until src.height) {
                val ty = y + dy
                if (ty !in 0 until dst.height) continue
                for (x in 0 until src.width) {
                    val tx = x + dx
                    if (tx in 0 until dst.width && src[x, y]) dst[tx, ty] = true
                }
            }
        }
    }
}
