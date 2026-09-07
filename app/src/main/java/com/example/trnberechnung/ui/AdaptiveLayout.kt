package com.example.trnberechnung.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal const val TABLET_MIN_SMALLEST_WIDTH_DP = 600
internal const val LARGE_TABLET_MIN_AVAILABLE_WIDTH_DP = 840

/**
 * The small set of window facts the existing screens need for their tablet-only branches.
 *
 * Compact values deliberately do not live here: every screen keeps using its existing phone
 * dimensions below [TABLET_MIN_SMALLEST_WIDTH_DP]. This prevents a tablet pass from subtly
 * changing the established handset layout.
 */
@Immutable
internal data class AdaptiveLayout(
    val isTablet: Boolean,
    val isLargeTablet: Boolean,
    val isLandscape: Boolean,
    val availableWidthDp: Int,
    val availableHeightDp: Int,
) {
    val mainContentMaxWidth: Dp
        get() =
            if (isLargeTablet) {
                TabletLayoutTokens.LargeMainContentMaxWidth
            } else {
                TabletLayoutTokens.MainContentMaxWidth
            }

    val compactContentMaxWidth: Dp
        get() =
            if (isLargeTablet) {
                TabletLayoutTokens.LargeCompactContentMaxWidth
            } else {
                TabletLayoutTokens.CompactContentMaxWidth
            }

    val overlayMaxWidth: Dp
        get() =
            if (isLargeTablet) {
                TabletLayoutTokens.LargeOverlayMaxWidth
            } else {
                TabletLayoutTokens.OverlayMaxWidth
            }

    val horizontalScreenPadding: Dp
        get() =
            if (isLargeTablet) {
                TabletLayoutTokens.LargeScreenPadding
            } else {
                TabletLayoutTokens.ScreenPadding
            }
}

/** Tablet-only values shared by screens; handset dimensions remain local and unchanged. */
internal object TabletLayoutTokens {
    val MainContentMaxWidth = 840.dp
    val LargeMainContentMaxWidth = 1100.dp
    val CompactContentMaxWidth = 720.dp
    val LargeCompactContentMaxWidth = 840.dp
    val OverlayMaxWidth = 640.dp
    val LargeOverlayMaxWidth = 760.dp
    val ScreenPadding = 24.dp
    val LargeScreenPadding = 32.dp
    val CardPadding = 24.dp
    val CardCornerRadius = 28.dp
    val PrimaryControlHeight = 60.dp
    val LargePrimaryControlHeight = 64.dp
    val StandardIconSize = 28.dp
    val SectionSpacing = 20.dp
}

@Composable
internal fun currentAdaptiveLayout(): AdaptiveLayout {
    val configuration = LocalConfiguration.current
    return classifyAdaptiveLayout(
        smallestWidthDp = configuration.smallestScreenWidthDp,
        availableWidthDp = configuration.screenWidthDp,
        availableHeightDp = configuration.screenHeightDp,
        isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
    )
}

internal fun classifyAdaptiveLayout(
    smallestWidthDp: Int,
    availableWidthDp: Int,
    availableHeightDp: Int,
    isLandscape: Boolean,
): AdaptiveLayout {
    val usableSmallestWidth =
        smallestWidthDp.takeIf { it > 0 }
            ?: minOf(availableWidthDp, availableHeightDp)
    val isTablet = usableSmallestWidth >= TABLET_MIN_SMALLEST_WIDTH_DP
    return AdaptiveLayout(
        isTablet = isTablet,
        isLargeTablet = isTablet && availableWidthDp >= LARGE_TABLET_MIN_AVAILABLE_WIDTH_DP,
        isLandscape = isLandscape,
        availableWidthDp = availableWidthDp,
        availableHeightDp = availableHeightDp,
    )
}
