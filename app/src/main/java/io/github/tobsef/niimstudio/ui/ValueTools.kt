package io.github.tobsef.niimstudio.ui

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Helpers for the inline value editor of [StepperRow]. */
object ValueTools {
    private const val EPS = 1e-4f

    /**
     * Rounding suggestions in ascending order: nearest whole number, the 5-step below and
     * above, and the next 10 up. Duplicates and the current value itself are left out.
     * Examples: 11 → [10, 15, 20], 7.2 → [5, 7, 10], 14.4 → [10, 14, 15, 20].
     */
    fun roundings(v: Float): List<Float> {
        val c = listOf(
            v.roundToInt().toFloat(),
            floor(v / 5f + EPS) * 5f,
            ceil(v / 5f - EPS) * 5f,
            ceil(v / 10f - EPS) * 10f,
        )
        val out = mutableListOf<Float>()
        for (x0 in c) {
            val x = x0 + 0f // turns -0 into 0
            if (abs(x - v) > EPS && out.none { abs(it - x) < EPS }) out += x
        }
        return out.sorted()
    }

    /** Step sizes offered for a value with the given unit; [step] is the field's default. */
    fun steps(step: Float, unit: String): List<Float> = when (unit.trim()) {
        "mm" -> listOf(0.1f, 0.5f, 1f, 5f)
        "°" -> listOf(1f, 5f, 15f, 45f, 90f)
        "pt" -> listOf(0.5f, 1f, 5f)
        "ms" -> listOf(1f, 5f, 10f, 50f)
        else -> listOf(step, step * 5, step * 10)
    }.let { if (it.none { s -> abs(s - step) < EPS }) (it + step).sorted() else it }

    /** Most-recent-first list of [max] values with [v] in front. */
    fun pushRecent(list: List<Float>, v: Float, max: Int = 3): List<Float> =
        (listOf(v) + list.filter { abs(it - v) > EPS }).take(max)

    /** Number without unit for the input field, using the given decimal separator. */
    fun plain(v: Float, decimalSep: Char): String {
        if (abs(v - v.roundToInt()) < EPS) return v.roundToInt().toString()
        val s = "%.3f".format(java.util.Locale.ROOT, v).trimEnd('0').trimEnd('.')
        return s.replace('.', decimalSep)
    }

    fun parse(s: String): Float? = s.trim().replace(',', '.').toFloatOrNull()
}
