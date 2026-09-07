package com.example.trnberechnung.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.R
import com.example.trnberechnung.ui.AdaptiveLayout
import com.example.trnberechnung.ui.TabletLayoutTokens
import com.example.trnberechnung.ui.currentAdaptiveLayout

internal fun tideNodeAppHeaderHeight(layout: AdaptiveLayout): Dp =
    when {
        !layout.isTablet && layout.isLandscape -> 52.dp
        !layout.isTablet -> 78.dp
        layout.isLandscape && layout.isLargeTablet -> 68.dp
        layout.isLandscape -> 64.dp
        else -> 92.dp
    }

/**
 * The global app header.
 *
 * There is intentionally no refresh button any more. The one that used to sit next to the settings
 * gear was doubly bound - it reloaded the visible tab *and* toggled the screen orientation - and the
 * data it reloaded now refreshes on its own (see the lifecycle-bound loop in `MainActivity`).
 * Orientation is back to the system's auto-rotate, which the manifest never overrode.
 */
@Composable
fun TideNodeAppHeader(
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    onColoredBackground: Boolean = false,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val layout = currentAdaptiveLayout()
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val headerHeight = tideNodeAppHeaderHeight(layout)
    val logoSize =
        when {
            !layout.isTablet && layout.isLandscape -> 30.dp
            !layout.isTablet -> 42.dp
            layout.isLandscape && layout.isLargeTablet -> 40.dp
            layout.isLandscape -> 36.dp
            else -> 50.dp
        }
    val titleFontSize =
        when {
            !layout.isTablet && layout.isLandscape -> 20.sp
            !layout.isTablet -> 28.sp
            layout.isLandscape && layout.isLargeTablet -> 26.sp
            layout.isLandscape -> 24.sp
            else -> 34.sp
        }
    val buttonSize =
        when {
            !layout.isTablet && layout.isLandscape -> 44.dp
            !layout.isTablet -> 48.dp
            layout.isLandscape && !layout.isLargeTablet -> 52.dp
            else -> 56.dp
        }
    val iconSize =
        when {
            !layout.isTablet -> 23.dp
            layout.isLargeTablet -> 30.dp
            else -> TabletLayoutTokens.StandardIconSize
        }
    val horizontalPadding = if (layout.isTablet) layout.horizontalScreenPadding else 14.dp
    val logoSpacing =
        when {
            !layout.isTablet && layout.isLandscape -> 6.dp
            !layout.isTablet -> 9.dp
            layout.isLandscape -> 8.dp
            else -> 11.dp
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .then(
                    if (layout.isTablet) {
                        Modifier.padding(
                            start = safeDrawingPadding.calculateStartPadding(layoutDirection),
                            top = safeDrawingPadding.calculateTopPadding(),
                            end = safeDrawingPadding.calculateEndPadding(layoutDirection),
                        )
                    } else {
                        Modifier.windowInsetsPadding(WindowInsets.statusBars)
                    },
                )
                .height(headerHeight)
                .padding(horizontal = horizontalPadding),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.tidenode_mark),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(logoSize)
                        .clip(RoundedCornerShape(logoSize * 0.27f))
                        .testTag("app_header_logo"),
            )
            Spacer(Modifier.size(logoSpacing))
            Text(
                text = "TideNode",
                // White wherever the header floats over the nautical chart or the Revier gradient,
                // maritime blue on the plain surfaces of Crew and Logbuch (lightened in the
                // dark theme, where the deep blue loses its contrast).
                color =
                    when {
                        onColoredBackground -> Color.White
                        isDark -> TideNodeBlueLight
                        else -> TideNodeBlue
                    },
                fontSize = titleFontSize,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.8).sp,
                modifier = Modifier.testTag("app_header_wordmark").semantics { heading() },
            )
            Spacer(Modifier.weight(1f))
            GlassIconButton(
                icon = Icons.Default.Settings,
                contentDescription = "Einstellungen",
                onClick = onSettings,
                size = buttonSize,
                iconSize = iconSize,
                modifier = Modifier.testTag("app_header_settings"),
            )
        }
    }
}
