package io.github.tobsef.niimstudio.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutTest {
    private val p = LabelProfile(id = "t", name = "t", tapeWidthMm = 12f, lengthMm = 60f)

    @Test
    fun singlePanelUsesWholeLabel() {
        val d = LabelDoc(profile = p)
        assertEquals(60f, d.panelLengthMm, 1e-4f)
        assertEquals(0f, d.wrapZone, 1e-4f)
        assertEquals(60f, d.widthMm, 1e-4f)
        assertEquals(12f, d.heightMm, 1e-4f)
    }

    @Test
    fun flagSplitsLabelAroundWrapZone() {
        val d = LabelDoc(profile = p, sides = SideMode.FLAG, cableDiameterMm = 5f)
        val wrap = (5 * Math.PI).toFloat()
        assertEquals(wrap, d.wrapZone, 1e-4f)
        assertEquals((60f - wrap) / 2, d.panelLengthMm, 1e-4f)
        assertEquals(60f, d.totalLengthMm, 1e-4f)
        assertEquals(d.panelLengthMm, d.widthMm, 1e-4f)
    }

    @Test
    fun manualWrapZoneAndPortrait() {
        val d = LabelDoc(profile = p, sides = SideMode.WRAP, autoWrapZone = false, wrapZoneMm = 20f, portrait = true)
        assertEquals(20f, d.panelLengthMm, 1e-4f)
        assertEquals(12f, d.widthMm, 1e-4f)
        assertEquals(20f, d.heightMm, 1e-4f)
    }

    @Test
    fun tooShortLabelGrowsToMinimalPanels() {
        val d = LabelDoc(profile = p.copy(lengthMm = 20f), sides = SideMode.FLAG, autoWrapZone = false, wrapZoneMm = 18f)
        assertEquals(LabelDoc.MIN_PANEL_MM, d.panelLengthMm, 1e-4f)
        assertEquals(2 * LabelDoc.MIN_PANEL_MM + 18f, d.totalLengthMm, 1e-4f)
    }
}
