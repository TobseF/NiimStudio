package dev.b18print

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import dev.b18print.protocol.MonoImage

/** 203 dpi = 8 dots per mm. */
const val DOTS_PER_MM = 8

object LabelRenderer {

    /**
     * Draws a label as the user sees it: [lengthMm] horizontal (along the tape),
     * [widthMm] vertical (across the tape).
     */
    fun renderText(text: String, widthMm: Float, lengthMm: Float, border: Boolean): Bitmap {
        val w = (lengthMm * DOTS_PER_MM).toInt().coerceAtLeast(8)
        val h = (widthMm * DOTS_PER_MM).toInt().coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val margin = 4f
        if (border) {
            val p = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
            c.drawRect(1f, 1f, w - 1f, h - 1f, p)
        }

        val lines = text.ifBlank { " " }.split("\n")
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        // Largest text size that fits all lines into the label.
        val availW = w - 2 * margin
        val availH = h - 2 * margin
        var size = availH / lines.size
        while (size > 6f) {
            paint.textSize = size
            val fm = paint.fontMetrics
            val lineH = fm.descent - fm.ascent
            val maxW = lines.maxOf { paint.measureText(it) }
            if (maxW <= availW && lineH * lines.size <= availH) break
            size -= 1f
        }
        paint.textSize = size
        val fm = paint.fontMetrics
        val lineH = fm.descent - fm.ascent
        var y = (h - lineH * lines.size) / 2f - fm.ascent
        for (line in lines) {
            val x = (w - paint.measureText(line)) / 2f
            c.drawText(line, x, y, paint)
            y += lineH
        }
        return bmp
    }

    /** Border, a corner marker and an arrow make orientation and offsets easy to see on paper. */
    fun renderTestPattern(widthMm: Float, lengthMm: Float): Bitmap {
        val w = (lengthMm * DOTS_PER_MM).toInt()
        val h = (widthMm * DOTS_PER_MM).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK; style = Paint.Style.STROKE; strokeWidth = 2f }
        c.drawRect(1f, 1f, w - 1f, h - 1f, p)
        p.style = Paint.Style.FILL
        c.drawRect(0f, 0f, h / 3f, h / 3f, p) // top-left marker
        // mm ticks along the bottom edge
        for (mm in 0..lengthMm.toInt()) {
            val x = mm * DOTS_PER_MM.toFloat()
            c.drawRect(x, h - (if (mm % 5 == 0) 12f else 6f), x + 1f, h.toFloat(), p)
        }
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = h * 0.45f
            typeface = Typeface.DEFAULT_BOLD
        }
        c.drawText("B18 TEST →", h / 3f + 6f, h * 0.62f, t)
        return bmp
    }

    /** Rotates the label clockwise by [rotation] degrees into print-head orientation. */
    fun rotate(src: Bitmap, rotation: Int): Bitmap {
        if (rotation % 360 == 0) return src
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    /**
     * Converts to 1 bit. Width beyond [printheadPixels] is cropped, narrower images are centred
     * on the print head when [center] is set.
     */
    fun toMono(src: Bitmap, printheadPixels: Int, center: Boolean, threshold: Int = 128): MonoImage {
        val cols = if (center) printheadPixels else minOf(src.width, printheadPixels)
        val offset = if (center) (printheadPixels - src.width) / 2 else 0
        val rows = src.height
        val px = IntArray(src.width * rows)
        src.getPixels(px, 0, src.width, 0, 0, src.width, rows)
        val black = BooleanArray(cols * rows)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val sx = x - offset
                if (sx < 0 || sx >= src.width) continue
                val c = px[y * src.width + sx]
                val a = Color.alpha(c)
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                black[y * cols + x] = a > 127 && lum < threshold
            }
        }
        return MonoImage(cols, rows, black)
    }

    /** Preview of exactly what is sent, scaled up for display. */
    fun monoPreview(img: MonoImage, scale: Int = 3): Bitmap {
        val bmp = Bitmap.createBitmap(img.cols, img.rows, Bitmap.Config.ARGB_8888)
        val px = IntArray(img.cols * img.rows) { if (img.black[it]) Color.BLACK else Color.WHITE }
        bmp.setPixels(px, 0, img.cols, 0, 0, img.cols, img.rows)
        return Bitmap.createScaledBitmap(bmp, img.cols * scale, img.rows * scale, false)
    }
}
