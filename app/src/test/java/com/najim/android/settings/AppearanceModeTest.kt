package com.najim.android.settings

import org.junit.Assert.*
import org.junit.Test

class AppearanceModeTest {
    @Test fun systemFollowsDevice() {
        assertTrue(AppearanceMode.SYSTEM.usesDark(true))
        assertFalse(AppearanceMode.SYSTEM.usesDark(false))
    }
    @Test fun explicitLightOverridesSystem() {
        assertFalse(AppearanceMode.LIGHT.usesDark(true))
        assertFalse(AppearanceMode.LIGHT.usesDark(false))
    }
    @Test fun explicitDarkOverridesSystem() {
        assertTrue(AppearanceMode.DARK.usesDark(true))
        assertTrue(AppearanceMode.DARK.usesDark(false))
    }
    @Test fun persistedValuesRoundTripAndUnknownFallsBack() {
        for (mode in AppearanceMode.entries) assertEquals(mode, AppearanceMode.fromStored(mode.storedValue))
        for (value in listOf(null, "", "unknown", "DARK")) assertEquals(AppearanceMode.SYSTEM, AppearanceMode.fromStored(value))
    }
}
