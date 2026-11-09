package io.github.tobsef.niimstudio.render

import android.graphics.Canvas
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import io.github.tobsef.niimstudio.L10n
import io.github.tobsef.niimstudio.data.Store

/**
 * A connector pictogram loaded from `assets/connectors/<key>.svg`.
 * [ar] is width / height taken from the SVG's viewBox.
 */
class Connector(val key: String, val ar: Float, private val svg: SVG) {
    /** Name from the string resource `conn_<key>`, else the SVG `<title>`, else the key. */
    val name: String
        get() {
            val ctx = L10n.context()
            @Suppress("DiscouragedApi")
            val id = ctx.resources.getIdentifier("conn_$key", "string", ctx.packageName)
            return if (id != 0) ctx.getString(id) else svg.documentTitle?.takeIf { it.isNotBlank() } ?: key
        }

    fun render(c: Canvas, w: Float, h: Float) = svg.renderToCanvas(c, RenderOptions.create().viewPort(0f, 0f, w, h))
}

/**
 * Connector pictograms from the cable-flag designer, stored as editable SVG files in
 * `app/src/main/assets/connectors/`. Convention: height 100 units, stroke width 9, black.
 * A new SVG in that folder shows up as a new connector (name via `conn_<key>` or `<title>`).
 */
object Connectors {
    /** Stroke width relative to the height (9 of 100 units). */
    const val LW = 0.09f

    private const val DIR = "connectors"

    /** Display order of the built-in connectors; extra files follow alphabetically. */
    private val order = listOf(
        "usba", "usbaf", "usbb", "usbmini", "usbmicro", "usbc", "usbcf", "usbmicrob",
        "hdmi", "dp", "rj45", "jack", "thinjack", "dc", "magnet", "lightning", "iec",
    )

    val all: List<Connector> by lazy {
        val assets = Store.appContext.assets
        val keys = assets.list(DIR).orEmpty().filter { it.endsWith(".svg") }.map { it.removeSuffix(".svg") }
        keys.sortedWith(compareBy<String> { order.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it })
            .mapNotNull { key ->
                try {
                    val svg = assets.open("$DIR/$key.svg").use { SVG.getFromInputStream(it) }
                    val vb = svg.documentViewBox
                    val ar = if (vb != null && vb.height() > 0) vb.width() / vb.height()
                    else svg.documentWidth / svg.documentHeight.coerceAtLeast(1f)
                    if (vb == null) svg.setDocumentViewBox(0f, 0f, svg.documentWidth, svg.documentHeight)
                    // Fill the render viewport; width/height in the file (px, mm …) would fix the size.
                    svg.setDocumentWidth("100%")
                    svg.setDocumentHeight("100%")
                    Connector(key, ar, svg)
                } catch (e: Exception) {
                    android.util.Log.w("Connectors", "Cannot load $key.svg: ${e.message}")
                    null
                }
            }
    }

    private val byKey by lazy { all.associateBy { it.key } }

    fun find(key: String): Connector? = byKey[key]

    fun name(key: String): String = find(key)?.name ?: key

    /** Draws the pictogram with its top-left corner at the origin and height [h] (width = ar × h). */
    @Suppress("UNUSED_PARAMETER")
    fun draw(c: Canvas, con: Connector, h: Float, color: Int) {
        synchronized(con) { con.render(c, con.ar * h, h) }
    }
}
