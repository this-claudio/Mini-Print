package com.vini.miniprinter.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CancellationException
import java.io.Closeable
import kotlin.math.roundToInt

/** Pages of a document (PDF or a single image) rendered to a given width. */
interface DocSource : Closeable {
    val pageCount: Int

    /** Page [index] scaled to [width]; [landscape] rotates it 90° first so lines run along the paper. */
    suspend fun render(index: Int, width: Int, landscape: Boolean): Bitmap

    companion object {
        fun open(context: Context, uri: Uri, mime: String): DocSource =
            if (mime == "application/pdf") PdfSource(context, uri) else ImageSource(ImageOps.decode(context, uri, 2400))
    }
}

private class ImageSource(private val bitmap: Bitmap) : DocSource {
    override val pageCount = 1

    override suspend fun render(index: Int, width: Int, landscape: Boolean): Bitmap {
        val src = if (landscape) ImageOps.transform(bitmap, 90f, false) else bitmap
        return ImageOps.scaleToWidth(src, width)
    }

    override fun close() = Unit
}

private class PdfSource(context: Context, uri: Uri) : DocSource {
    private val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")
        ?: error("não foi possível abrir o PDF")
    private val renderer = PdfRenderer(fd)

    // PdfRenderer allows one open page at a time and must not be closed mid-render
    // (the screen may close it while a page is still rendering in the background).
    private val lock = Any()
    private var closed = false

    override val pageCount = renderer.pageCount

    override suspend fun render(index: Int, width: Int, landscape: Boolean): Bitmap = synchronized(lock) {
        if (closed) throw CancellationException("documento fechado")
        renderer.openPage(index).use { page ->
            // Render at 2x the final width, then downscale: crisper text than rendering at target size.
            val targetW = width * 2
            val pageW = if (landscape) page.height else page.width
            val scale = targetW.toFloat() / pageW
            val w = (page.width * scale).roundToInt().coerceAtLeast(1)
            val h = (page.height * scale).roundToInt().coerceAtLeast(1)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            val oriented = if (landscape) ImageOps.transform(bmp, 90f, false) else bmp
            ImageOps.scaleToWidth(oriented, width)
        }
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        renderer.close()
        fd.close()
    }
}
