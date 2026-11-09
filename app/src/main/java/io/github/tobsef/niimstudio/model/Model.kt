package io.github.tobsef.niimstudio.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/**
 * Element geometry in millimetres, label coordinates (x to the right, y down).
 * [rotation] rotates the box around its centre; [mirror] flips the content horizontally.
 */
@Serializable
data class Frame(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val rotation: Float = 0f,
    val mirror: Boolean = false,
) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2

    /** Size of the axis-aligned bounding box of the rotated frame. */
    fun boundsSize(): Pair<Float, Float> {
        val r = Math.toRadians(rotation.toDouble())
        val c = kotlin.math.abs(kotlin.math.cos(r)).toFloat()
        val s = kotlin.math.abs(kotlin.math.sin(r)).toFloat()
        return (w * c + h * s) to (w * s + h * c)
    }

    fun withCenter(ncx: Float, ncy: Float) = copy(x = ncx - w / 2, y = ncy - h / 2)
}

@Serializable
enum class FitMode { AUTO_HEIGHT, FIXED, FIT }

@Serializable
data class TextStyle(
    val font: String = "sys:sans-serif",
    val sizePt: Float = 11f,
    val bold: Boolean = true,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
    /** 0 = left, 1 = centre, 2 = right */
    val alignH: Int = 1,
    /** 0 = top, 1 = centre, 2 = bottom */
    val alignV: Int = 1,
    /** Extra letter spacing in em. */
    val letterSpacing: Float = 0f,
    /** Extra line spacing in mm. */
    val lineSpacing: Float = 0f,
    val wrapWord: Boolean = true,
    val fit: FitMode = FitMode.AUTO_HEIGHT,
    /** White text on a black box. */
    val inverse: Boolean = false,
)

@Serializable
enum class ShapeKind { RECT, ROUND_RECT, ELLIPSE, LINE_H, LINE_V }

@Serializable
sealed class Element {
    abstract val id: String
    abstract val frame: Frame
    abstract val locked: Boolean

    abstract fun withFrame(f: Frame): Element
    abstract fun withLocked(l: Boolean): Element
    abstract fun withId(id: String): Element

    /** Corner handle scales proportionally. */
    open val keepsAspect: Boolean get() = false
}

@Serializable
@SerialName("text")
data class TextEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val text: String,
    val style: TextStyle = TextStyle(),
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
    override val keepsAspect get() = true
}

@Serializable
@SerialName("date")
data class DateEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val pattern: String = "dd.MM.yyyy",
    val offsetDays: Int = 0,
    val prefix: String = "",
    val style: TextStyle = TextStyle(),
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
    override val keepsAspect get() = true
}

@Serializable
@SerialName("image")
data class ImageEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val file: String,
    val dither: Boolean = true,
    val threshold: Int = 128,
    val invert: Boolean = false,
    val keepRatio: Boolean = true,
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
    override val keepsAspect get() = keepRatio
}

@Serializable
@SerialName("symbol")
data class SymbolEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val key: String,
    val invert: Boolean = false,
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
    override val keepsAspect get() = true
}

@Serializable
@SerialName("barcode")
data class BarcodeEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val content: String,
    val format: String = "CODE_128",
    val showText: Boolean = true,
    val textSizePt: Float = 6f,
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
}

@Serializable
@SerialName("qr")
data class QrEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val content: String,
    val format: String = "QR_CODE",
    val ecc: String = "M",
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
    override val keepsAspect get() = true
}

@Serializable
@SerialName("shape")
data class ShapeEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val shape: ShapeKind = ShapeKind.RECT,
    val strokeMm: Float = 0.4f,
    val filled: Boolean = false,
    val cornerMm: Float = 1f,
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
}

/** Position of the connector name relative to the pictogram. */
@Serializable
enum class ConnLabel { BELOW, BESIDE, NONE }

/** Connector pictogram from the cable-flag designer (see render/Connectors.kt). */
@Serializable
@SerialName("connector")
data class ConnectorEl(
    override val id: String,
    override val frame: Frame,
    override val locked: Boolean = false,
    val key: String = "usbc",
    val label: ConnLabel = ConnLabel.BELOW,
    /** Empty = the connector's name in the app language. */
    val text: String = "",
    /** Pictogram height relative to the element height. */
    val iconScale: Float = 0.8f,
    val upper: Boolean = false,
    val style: TextStyle = TextStyle(font = "sys:sans-serif-condensed", bold = true),
) : Element() {
    override fun withFrame(f: Frame) = copy(frame = f)
    override fun withLocked(l: Boolean) = copy(locked = l)
    override fun withId(id: String) = copy(id = id)
    override val keepsAspect get() = true
}

/** An editable label paper definition. */
@Serializable
data class LabelProfile(
    val id: String = newId(),
    val name: String,
    /** Tape width across the print head. */
    val tapeWidthMm: Float,
    /** Label length along the feed direction. */
    val lengthMm: Float,
    val labelType: Int = 1,
    val gapMm: Float = 2f,
    val cornerMm: Float = 1.5f,
    /** Print calibration offsets (shift the whole image). */
    val offsetXmm: Float = 0f,
    val offsetYmm: Float = 0f,
    /** 0 = use the app default. */
    val density: Int = 0,
) {
    val sizeLabel get() = "${fmt(tapeWidthMm)}×${fmt(lengthMm)} mm"

    companion object {
        fun fmt(v: Float) = if (v % 1f == 0f) v.toInt().toString() else "%.1f".format(v)

        /** Built-in profiles; the words are passed in localized. */
        fun defaults(white: String = "White", cable: String = "Cable", continuous: String = "continuous") = listOf(
            LabelProfile(id = "p12x40", name = "12×40 $white", tapeWidthMm = 12f, lengthMm = 40f),
            LabelProfile(id = "p14x50", name = "14×50 $white", tapeWidthMm = 14f, lengthMm = 50f),
            LabelProfile(id = "p12x30", name = "12×30 $white", tapeWidthMm = 12f, lengthMm = 30f),
            LabelProfile(id = "p15x30", name = "15×30 $white", tapeWidthMm = 15f, lengthMm = 30f),
            LabelProfile(id = "p12x22", name = "12×22 $cable", tapeWidthMm = 12f, lengthMm = 22f),
            LabelProfile(id = "p12xc", name = "12 mm $continuous (60 mm)", tapeWidthMm = 12f, lengthMm = 60f, labelType = 3, gapMm = 0f, cornerMm = 0f),
        )
    }
}

@Serializable
enum class DataMode { NONE, LIST, COUNTER }

/**
 * Variable data. Text, barcode and QR contents may contain {A}..{Z} (CSV columns)
 * and {N} (running number). Each data row prints one label.
 */
@Serializable
data class DataSource(
    val mode: DataMode = DataMode.NONE,
    val listText: String = "",
    val counterStart: Int = 1,
    val counterStep: Int = 1,
    val counterCount: Int = 10,
    val counterDigits: Int = 0,
)

@Serializable
data class LabelDoc(
    val profile: LabelProfile,
    val elements: List<Element> = emptyList(),
    /** Design the label upright (tape width horizontal). */
    val portrait: Boolean = false,
    val data: DataSource = DataSource(),
    val templateId: String? = null,
    val templateName: String? = null,
    /** Single panel, cable flag (two panels, the second turned 180°) or wrap (repeated). */
    val sides: SideMode = SideMode.SINGLE,
    val cableDiameterMm: Float = 5f,
    /** Wrap zone = cable Ø × π when true, otherwise [wrapZoneMm]. */
    val autoWrapZone: Boolean = true,
    val wrapZoneMm: Float = 15.7f,
    val foldMarks: Boolean = false,
) {
    /** Unprinted zone between the two panels of a flag/wrap label. */
    val wrapZone: Float
        get() = when {
            sides == SideMode.SINGLE -> 0f
            autoWrapZone -> cableDiameterMm * Math.PI.toFloat()
            else -> wrapZoneMm
        }

    /** Length of the designed panel along the tape. */
    val panelLengthMm: Float
        get() = if (sides == SideMode.SINGLE) profile.lengthMm else ((profile.lengthMm - wrapZone) / 2).coerceAtLeast(MIN_PANEL_MM)

    /** Printed length: the profile length, or longer if two minimal panels do not fit. */
    val totalLengthMm: Float
        get() = if (sides == SideMode.SINGLE) profile.lengthMm else maxOf(profile.lengthMm, 2 * panelLengthMm + wrapZone)

    /** Size of the design canvas (one panel). */
    val widthMm get() = if (portrait) profile.tapeWidthMm else panelLengthMm
    val heightMm get() = if (portrait) panelLengthMm else profile.tapeWidthMm

    companion object {
        const val MIN_PANEL_MM = 3f
    }
}

@Serializable
enum class SideMode { SINGLE, FLAG, WRAP }

@Serializable
data class Template(
    val id: String = newId(),
    val name: String,
    val updated: Long = System.currentTimeMillis(),
    val doc: LabelDoc,
)

@Serializable
enum class TransportKind { SPP, BLE }

@Serializable
data class AppSettings(
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val transport: TransportKind = TransportKind.SPP,
    val autoConnect: Boolean = true,
    val keepAlive: Boolean = true,
    val density: Int = 2,
    /** null = automatic by protocol version */
    val variant: String? = null,
    val packetDelayMs: Int = 10,
    val indexedRows: Boolean = true,
    val printheadDots: Int = 96,
    val rotation: Int = 90,
    val centerOnHead: Boolean = true,
    val lastProfileId: String = "p12x40",
    val favoriteSymbols: List<String> = emptyList(),
    val favoriteFonts: List<String> = emptyList(),
    val recentFonts: List<String> = emptyList(),
    val snapping: Boolean = true,
    /** Last values set by hand in value editors, per unit ("mm", "°", "" …), most recent first. */
    val recentValues: Map<String, List<Float>> = emptyMap(),
    /** "system" or a language code such as "en", "de". */
    val language: String = "system",
)
