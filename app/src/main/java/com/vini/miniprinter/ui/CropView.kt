package com.vini.miniprinter.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vini.miniprinter.image.NormRect
import com.vini.miniprinter.image.NormRect.Handle
import kotlin.math.hypot

/** Touch area extends this far beyond the image so corner handles sitting on the edge are easy to grab. */
private val TOUCH_MARGIN = 28.dp

/**
 * Image with a draggable crop rectangle (corners resize, inside moves).
 * [aspect] is width/height in normalized units, null = free.
 */
@Composable
fun CropView(bitmap: ImageBitmap, crop: NormRect, aspect: Float?, onChange: (NormRect) -> Unit) {
    val latestCrop by rememberUpdatedState(crop)
    val latestAspect by rememberUpdatedState(aspect)
    val latestOnChange by rememberUpdatedState(onChange)

    Box(
        Modifier
            // Corners near the screen edge would otherwise trigger Android's back gesture.
            .systemGestureExclusion()
            .pointerInput(Unit) {
                val margin = TOUCH_MARGIN.toPx()
                val touchRadius = 40.dp.toPx()
                var handle = Handle.NONE
                var working = latestCrop
                detectDragGestures(
                    onDragStart = { p ->
                        working = latestCrop
                        val w = size.width - 2 * margin
                        val h = size.height - 2 * margin
                        val x = p.x - margin
                        val y = p.y - margin
                        fun dist(cx: Float, cy: Float) = hypot(x - cx * w, y - cy * h)
                        val corners = with(working) {
                            listOf(
                                Handle.TOP_LEFT to dist(left, top),
                                Handle.TOP_RIGHT to dist(right, top),
                                Handle.BOTTOM_LEFT to dist(left, bottom),
                                Handle.BOTTOM_RIGHT to dist(right, bottom),
                            )
                        }
                        val nearest = corners.minBy { it.second }
                        handle = when {
                            nearest.second < touchRadius -> nearest.first
                            with(working) { x in left * w..right * w && y in top * h..bottom * h } -> Handle.MOVE
                            else -> Handle.NONE
                        }
                    },
                    onDrag = { change, amount ->
                        if (handle != Handle.NONE) {
                            change.consume()
                            val w = size.width - 2 * margin
                            val h = size.height - 2 * margin
                            working = working.drag(handle, amount.x / w, amount.y / h, latestAspect)
                            latestOnChange(working)
                        }
                    },
                )
            }
            .padding(TOUCH_MARGIN),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.aspectRatio(bitmap.width.toFloat() / bitmap.height)) {
            Image(bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(Modifier.fillMaxSize()) {
                val l = crop.left * size.width
                val t = crop.top * size.height
                val r = crop.right * size.width
                val b = crop.bottom * size.height
                val shade = Color.Black.copy(alpha = 0.55f)
                drawRect(shade, Offset.Zero, Size(size.width, t))
                drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                drawRect(shade, Offset(0f, t), Size(l, b - t))
                drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                val thin = 1.dp.toPx()
                for (i in 1..2) {
                    val x = l + (r - l) * i / 3f
                    val y = t + (b - t) * i / 3f
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(x, t), Offset(x, b), thin)
                    drawLine(Color.White.copy(alpha = 0.6f), Offset(l, y), Offset(r, y), thin)
                }
                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(2.dp.toPx()))
                val dot = 9.dp.toPx()
                listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
                    drawCircle(Brand.Yellow, dot, it)
                    drawCircle(Brand.Ink, dot, it, style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
