package dev.b18print.render

import dev.b18print.model.DataMode
import dev.b18print.model.DataSource
import org.junit.Assert.assertEquals
import org.junit.Test

class DataVarsTest {

    @Test
    fun listModeSplitsColumnsAndCountsRows() {
        val ds = DataSource(mode = DataMode.LIST, listText = "Apfel;1,99\nBirne;2,49\n\n")
        assertEquals(2, DataVars.count(ds))
        assertEquals(2, DataVars.columns(ds))
        val v = DataVars.vars(ds, 1)
        assertEquals("Birne 2,49 #2", DataVars.apply("{A} {B} #{N}", v))
    }

    @Test
    fun counterModePadsDigits() {
        val ds = DataSource(mode = DataMode.COUNTER, counterStart = 7, counterStep = 3, counterCount = 5, counterDigits = 4)
        assertEquals(5, DataVars.count(ds))
        assertEquals("Nr. 0013", DataVars.apply("Nr. {N}", DataVars.vars(ds, 2)))
    }

    @Test
    fun noneModeLeavesPlaceholders() {
        val ds = DataSource()
        assertEquals(1, DataVars.count(ds))
        assertEquals("{A}", DataVars.apply("{A}", DataVars.vars(ds, 0)))
    }

    @Test
    fun unknownColumnBecomesEmpty() {
        val ds = DataSource(mode = DataMode.LIST, listText = "x")
        assertEquals("x-", DataVars.apply("{A}-{C}", DataVars.vars(ds, 0)))
    }
}
