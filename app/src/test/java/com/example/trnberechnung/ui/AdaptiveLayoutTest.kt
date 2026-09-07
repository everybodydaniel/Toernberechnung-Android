package com.example.trnberechnung.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptiveLayoutTest {
    @Test
    fun `phone landscape remains compact`() {
        val layout =
            classifyAdaptiveLayout(
                smallestWidthDp = 393,
                availableWidthDp = 873,
                availableHeightDp = 393,
                isLandscape = true,
            )

        assertFalse(layout.isTablet)
        assertFalse(layout.isLargeTablet)
    }

    @Test
    fun `six hundred dp smallest width enters tablet layout`() {
        val layout =
            classifyAdaptiveLayout(
                smallestWidthDp = 600,
                availableWidthDp = 600,
                availableHeightDp = 960,
                isLandscape = false,
            )

        assertTrue(layout.isTablet)
        assertFalse(layout.isLargeTablet)
    }

    @Test
    fun `wide tablet uses large available-width layout only in landscape`() {
        val landscape =
            classifyAdaptiveLayout(
                smallestWidthDp = 800,
                availableWidthDp = 1280,
                availableHeightDp = 800,
                isLandscape = true,
            )
        val portrait =
            classifyAdaptiveLayout(
                smallestWidthDp = 800,
                availableWidthDp = 800,
                availableHeightDp = 1280,
                isLandscape = false,
            )

        assertTrue(landscape.isLargeTablet)
        assertTrue(portrait.isTablet)
        assertFalse(portrait.isLargeTablet)
    }
}
