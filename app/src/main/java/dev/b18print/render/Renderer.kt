package dev.b18print.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.common.BitMatrix
import dev.b18print.model.AppSettings
import dev.b18print.model.BarcodeEl
import dev.b18print.model.ConnLabel
import dev.b18print.model.ConnectorEl
import dev.b18print.model.SideMode
import dev.b18print.model.DateEl
import dev.b18print.model.Element
import dev.b18print.model.FitMode
import dev.b18print.model.ImageEl
import dev.b18print.model.LabelDoc
import dev.b18print.model.QrEl
import dev.b18print.model.ShapeEl
import dev.b18print.model.ShapeKind
import dev.b18print.model.SymbolEl
import dev.b18print.model.TextEl
import dev.b18print.model.TextStyle
import dev.b18print.protocol.MonoImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlin.math.floor

/** 203 dpi ≈ 8 dots per mm. */
const val DOTS_PER_MM = 8

/** 1 pt = 1/72 inch. */
const val MM_PER_PT = 25.4f / 72f

class RenderCtx(
    val vars: Map<String, String> = emptyMap(),
    val now: Date = Date(),
    /** Print rendering hides invalid-content placeholders. */
    val forPrint: Boolean = false,
)

/**
 * Draws label elements onto a canvas whose unit is 1 mm. The same code renders the
 * editor view and the print bitmap, so the editor preview matches the print.
 */
object Renderer {
    /** Text is laid out in 1/100 mm because StaticLayout works with integer line positions. */
    private const val TU = 100f

    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }
    private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val bitmapPaint = Paint().apply { isFilterBitmap = false }

    fun drawElements(c: Canvas, doc: LabelDoc, rc: RenderCtx) {
        for (e in doc.elements) drawElement(c, e, rc)
    }

    fun drawElement(c: Canvas, e: Element, rc: RenderCtx) {
        val f = e.frame
        c.save()
        c.translate(f.cx, f.cy)
        if (f.rotation != 0f) c.rotate(f.rotation)
        if (f.mirror) c.scale(-1f, 1f)
        c.translate(-f.w / 2, -f.h / 2)
        when (e) {
            is TextEl -> drawText(c, DataVars.apply(e.text, rc.vars), e.style, f.w, f.h)
            is DateEl -> drawText(c, dateText(e, rc.now), e.style, f.w, f.h)
            is ImageEl -> drawImage(c, e)
            is SymbolEl -> drawSymbol(c, e)
            is BarcodeEl -> drawBarcode(c, e, DataVars.apply(e.content, rc.vars), rc)
            is QrEl -> drawMatrix(c, e, DataVars.apply(e.content, rc.vars), rc)
            is ShapeEl -> drawShape(c, e)
            is ConnectorEl -> drawConnector(c, e, rc.vars)
        }
        c.restore()
    }

    // ------------------------------------------------------------ connectors

    /** Layout of a connector cell (all mm), mirroring the cable-flag designer. */
    private class ConnLayout(
        val con: Connector, val iconH: Float, val iconW: Float, val text: String,
        val textSize: Float, val gap: Float, val width: Float, val below: Boolean,
    )

    fun connectorText(e: ConnectorEl, vars: Map<String, String> = emptyMap()): String {
        if (e.label == ConnLabel.NONE) return ""
        val t = DataVars.apply(e.text.ifBlank { Connectors.name(e.key) }, vars).trim()
        return if (e.upper) t.uppercase(dev.b18print.L10n.locale()) else t
    }

    private fun textWidth(st: TextStyle, text: String, sizeMm: Float): Float =
        if (text.isEmpty()) 0f else paintFor(st.copy(underline = false, strike = false, inverse = false), sizeMm).measureText(text) / TU

    private fun connLayout(e: ConnectorEl, h: Float, text: String): ConnLayout? {
        val con = Connectors.find(e.key) ?: return null
        val scale = e.iconScale.coerceIn(0.2f, 1f)
        if (text.isEmpty()) {
            val ih = h * scale
            return ConnLayout(con, ih, con.ar * ih, "", 0f, 0f, con.ar * ih, false)
        }
        return if (e.label == ConnLabel.BELOW) {
            var ts = maxOf(0.8f, h * 0.24f)
            val pad = h * 0.07f
            var ih = minOf(h * scale, h - ts - pad)
            if (ih < h * 0.3f) {
                ih = h * 0.55f
                ts = maxOf(0.8f, h - ih - pad)
            }
            val iw = con.ar * ih
            ConnLayout(con, ih, iw, text, ts, pad, maxOf(iw, textWidth(e.style, text, ts)), true)
        } else {
            val ts = maxOf(0.8f, h * 0.34f)
            val ih = h * scale
            val iw = con.ar * ih
            val g = h * 0.14f
            ConnLayout(con, ih, iw, text, ts, g, iw + g + textWidth(e.style, text, ts), false)
        }
    }

    /** Width the connector needs at height [h] without shrinking. */
    fun connectorNaturalWidth(e: ConnectorEl, h: Float): Float =
        connLayout(e, h, connectorText(e))?.width ?: h

    private fun drawConnector(c: Canvas, e: ConnectorEl, vars: Map<String, String> = emptyMap()) {
        val f = e.frame
        val l = connLayout(e, f.h, connectorText(e, vars)) ?: run {
            drawMissing(c, f.w, f.h)
            return
        }
        // Shrink uniformly when the box is narrower than the natural width.
        val k = minOf(1f, f.w / l.width)
        c.save()
        c.translate((f.w - l.width * k) / 2, f.h * (1 - k) / 2)
        c.scale(k, k)
        val h = f.h
        val paint = paintFor(e.style.copy(underline = false, strike = false, inverse = false), l.textSize)
        if (l.text.isEmpty()) {
            c.save(); c.translate((l.width - l.iconW) / 2, (h - l.iconH) / 2); Connectors.draw(c, l.con, l.iconH, Color.BLACK); c.restore()
        } else if (l.below) {
            val block = l.iconH + l.gap + l.textSize
            val top = (h - block) / 2
            c.save(); c.translate((l.width - l.iconW) / 2, top); Connectors.draw(c, l.con, l.iconH, Color.BLACK); c.restore()
            drawLine(c, l.text, paint, l.width / 2, top + l.iconH + l.gap + l.textSize / 2, center = true)
        } else {
            c.save(); c.translate(0f, (h - l.iconH) / 2); Connectors.draw(c, l.con, l.iconH, Color.BLACK); c.restore()
            drawLine(c, l.text, paint, l.iconW + l.gap, h / 2, center = false)
        }
        c.restore()
    }

    /** One line of text whose ink is vertically centred on [cy]. */
    private fun drawLine(c: Canvas, text: String, paint: TextPaint, x: Float, cy: Float, center: Boolean) {
        val b = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, b)
        val w = paint.measureText(text)
        c.save()
        c.scale(1 / TU, 1 / TU)
        val px = x * TU - if (center) w / 2 else 0f
        val py = cy * TU - (b.top + b.bottom) / 2f
        c.drawText(text, px, py, paint)
        c.restore()
    }

    // ------------------------------------------------------------ text

    fun dateText(e: DateEl, now: Date): String {
        val cal = Calendar.getInstance().apply {
            time = now
            add(Calendar.DAY_OF_YEAR, e.offsetDays)
        }
        val s = try {
            SimpleDateFormat(e.pattern, dev.b18print.L10n.locale()).format(cal.time)
        } catch (ex: Exception) {
            e.pattern
        }
        return e.prefix + s
    }

    private fun paintFor(st: TextStyle, sizeMm: Float): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.typeface(st.font, st.bold, st.italic)
        textSize = sizeMm * TU
        isUnderlineText = st.underline
        isStrikeThruText = st.strike
        letterSpacing = st.letterSpacing
        color = if (st.inverse) Color.WHITE else Color.BLACK
    }

    private fun layout(text: String, st: TextStyle, sizeMm: Float, widthMm: Float): StaticLayout {
        val t = if (st.wrapWord) text else text.toList().joinToString("​")
        val w = (widthMm * TU).toInt().coerceAtLeast(1)
        val align = when (st.alignH) {
            0 -> Layout.Alignment.ALIGN_NORMAL
            2 -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_CENTER
        }
        return StaticLayout.Builder.obtain(t, 0, t.length, paintFor(st, sizeMm), w)
            .setAlignment(align)
            .setLineSpacing(st.lineSpacing * TU, 1f)
            .setIncludePad(false)
            .build()
    }

    private fun innerPad(st: TextStyle) = if (st.inverse) 0.6f else 0f

    /** Height the text needs at its font size within [widthMm]. */
    fun textHeightMm(text: String, st: TextStyle, widthMm: Float): Float {
        val p = innerPad(st)
        return layout(text.ifEmpty { " " }, st, st.sizePt * MM_PER_PT, widthMm - 2 * p).height / TU + 2 * p
    }

    private fun fitSize(text: String, st: TextStyle, w: Float, h: Float): Float {
        var lo = 0.5f
        var hi = 200f
        repeat(18) {
            val mid = (lo + hi) / 2
            val l = layout(text, st, mid, w)
            var maxW = 0f
            for (i in 0 until l.lineCount) maxW = maxOf(maxW, l.getLineWidth(i))
            if (l.height / TU <= h && maxW / TU <= w + 0.01f) lo = mid else hi = mid
        }
        return lo
    }

    private fun drawText(c: Canvas, text0: String, st: TextStyle, w: Float, h: Float) {
        val text = text0.ifEmpty { " " }
        if (st.inverse) c.drawRect(0f, 0f, w, h, black)
        val p = innerPad(st)
        val iw = w - 2 * p
        val ih = h - 2 * p
        val size = if (st.fit == FitMode.FIT) fitSize(text, st, iw, ih) else st.sizePt * MM_PER_PT
        val l = layout(text, st, size, iw)
        val th = l.height / TU
        val top = p + when (st.alignV) {
            0 -> 0f
            2 -> ih - th
            else -> (ih - th) / 2
        }
        c.save()
        if (st.fit == FitMode.FIXED) c.clipRect(0f, 0f, w, h)
        c.translate(p, top)
        c.scale(1 / TU, 1 / TU)
        l.draw(c)
        c.restore()
    }

    // ------------------------------------------------------------ images & symbols

    private fun drawImage(c: Canvas, e: ImageEl) {
        val f = e.frame
        var dw = f.w
        var dh = f.h
        val src = Images.source(e.file)
        if (src == null) {
            drawMissing(c, f.w, f.h)
            return
        }
        if (e.keepRatio) {
            val s = minOf(f.w / src.width, f.h / src.height)
            dw = src.width * s
            dh = src.height * s
        }
        val wd = (dw * DOTS_PER_MM).toInt().coerceAtLeast(1)
        val hd = (dh * DOTS_PER_MM).toInt().coerceAtLeast(1)
        val bmp = Images.processed(e.file, wd, hd, e.dither, e.threshold, e.invert) ?: return
        val left = (f.w - dw) / 2
        val top = (f.h - dh) / 2
        c.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), bitmapPaint)
    }

    private fun drawSymbol(c: Canvas, e: SymbolEl) {
        if (e.invert) {
            c.drawRect(0f, 0f, e.frame.w, e.frame.h, black)
            Symbols.draw(c, e.key, e.frame.w, e.frame.h, white)
        } else {
            Symbols.draw(c, e.key, e.frame.w, e.frame.h, black)
        }
    }

    private fun drawMissing(c: Canvas, w: Float, h: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 60, 60)
            style = Paint.Style.STROKE
            strokeWidth = 0.3f
        }
        c.drawRect(0f, 0f, w, h, p)
        c.drawLine(0f, 0f, w, h, p)
        c.drawLine(w, 0f, 0f, h, p)
    }

    // ------------------------------------------------------------ codes

    private fun drawBarcode(c: Canvas, e: BarcodeEl, content: String, rc: RenderCtx) {
        val f = e.frame
        val r = Barcodes.encode(content, e.format)
        if (r !is Barcodes.Result.Ok) {
            if (!rc.forPrint) drawError(c, f.w, f.h, (r as Barcodes.Result.Err).msg)
            return
        }
        val m: BitMatrix = r.m
        val textH = if (e.showText) e.textSizePt * MM_PER_PT * 1.25f else 0f
        val barH = (f.h - textH).coerceAtLeast(0.5f)
        // Whole print dots per module so bars stay sharp and scannable.
        val wDots = f.w * DOTS_PER_MM
        val module = floor(wDots / m.width).coerceAtLeast(1f) / DOTS_PER_MM
        val total = module * m.width
        val x0 = ((f.w - total) / 2 * DOTS_PER_MM).toInt() / DOTS_PER_MM.toFloat()
        val p = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        var x = 0
        while (x < m.width) {
            if (m.get(x, 0)) {
                var run = 1
                while (x + run < m.width && m.get(x + run, 0)) run++
                c.drawRect(x0 + x * module, 0f, x0 + (x + run) * module, barH, p)
                x += run
            } else {
                x++
            }
        }
        if (e.showText) {
            val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Fonts.typeface("sys:sans-serif", false, false)
                textSize = e.textSizePt * MM_PER_PT * TU
                textAlign = Paint.Align.CENTER
            }
            c.save()
            c.scale(1 / TU, 1 / TU)
            c.drawText(content, f.w / 2 * TU, (barH + textH * 0.85f) * TU, tp)
            c.restore()
        }
    }

    private fun drawMatrix(c: Canvas, e: QrEl, content: String, rc: RenderCtx) {
        val f = e.frame
        val r = Barcodes.encode(content, e.format, e.ecc)
        if (r !is Barcodes.Result.Ok) {
            if (!rc.forPrint) drawError(c, f.w, f.h, (r as Barcodes.Result.Err).msg)
            return
        }
        val m = r.m
        val moduleDots = floor(minOf(f.w * DOTS_PER_MM / m.width, f.h * DOTS_PER_MM / m.height)).coerceAtLeast(1f)
        val module = moduleDots / DOTS_PER_MM
        val x0 = ((f.w - module * m.width) / 2 * DOTS_PER_MM).toInt() / DOTS_PER_MM.toFloat()
        val y0 = ((f.h - module * m.height) / 2 * DOTS_PER_MM).toInt() / DOTS_PER_MM.toFloat()
        val p = Paint().apply { color = Color.BLACK; isAntiAlias = false }
        for (y in 0 until m.height) {
            var x = 0
            while (x < m.width) {
                if (m.get(x, y)) {
                    var run = 1
                    while (x + run < m.width && m.get(x + run, y)) run++
                    c.drawRect(x0 + x * module, y0 + y * module, x0 + (x + run) * module, y0 + (y + 1) * module, p)
                    x += run
                } else {
                    x++
                }
            }
        }
    }

    private fun drawError(c: Canvas, w: Float, h: Float, msg: String) {
        drawMissing(c, w, h)
        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(220, 60, 60)
            textSize = minOf(h * 0.25f, 2.5f) * TU
            textAlign = Paint.Align.CENTER
        }
        c.save()
        c.scale(1 / TU, 1 / TU)
        c.drawText(msg.take(40), w / 2 * TU, h / 2 * TU, tp)
        c.restore()
    }

    // ------------------------------------------------------------ shapes

    private fun drawShape(c: Canvas, e: ShapeEl) {
        val f = e.frame
        val sw = e.strokeMm.coerceAtLeast(0.125f)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = if (e.filled) Paint.Style.FILL else Paint.Style.STROKE
            strokeWidth = sw
        }
        val r = RectF(sw / 2, sw / 2, f.w - sw / 2, f.h - sw / 2)
        if (e.filled) r.set(0f, 0f, f.w, f.h)
        when (e.shape) {
            ShapeKind.RECT -> c.drawRect(r, p)
            ShapeKind.ROUND_RECT -> c.drawRoundRect(r, e.cornerMm, e.cornerMm, p)
            ShapeKind.ELLIPSE -> c.drawOval(r, p)
            ShapeKind.LINE_H -> c.drawRect(0f, (f.h - sw) / 2, f.w, (f.h + sw) / 2, p.apply { style = Paint.Style.FILL })
            ShapeKind.LINE_V -> c.drawRect((f.w - sw) / 2, 0f, (f.w + sw) / 2, f.h, p.apply { style = Paint.Style.FILL })
        }
    }

    // ------------------------------------------------------------ print pipeline

    /** Renders the label at print resolution in editor orientation (white background). */
    /**
     * Renders the whole printed label in editor orientation (white background).
     * Flag labels get the panel twice with the wrap zone in between, the second copy
     * turned 180° so the folded flag reads from both sides; wrap labels repeat it unturned.
     */
    fun renderLabel(doc: LabelDoc, rc: RenderCtx, scale: Float = DOTS_PER_MM.toFloat(), applyOffsets: Boolean = true): Bitmap {
        val total = doc.totalLengthMm
        val fullW = if (doc.portrait) doc.widthMm else total
        val fullH = if (doc.portrait) total else doc.heightMm
        val bmp = Bitmap.createBitmap((fullW * scale).toInt().coerceAtLeast(1), (fullH * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        c.scale(scale, scale)
        if (applyOffsets) {
            val ox = doc.profile.offsetXmm
            val oy = doc.profile.offsetYmm
            // Offsets are defined along the tape (x) and across it (y) in landscape design.
            if (doc.portrait) c.translate(oy, ox) else c.translate(ox, oy)
        }
        val pw = doc.widthMm
        val ph = doc.heightMm
        fun panel() {
            c.save()
            c.clipRect(0f, 0f, pw, ph)
            drawElements(c, doc, rc)
            c.restore()
        }
        panel()
        if (doc.sides != SideMode.SINGLE) {
            val second = total - doc.panelLengthMm
            c.save()
            if (doc.portrait) c.translate(0f, second) else c.translate(second, 0f)
            if (doc.sides == SideMode.FLAG) c.rotate(180f, pw / 2, ph / 2)
            panel()
            c.restore()
            if (doc.foldMarks) {
                val p = Paint().apply {
                    color = Color.BLACK
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(0.125f, doc.profile.tapeWidthMm * 0.02f)
                    pathEffect = android.graphics.DashPathEffect(floatArrayOf(doc.profile.tapeWidthMm * 0.09f, doc.profile.tapeWidthMm * 0.09f), 0f)
                }
                for (at in listOf(doc.panelLengthMm, second)) {
                    if (doc.portrait) c.drawLine(0f, at, pw, at, p) else c.drawLine(at, 0f, at, ph, p)
                }
            }
        }
        return bmp
    }

    /** Clockwise rotation from editor orientation into print-head orientation. */
    fun printRotation(doc: LabelDoc, s: AppSettings): Int =
        ((if (doc.portrait) s.rotation - 90 else s.rotation) % 360 + 360) % 360

    fun printImage(doc: LabelDoc, s: AppSettings, rowIndex: Int): MonoImage {
        val rc = RenderCtx(vars = DataVars.vars(doc.data, rowIndex), forPrint = true)
        val label = renderLabel(doc, rc)
        return toMono(rotate(label, printRotation(doc, s)), s.printheadDots, s.centerOnHead)
    }

    fun rotate(src: Bitmap, rotation: Int): Bitmap {
        if (rotation % 360 == 0) return src
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
    }

    /**
     * Converts to 1 bit. Width beyond [printheadPixels] is cropped; with [center] the
     * image is centred on the head (a 14 mm tape loses 1 mm per side on the 12 mm head).
     */
    fun toMono(src: Bitmap, printheadPixels: Int, center: Boolean, threshold: Int = 128): MonoImage {
        val cols = if (center) printheadPixels else minOf(src.width, printheadPixels)
        val offset = if (center) (printheadPixels - src.width) / 2 else 0
        val rows = src.height
        val px = IntArray(src.width * rows)
        src.getPixels(px, 0, src.width, 0, 0, src.width, rows)
        val blackPx = BooleanArray(cols * rows)
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val sx = x - offset
                if (sx < 0 || sx >= src.width) continue
                val c = px[y * src.width + sx]
                val lum = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000
                blackPx[y * cols + x] = Color.alpha(c) > 127 && lum < threshold
            }
        }
        return MonoImage(cols, rows, blackPx)
    }

    /** Mono image rotated back into editor orientation for previews. */
    fun monoPreview(img: MonoImage, rotationBack: Int): Bitmap {
        val bmp = Bitmap.createBitmap(img.cols, img.rows, Bitmap.Config.ARGB_8888)
        val px = IntArray(img.cols * img.rows) { if (img.black[it]) Color.BLACK else Color.WHITE }
        bmp.setPixels(px, 0, img.cols, 0, 0, img.cols, img.rows)
        return rotate(bmp, rotationBack)
    }
}
