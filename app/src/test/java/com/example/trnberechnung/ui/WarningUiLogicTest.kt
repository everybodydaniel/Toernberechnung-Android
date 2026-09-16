package com.example.trnberechnung.ui

import androidx.compose.ui.unit.dp
import com.example.trnberechnung.warnings.OfficialLinkKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarningUiLogicTest {
    @Test
    fun `pdf sticker is shown only for an actual pdf link kind`() {
        assertTrue(showsPdfBadge(OfficialLinkKind.PDF))
        assertFalse(showsPdfBadge(OfficialLinkKind.WEB))
    }

    @Test
    fun `header badge is shown only while unseen messages exist`() {
        assertFalse(shouldShowWarningBadge(-1))
        assertFalse(shouldShowWarningBadge(0))
        assertTrue(shouldShowWarningBadge(1))
        assertTrue(shouldShowWarningBadge(42))
    }

    @Test
    fun `warning content uses compact phone values`() {
        val phone =
            classifyAdaptiveLayout(
                smallestWidthDp = 393,
                availableWidthDp = 393,
                availableHeightDp = 873,
                isLandscape = false,
            )

        assertEquals(16.dp, warningScreenHorizontalPadding(phone))
        assertEquals(720.dp, warningScreenMaxContentWidth(phone))
    }

    @Test
    fun `warning content expands padding and width for regular and large tablets`() {
        val tablet =
            classifyAdaptiveLayout(
                smallestWidthDp = 600,
                availableWidthDp = 800,
                availableHeightDp = 1280,
                isLandscape = false,
            )
        val largeTablet =
            classifyAdaptiveLayout(
                smallestWidthDp = 800,
                availableWidthDp = 1280,
                availableHeightDp = 800,
                isLandscape = true,
            )

        assertEquals(24.dp, warningScreenHorizontalPadding(tablet))
        assertEquals(840.dp, warningScreenMaxContentWidth(tablet))
        assertEquals(32.dp, warningScreenHorizontalPadding(largeTablet))
        assertEquals(1100.dp, warningScreenMaxContentWidth(largeTablet))
    }

    @Test
    fun `warning actions use readable layouts for phone and tablet widths`() {
        assertEquals(
            WarningActionLayout.NARROW_STACKED,
            warningActionLayout(256.dp),
        )
        assertEquals(
            WarningActionLayout.NARROW_STACKED,
            warningActionLayout(329.dp),
        )
        assertEquals(
            WarningActionLayout.COMPACT_SINGLE_ROW,
            warningActionLayout(400.dp),
        )
        assertEquals(
            WarningActionLayout.REGULAR_SINGLE_ROW,
            warningActionLayout(720.dp),
        )
        assertEquals(
            WarningActionLayout.REGULAR_SINGLE_ROW,
            warningActionLayout(1068.dp),
        )
    }

    @Test
    fun `larger phone font keeps every warning action on an unclipped row`() {
        assertEquals(
            WarningActionLayout.NARROW_STACKED,
            warningActionLayout(329.dp, fontScale = 1.3f),
        )
        assertEquals(
            WarningActionLayout.NARROW_STACKED,
            warningActionLayout(720.dp, fontScale = 1.3f),
        )
        assertEquals(11f, warningCompactActionFontSizeSp(fontScale = 1f), 0.001f)
        assertEquals(
            14.3f,
            warningCompactActionFontSizeSp(fontScale = 2f) * 2f,
            0.001f,
        )
    }
}
