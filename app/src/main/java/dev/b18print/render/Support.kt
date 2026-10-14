package dev.b18print.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import dev.b18print.L10n
import dev.b18print.R
import dev.b18print.data.Store
import dev.b18print.model.DataMode
import dev.b18print.model.DataSource
import java.io.File

// ------------------------------------------------------------------ fonts

data class FontInfo(val key: String, val label: String)

object Fonts {
    val system = listOf(
        FontInfo("sys:sans-serif", "Sans Serif"),
        FontInfo("sys:sans-serif-medium", "Sans Medium"),
        FontInfo("sys:sans-serif-black", "Sans Black"),
        FontInfo("sys:sans-serif-light", "Sans Light"),
        FontInfo("sys:sans-serif-thin", "Sans Thin"),
        FontInfo("sys:sans-serif-condensed", "Sans Condensed"),
        FontInfo("sys:sans-serif-condensed-light", "Sans Condensed Light"),
        FontInfo("sys:sans-serif-smallcaps", "Sans Small Caps"),
        FontInfo("sys:serif", "Serif"),
        FontInfo("sys:monospace", "Monospace"),
        FontInfo("sys:serif-monospace", "Serif Monospace"),
        FontInfo("sys:casual", "Casual"),
        FontInfo("sys:cursive", "Cursive"),
    )

    fun imported(): List<FontInfo> = Store.fontsDir.listFiles().orEmpty()
        .filter { it.extension.lowercase() in setOf("ttf", "otf") }
        .sortedBy { it.name.lowercase() }
        .map { FontInfo("file:${it.name}", it.nameWithoutExtension) }

    fun all() = system + imported()

    fun label(key: String) = all().firstOrNull { it.key == key }?.label ?: key.substringAfter(':')

    private val cache = HashMap<String, Typeface>()

    fun base(key: String): Typeface = synchronized(cache) {
        cache.getOrPut(key) {
            try {
                when {
                    key.startsWith("file:") -> Typeface.createFromFile(File(Store.fontsDir, key.removePrefix("file:")))
                    else -> Typeface.create(key.removePrefix("sys:"), Typeface.NORMAL)
                }
            } catch (e: Exception) {
                Typeface.SANS_SERIF
            }
        }
    }

    fun typeface(key: String, bold: Boolean, italic: Boolean): Typeface {
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base(key), style)
    }
}

// ------------------------------------------------------------------ variable data

object DataVars {
    private val placeholder = Regex("\\{([A-Z])\\}")

    fun rows(ds: DataSource): List<List<String>> {
        val lines = ds.listText.lines().map { it.trimEnd('\r') }.filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val sep = when {
            lines.any { '\t' in it } -> "\t"
            lines.any { ';' in it } -> ";"
            lines.any { ',' in it } -> ","
            else -> null
        }
        return lines.map { l -> if (sep == null) listOf(l.trim()) else l.split(sep).map { it.trim().trim('"') } }
    }

    fun count(ds: DataSource): Int = when (ds.mode) {
        DataMode.NONE -> 1
        DataMode.LIST -> rows(ds).size.coerceAtLeast(1)
        DataMode.COUNTER -> ds.counterCount.coerceIn(1, 9999)
    }

    fun columns(ds: DataSource): Int = rows(ds).maxOfOrNull { it.size } ?: 0

    fun vars(ds: DataSource, index: Int): Map<String, String> {
        if (ds.mode == DataMode.NONE) return emptyMap()
        val m = HashMap<String, String>()
        val n = ds.counterStart + index * ds.counterStep
        m["N"] = if (ds.counterDigits > 0) n.toString().padStart(ds.counterDigits, '0') else n.toString()
        if (ds.mode == DataMode.LIST) {
            rows(ds).getOrNull(index)?.forEachIndexed { i, v -> if (i < 26) m[('A' + i).toString()] = v }
        }
        return m
    }

    fun apply(text: String, vars: Map<String, String>): String =
        if (vars.isEmpty()) text else placeholder.replace(text) { mr -> vars[mr.groupValues[1]] ?: "" }
}

// ------------------------------------------------------------------ barcodes

object Barcodes {
    val linear = listOf(
        "CODE_128" to "Code 128", "EAN_13" to "EAN-13", "EAN_8" to "EAN-8", "UPC_A" to "UPC-A",
        "UPC_E" to "UPC-E", "CODE_39" to "Code 39", "CODE_93" to "Code 93", "ITF" to "ITF", "CODABAR" to "Codabar",
    )
    val matrix = listOf(
        "QR_CODE" to "QR", "DATA_MATRIX" to "Data Matrix", "AZTEC" to "Aztec", "PDF_417" to "PDF417",
    )

    sealed class Result {
        class Ok(val m: BitMatrix) : Result()
        class Err(val msg: String) : Result()
    }

    private val cache = LruCache<String, Result>(64)

    fun encode(content: String, format: String, ecc: String = "M"): Result {
        val key = "$format|$ecc|${L10n.locale()}|$content"
        cache.get(key)?.let { return it }
        val r = try {
            if (content.isEmpty()) throw IllegalArgumentException(L10n.str(R.string.code_no_content))
            val fmt = BarcodeFormat.valueOf(format)
            val hints = HashMap<EncodeHintType, Any>()
            hints[EncodeHintType.MARGIN] = 0
            hints[EncodeHintType.CHARACTER_SET] = "UTF-8"
            if (fmt == BarcodeFormat.QR_CODE) hints[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.valueOf(ecc)
            Result.Ok(MultiFormatWriter().encode(content, fmt, 0, 0, hints))
        } catch (e: Exception) {
            Result.Err(e.message ?: L10n.str(R.string.code_invalid))
        }
        cache.put(key, r)
        return r
    }
}

// ------------------------------------------------------------------ images

object Images {
    private val sources = LruCache<String, Bitmap>(8)
    private val processed = LruCache<String, Bitmap>(16)

    fun source(file: String): Bitmap? {
        sources.get(file)?.let { return it }
        val f = File(Store.imagesDir, file)
        val b = if (f.exists()) BitmapFactory.decodeFile(f.path) else null
        if (b != null) sources.put(file, b)
        return b
    }

    /** 1-bit rendering at print resolution, black pixels opaque, white transparent. */
    fun processed(file: String, wDots: Int, hDots: Int, dither: Boolean, threshold: Int, invert: Boolean): Bitmap? {
        if (wDots <= 0 || hDots <= 0 || wDots > 4000 || hDots > 4000) return null
        val key = "$file|$wDots|$hDots|$dither|$threshold|$invert"
        processed.get(key)?.let { return it }
        val src = source(file) ?: return null
        val scaled = Bitmap.createScaledBitmap(src, wDots, hDots, true)
        val px = IntArray(wDots * hDots)
        scaled.getPixels(px, 0, wDots, 0, 0, wDots, hDots)
        val lum = FloatArray(px.size) { i ->
            val c = px[i]
            (Color.red(c) * 0.299f + Color.green(c) * 0.587f + Color.blue(c) * 0.114f)
        }
        val out = IntArray(px.size)
        for (y in 0 until hDots) {
            for (x in 0 until wDots) {
                val i = y * wDots + x
                val old = lum[i]
                val black = old < threshold
                out[i] = if (black != invert) Color.BLACK else Color.TRANSPARENT
                if (dither) {
                    // Floyd–Steinberg error diffusion
                    val err = old - (if (black) 0f else 255f)
                    if (x + 1 < wDots) lum[i + 1] += err * 7 / 16
                    if (y + 1 < hDots) {
                        if (x > 0) lum[i + wDots - 1] += err * 3 / 16
                        lum[i + wDots] += err * 5 / 16
                        if (x + 1 < wDots) lum[i + wDots + 1] += err * 1 / 16
                    }
                }
            }
        }
        val b = Bitmap.createBitmap(out, wDots, hDots, Bitmap.Config.ARGB_8888)
        processed.put(key, b)
        return b
    }
}
