package com.vini.miniprinter.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

data class BorderSettings(
    val enabled: Boolean = true,
    val thickness: Int = 2,
    val margin: Int = 4,
    val polaroid: Boolean = false,
    val polaroidBottom: Int = 100,
) {
    val pad get() = if (enabled) thickness + margin else 0
    val bottomExtra get() = if (enabled && polaroid) polaroidBottom else 0
}

data class Caption(
    val text: String = "",
    val below: Boolean = true,
    val style: TextStyle = TextStyle(sizePx = 26, align = Layout.Alignment.ALIGN_CENTER),
)

data class PhotoEdit(
    val caption: Caption = Caption(),
    val rotation90: Int = 0,
    val fineRotation: Float = 0f,
    val mirror: Boolean = false,
    val crop: NormRect = NormRect.FULL,
    // Thermal paper prints darker than the on-screen preview, so photos start lighter.
    val tone: ToneSettings = TonePresets.DEFAULT.applyTo(ToneSettings()),
    val border: BorderSettings = BorderSettings(enabled = false),
) {
    val totalRotation get() = rotation90 * 90f + fineRotation
}

enum class TextFont(val label: String) {
    SANS("Sans"),
    SERIF("Serif"),
    MONO("Mono");

    val typeface: Typeface
        get() = when (this) {
            SANS -> Typeface.SANS_SERIF
            SERIF -> Typeface.SERIF
            MONO -> bundledMono ?: Typeface.MONOSPACE
        }

    companion object {
        /**
         * Set at startup. Bundled because system MONOSPACE falls back to the sans face on many
         * OEM ROMs/font themes and has no real bold.
         */
        @Volatile
        var bundledMono: Typeface? = null
    }
}

data class TextStyle(
    val sizePx: Int = 28,
    val bold: Boolean = false,
    val align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
    val font: TextFont = TextFont.SANS,
    val border: BorderSettings = BorderSettings(enabled = false),
)

/** Everything ends as a MonoImage exactly [printerWidth] dots wide (border included). */
object Pipeline {

    fun finish(scaled: Bitmap, tone: ToneSettings, border: BorderSettings): MonoImage {
        val mono = MonoConverter.toMono(ImageOps.pixels(scaled), scaled.width, scaled.height, tone)
        return if (border.enabled) mono.withBorder(border.thickness, border.margin, border.bottomExtra) else mono
    }

    /** [geom] is the source already rotated/mirrored by [PhotoEdit.totalRotation]. */
    fun photo(geom: Bitmap, edit: PhotoEdit, printerWidth: Int): MonoImage {
        val border = edit.border
        val contentWidth = printerWidth - 2 * border.pad
        val scaled = ImageOps.scaleToWidth(ImageOps.crop(geom, edit.crop), contentWidth)
        val photo = MonoConverter.toMono(ImageOps.pixels(scaled), scaled.width, scaled.height, edit.tone)
        val cap = edit.caption.takeIf { it.text.isNotBlank() }?.let { textMono(it.text, it.style, contentWidth) }

        if (cap != null && edit.caption.below && border.bottomExtra > 0) {
            // Polaroid: write the caption in the big bottom strip instead of growing the print.
            val bottom = maxOf(border.bottomExtra, cap.height + 2 * CAPTION_GAP)
            val out = photo.withBorder(border.thickness, border.margin, bottom)
            MonoImage.blit(cap, out, border.pad, border.pad + photo.height + (bottom - cap.height) / 2)
            return out
        }
        val body = when {
            cap == null -> photo
            edit.caption.below -> MonoImage.stack(photo, cap, CAPTION_GAP)
            else -> MonoImage.stack(cap, photo, CAPTION_GAP)
        }
        return if (border.enabled) body.withBorder(border.thickness, border.margin, border.bottomExtra) else body
    }

    private const val CAPTION_GAP = 8

    private fun textMono(text: String, style: TextStyle, width: Int): MonoImage {
        val bmp = textBitmap(text, width, style)
        return MonoConverter.toMono(ImageOps.pixels(bmp), bmp.width, bmp.height, TEXT_TONE)
    }

    private val TEXT_TONE = ToneSettings(mode = DitherMode.THRESHOLD, threshold = 150)

    fun photoFromUri(context: Context, uri: Uri, edit: PhotoEdit, printerWidth: Int): MonoImage {
        val geom = ImageOps.transform(ImageOps.decode(context, uri), edit.totalRotation, edit.mirror)
        return photo(geom, edit, printerWidth)
    }

    fun textBitmap(text: String, width: Int, style: TextStyle): Bitmap {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = style.sizePx.toFloat()
            val tf = Typeface.create(style.font.typeface, if (style.bold) Typeface.BOLD else Typeface.NORMAL)
            typeface = tf
            // Like TextView: smear when the face has no real bold (StaticLayout won't do it for us).
            isFakeBoldText = style.bold && !tf.isBold
        }
        val content = text.ifEmpty { " " }
        val layout = StaticLayout.Builder.obtain(content, 0, content.length, paint, width)
            .setAlignment(style.align)
            .setIncludePad(true)
            .build()
        val bmp = Bitmap.createBitmap(width, maxOf(1, layout.height), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        layout.draw(c)
        return bmp
    }

    fun text(text: String, style: TextStyle, printerWidth: Int): MonoImage {
        val bmp = textBitmap(text, printerWidth - 2 * style.border.pad, style)
        return finish(bmp, TEXT_TONE, style.border)
    }

    fun testPage(printerWidth: Int): MonoImage {
        val title = text(
            "Mini Printer OK\nÁÉÍÓÚ ãõç 0123456789",
            TextStyle(sizePx = 30, bold = true, align = Layout.Alignment.ALIGN_CENTER),
            printerWidth,
        )
        val gradient = Bitmap.createBitmap(printerWidth, 60, Bitmap.Config.ARGB_8888).also {
            val p = Paint().apply {
                shader = LinearGradient(0f, 0f, printerWidth.toFloat(), 0f, Color.BLACK, Color.WHITE, Shader.TileMode.CLAMP)
            }
            Canvas(it).drawRect(0f, 0f, printerWidth.toFloat(), 60f, p)
        }
        val grad = finish(gradient, ToneSettings(), BorderSettings(enabled = false))
        val out = MonoImage(printerWidth, title.height + grad.height + 8)
        MonoImage.blit(title, out, 0, 0)
        MonoImage.blit(grad, out, 0, title.height + 8)
        return out.withBorder(2, 0).fitWidth(printerWidth)
    }
}
