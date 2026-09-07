package com.example.trnberechnung.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance

val TideNodeInk = Color(0xFF111318)
val TideNodeBlue = Color(0xFF24579F)

/** [TideNodeBlue] lightened for dark surfaces, where the deep blue loses its contrast. */
val TideNodeBlueLight = Color(0xFF60A5FA)
val TideNodeCyan = Color(0xFF09B7D6)
val TideNodeWarning = Color(0xFFF59E0B)
val TideNodeDanger = Color(0xFFDC2626)
val TideNodeSuccess = Color(0xFF15803D)

@Composable
fun Modifier.tideNodeGlass(
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 10.dp,
    tint: Color = Color.Unspecified,
    alpha: Float = 0.85f,
): Modifier {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val shape = RoundedCornerShape(cornerRadius)

    val actualTint = if (tint != Color.Unspecified) tint else if (isDark) Color(0xFF1E293B) else Color.White
    val border = if (isDark) Color(0xFF334155) else Color.White.copy(alpha = 0.68f)

    val gradientColors = if (isDark) {
        listOf(
            actualTint.copy(alpha = alpha.coerceIn(0f, 1f)),
            Color(0xFF0F172A).copy(alpha = alpha.coerceIn(0f, 1f)),
        )
    } else {
        listOf(
            actualTint.copy(alpha = alpha.coerceIn(0f, 1f)),
            Color(0xFFF5FBFF).copy(alpha = (alpha - 0.12f).coerceAtLeast(0f)),
            Color(0xFFF5FFE9).copy(alpha = (alpha - 0.18f).coerceAtLeast(0f)),
        )
    }

    return shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.14f),
        spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.18f),
    )
        .clip(shape)
        .background(Brush.linearGradient(gradientColors))
        .border(1.dp, border, shape)
}

@Composable
fun Modifier.tideNodeDarkGlass(
    cornerRadius: Dp = 28.dp,
    elevation: Dp = 10.dp,
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    return shadow(
        elevation = elevation,
        shape = shape,
        ambientColor = Color.Black.copy(alpha = 0.24f),
        spotColor = Color.Black.copy(alpha = 0.30f),
    )
        .clip(shape)
        .background(
            Brush.linearGradient(
                listOf(
                    Color(0xD9222D3B),
                    Color(0xC9131D2B),
                ),
            ),
        )
        .border(1.dp, Color.White.copy(alpha = 0.22f), shape)
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 23.dp,
    iconTint: Color = Color.Unspecified,
    badge: (@Composable BoxScope.() -> Unit)? = null,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bg = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.85f)
    val border = if (isDark) Color(0xFF334155) else Color.White.copy(alpha = 0.85f)
    val resolvedTint = if (iconTint != Color.Unspecified) iconTint else if (isDark) TideNodeBlueLight else TideNodeBlue

    Box(
        modifier =
            modifier
                .size(size)
                .shadow(
                    elevation = 7.dp,
                    shape = CircleShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.28f else 0.12f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.16f),
                )
                .clip(CircleShape)
                .background(bg)
                .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.matchParentSize(),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = resolvedTint,
                modifier = Modifier.size(iconSize),
            )
        }
        badge?.invoke(this)
    }
}
