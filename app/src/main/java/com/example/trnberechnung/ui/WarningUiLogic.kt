package com.example.trnberechnung.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun warningScreenHorizontalPadding(layout: AdaptiveLayout): Dp =
    if (layout.isTablet) layout.horizontalScreenPadding else 16.dp

internal fun warningScreenMaxContentWidth(layout: AdaptiveLayout): Dp =
    if (layout.isTablet) layout.mainContentMaxWidth else 720.dp

internal enum class WarningActionLayout {
    NARROW_STACKED,
    COMPACT_SINGLE_ROW,
    REGULAR_SINGLE_ROW,
}

internal fun warningActionLayout(
    availableWidth: Dp,
    fontScale: Float = 1f,
): WarningActionLayout =
    when {
        availableWidth < 360.dp || fontScale > 1.15f ->
            WarningActionLayout.NARROW_STACKED
        availableWidth >= 480.dp -> WarningActionLayout.REGULAR_SINGLE_ROW
        else -> WarningActionLayout.COMPACT_SINGLE_ROW
    }

internal fun warningCompactActionFontSizeSp(fontScale: Float): Float {
    val safeFontScale = fontScale.coerceAtLeast(1f)
    return 11f * (1.3f / safeFontScale).coerceAtMost(1f)
}

internal fun shouldShowWarningBadge(unseenWarningCount: Int): Boolean = unseenWarningCount > 0
