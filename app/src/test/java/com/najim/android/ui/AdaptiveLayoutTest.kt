package com.najim.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveLayoutTest {
    @Test fun compactPadding() = assertEquals(12, AdaptiveLayout.sidePadding(320f))
    @Test fun regularPadding() = assertEquals(20, AdaptiveLayout.sidePadding(360f))
    @Test fun narrowMetricAreaUsesOneColumn() = assertEquals(1, AdaptiveLayout.metricColumns(288f, 1f))
    @Test fun enoughContentWidthUsesTwoColumns() = assertEquals(2, AdaptiveLayout.metricColumns(350f, 1f))
    @Test fun largerFontSwitchesToOneColumn() = assertEquals(1, AdaptiveLayout.metricColumns(350f, 1.3f))
    @Test fun wideLayoutCanFitLargerFont() = assertEquals(2, AdaptiveLayout.metricColumns(500f, 1.5f))
    @Test fun invalidInputsFailSafe() {
        assertEquals(1, AdaptiveLayout.metricColumns(Float.NaN, 1f))
        assertEquals(1, AdaptiveLayout.metricColumns(500f, 0f))
        assertEquals(1, AdaptiveLayout.metricColumns(500f, Float.POSITIVE_INFINITY))
    }
}
