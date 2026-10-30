package com.vini.miniprinter.image

/** Rectangle in normalized (0..1) image coordinates. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top

    /** Same region after rotating the image 90° clockwise. */
    fun rotatedCw() = NormRect(1f - bottom, left, 1f - top, right)

    /** Same region after rotating the image 90° counter-clockwise. */
    fun rotatedCcw() = NormRect(top, 1f - right, bottom, 1f - left)

    fun mirrored() = NormRect(1f - right, top, 1f - left, bottom)

    enum class Handle { NONE, MOVE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    /**
     * Applies a drag of (dx, dy) normalized units on [handle].
     * [aspect] is the wanted width/height ratio in normalized units (null = free).
     */
    fun drag(handle: Handle, dx: Float, dy: Float, aspect: Float?): NormRect {
        if (handle == Handle.NONE) return this
        if (handle == Handle.MOVE) {
            val l = (left + dx).coerceIn(0f, 1f - width)
            val t = (top + dy).coerceIn(0f, 1f - height)
            return NormRect(l, t, l + width, t + height)
        }
        var l = left; var t = top; var r = right; var b = bottom
        val movesLeft = handle == Handle.TOP_LEFT || handle == Handle.BOTTOM_LEFT
        val movesTop = handle == Handle.TOP_LEFT || handle == Handle.TOP_RIGHT
        if (movesLeft) l = (l + dx).coerceIn(0f, r - MIN) else r = (r + dx).coerceIn(l + MIN, 1f)
        if (movesTop) t = (t + dy).coerceIn(0f, b - MIN) else b = (b + dy).coerceIn(t + MIN, 1f)
        if (aspect != null) {
            var w = r - l
            var h = w / aspect
            val maxH = if (movesTop) b else 1f - t
            if (h > maxH) { h = maxH; w = h * aspect }
            if (movesLeft) l = r - w else r = l + w
            if (movesTop) t = b - h else b = t + h
        }
        return NormRect(l, t, r, b)
    }

    companion object {
        const val MIN = 0.05f
        val FULL = NormRect(0f, 0f, 1f, 1f)

        /** Largest centered rect with normalized [aspect] (width/height). */
        fun centered(aspect: Float): NormRect {
            var w = 1f
            var h = 1f / aspect
            if (h > 1f) { h = 1f; w = aspect }
            val l = (1f - w) / 2f
            val t = (1f - h) / 2f
            return NormRect(l, t, l + w, t + h)
        }
    }
}
