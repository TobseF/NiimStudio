package io.github.tobsef.niimstudio.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ValueToolsTest {

    @Test
    fun roundingExamplesFromSpec() {
        assertEquals(listOf(10f, 15f, 20f), ValueTools.roundings(11f))
        assertEquals(listOf(5f, 7f, 10f), ValueTools.roundings(7.2f))
        assertEquals(listOf(10f, 14f, 15f, 20f), ValueTools.roundings(14.4f))
    }

    @Test
    fun roundingSkipsCurrentValueAndDuplicates() {
        assertEquals(listOf(5f, 10f), ValueTools.roundings(7f))
        assertEquals(emptyList<Float>(), ValueTools.roundings(10f))
        assertEquals(listOf(10f, 13f, 15f, 20f), ValueTools.roundings(12.6f))
        assertEquals(listOf(-5f, -3f, 0f), ValueTools.roundings(-2.6f))
    }

    @Test
    fun recentValuesAreUniqueMostRecentFirst() {
        var r = emptyList<Float>()
        for (v in listOf(5f, 7.5f, 5f, 12f, 20f)) r = ValueTools.pushRecent(r, v)
        assertEquals(listOf(20f, 12f, 5f), r)
    }

    @Test
    fun plainAndParse() {
        assertEquals("7,2", ValueTools.plain(7.2f, ','))
        assertEquals("14", ValueTools.plain(14f, ','))
        assertEquals(7.25f, ValueTools.parse("7,25")!!, 1e-6f)
        assertEquals(null, ValueTools.parse("abc"))
    }

    @Test
    fun stepOptionsIncludeDefault() {
        assertEquals(listOf(0.1f, 0.125f, 0.5f, 1f, 5f), ValueTools.steps(0.125f, " mm"))
        assertEquals(listOf(1f, 5f, 15f, 45f, 90f), ValueTools.steps(15f, "°"))
    }
}
