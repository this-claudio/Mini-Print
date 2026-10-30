package com.vini.miniprinter.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.roundToInt

object ImageOps {

    /** Decodes [uri] into a software ARGB bitmap no larger than [maxDim], honoring EXIF rotation. */
    fun decode(context: Context, uri: Uri, maxDim: Int = 1600): Bitmap {
        val cr = context.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(cr, uri)
            return ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val longest = max(info.size.width, info.size.height)
                if (longest > maxDim) {
                    val s = maxDim.toFloat() / longest
                    decoder.setTargetSize((info.size.width * s).roundToInt(), (info.size.height * s).roundToInt())
                }
            }.let { it.copy(Bitmap.Config.ARGB_8888, false) }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("imagem inválida")
        val orientation = cr.openInputStream(uri).use { s ->
            s?.let { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        } ?: ExifInterface.ORIENTATION_NORMAL
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            else -> return bmp
        }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** Rotates by any angle (canvas grows, new corners white), then optionally mirrors horizontally. */
    fun transform(src: Bitmap, degrees: Float, mirror: Boolean): Bitmap {
        if (degrees % 360f == 0f && !mirror) return src
        val m = Matrix()
        m.postRotate(degrees)
        if (mirror) m.postScale(-1f, 1f)
        val bounds = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
        m.mapRect(bounds)
        m.postTranslate(-bounds.left, -bounds.top)
        val out = Bitmap.createBitmap(bounds.width().roundToInt(), bounds.height().roundToInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    fun crop(src: Bitmap, r: NormRect): Bitmap {
        if (r == NormRect.FULL) return src
        val x = (r.left * src.width).roundToInt().coerceIn(0, src.width - 1)
        val y = (r.top * src.height).roundToInt().coerceIn(0, src.height - 1)
        val w = (r.width * src.width).roundToInt().coerceIn(1, src.width - x)
        val h = (r.height * src.height).roundToInt().coerceIn(1, src.height - y)
        return Bitmap.createBitmap(src, x, y, w, h)
    }

    /** Halves repeatedly before the final bilinear step to avoid aliasing on big reductions. */
    fun scaleToWidth(src: Bitmap, width: Int): Bitmap {
        var cur = src
        while (cur.width / 2 >= width) {
            cur = Bitmap.createScaledBitmap(cur, cur.width / 2, max(1, cur.height / 2), true)
        }
        if (cur.width == width) return cur
        val h = max(1, (cur.height.toFloat() * width / cur.width).roundToInt())
        return Bitmap.createScaledBitmap(cur, width, h, true)
    }

    fun pixels(bmp: Bitmap): IntArray {
        val px = IntArray(bmp.width * bmp.height)
        bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
        return px
    }

    fun MonoImage.toBitmap(): Bitmap {
        val px = IntArray(width * height)
        for (y in 0 until height) for (x in 0 until width) {
            px[y * width + x] = if (this[x, y]) Color.BLACK else Color.WHITE
        }
        return Bitmap.createBitmap(px, width, height, Bitmap.Config.ARGB_8888)
    }
}
