package com.example.trnberechnung.ui.map

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trnberechnung.mapplanning.RoutePlanningUiState
import com.example.trnberechnung.mapplanning.RoutePlanningViewModel
import com.example.trnberechnung.mapplanning.RouteStatus
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.TideNodeCyan
import com.example.trnberechnung.ui.components.TideNodeDanger
import com.example.trnberechnung.ui.components.TideNodeInk
import com.example.trnberechnung.ui.components.TideNodeSuccess
import com.example.trnberechnung.ui.components.TideNodeWarning
import com.example.trnberechnung.ui.components.tideNodeGlass
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

enum class RouteDashboardMode {
    COMPACT,
    SUMMARY,
    FULL,
}

@Composable
fun RouteResultDashboard(
    state: RoutePlanningUiState,
    onOpenNauti: () -> Unit,
    onRefreshPassageWindow: () -> Unit,
    onStartNavigation: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var mode by rememberSaveable { mutableStateOf(RouteDashboardMode.FULL) }
    LaunchedEffect(state.hasCompleteRouteInput, state.routeMetrics != null) {
        if ((state.routeMetrics != null || state.hasCompleteRouteInput) && mode == RouteDashboardMode.COMPACT) {
            mode = RouteDashboardMode.FULL
        }
    }
    RouteResultDashboardContent(
        uiState = state,
        mode = mode,
        onModeChange = { mode = it },
        onOpenNauti = onOpenNauti,
        onRefreshPassageWindow = onRefreshPassageWindow,
        onNavigate = onStartNavigation,
        onSave = onSave,
        modifier = modifier,
    )
}

@Composable
fun RouteResultDashboard(
    viewModel: RoutePlanningViewModel,
    mode: RouteDashboardMode,
    onModeChange: (RouteDashboardMode) -> Unit,
    onOpenNauti: () -> Unit,
    onNavigate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RouteResultDashboardContent(
        uiState = uiState,
        mode = mode,
        onModeChange = onModeChange,
        onOpenNauti = onOpenNauti,
        onRefreshPassageWindow = viewModel::refreshPassageWindow,
        onNavigate = onNavigate,
        onSave = onSave,
        modifier = modifier,
    )
}

@Composable
private fun RouteResultDashboardContent(
    uiState: RoutePlanningUiState,
    mode: RouteDashboardMode,
    onModeChange: (RouteDashboardMode) -> Unit,
    onOpenNauti: () -> Unit,
    onRefreshPassageWindow: () -> Unit,
    onNavigate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWorking = uiState.isCalculating || uiState.isSearchingPassageWindow

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .tideNodeGlass(cornerRadius = 32.dp, elevation = 16.dp, alpha = 0.82f)
                .padding(bottom = 14.dp) // padding top and sides handled inside to allow full-width progress bar
                .testTag("route_result_dashboard"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isWorking) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)),
                color = TideNodeCyan,
                trackColor = Color.Transparent,
            )
        } else {
            Spacer(Modifier.height(3.dp))
        }

        Box(modifier = Modifier.padding(horizontal = 18.dp)) {
            DashboardDragHandle(
                mode = mode,
                onModeChange = onModeChange,
            )
        }

        Column(
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (mode != RouteDashboardMode.COMPACT) {
                RouteStatusHeader(uiState)
                if (uiState.routeStatus == RouteStatus.NICHT_BEFAHRBAR || uiState.routeStatus == RouteStatus.EINGESCHRAENKT) {
                    SafetyFailureSection(
                        uiState = uiState
                    )
                }
            }

            NautiDashboardRow(
                compact = mode == RouteDashboardMode.COMPACT,
                onClick = onOpenNauti,
            )

            if (mode != RouteDashboardMode.COMPACT) {
                DashboardPassageRow(
                    uiState = uiState,
                    onRefresh = onRefreshPassageWindow,
                )
                DashboardMetrics(uiState)
            }

            if (mode == RouteDashboardMode.FULL) {
                if (uiState.messages.isNotEmpty()) {
                    Text(
                        uiState.messages.first(),
                        color = Color(0xFF696D74),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                DashboardActions(
                    uiState = uiState,
                    onNavigate = onNavigate,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun DashboardDragHandle(
    mode: RouteDashboardMode,
    onModeChange: (RouteDashboardMode) -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val handleColor = if (isDark) Color(0xFF64748B) else Color(0xFF92969B)
    val dragThreshold = with(LocalDensity.current) { 36.dp.toPx() }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .pointerInput(mode, dragThreshold) {
                    var accumulatedDrag = 0f
                    detectVerticalDragGestures(
                        onDragStart = { accumulatedDrag = 0f },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            accumulatedDrag += dragAmount
                        },
                        onDragEnd = {
                            when {
                                accumulatedDrag <= -dragThreshold ->
                                    onModeChange(mode.expand())

                                accumulatedDrag >= dragThreshold ->
                                    onModeChange(mode.collapse())
                            }
                        },
                    )
                }
                .clickable { onModeChange(if (mode == RouteDashboardMode.COMPACT) RouteDashboardMode.FULL else RouteDashboardMode.COMPACT) }
                .testTag("route_dashboard_handle"),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = 42.dp, height = 5.dp)
                .clip(CircleShape)
                .background(handleColor),
        )
    }
}

@Composable
private fun SafetyFailureSection(
    uiState: RoutePlanningUiState,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (isDark) Color(0xFF451A1A).copy(alpha = 0.4f) else Color(0xFFFFEBEE).copy(alpha = 0.6f)
    val textColor = if (isDark) Color(0xFFFFCDD2) else Color(0xFFC62828)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, null, tint = textColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = uiState.failureReason.toDisplayString(),
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RouteStatusHeader(uiState: RoutePlanningUiState) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF696D74)
    val statusColor = if (isDark) Color.White else TideNodeInk

    val (icon, tint, bgTint) =
        when (uiState.routeStatus) {
            RouteStatus.BEFAHRBAR -> Triple(Icons.Default.CheckCircle, TideNodeSuccess, TideNodeSuccess.copy(alpha = if (isDark) 0.25f else 0.13f))
            RouteStatus.EINGESCHRAENKT -> Triple(Icons.Default.Warning, TideNodeWarning, TideNodeWarning.copy(alpha = if (isDark) 0.25f else 0.13f))
            RouteStatus.NICHT_BEFAHRBAR -> Triple(Icons.Default.Error, TideNodeDanger, TideNodeDanger.copy(alpha = if (isDark) 0.25f else 0.13f))
            RouteStatus.UNVOLLSTAENDIG -> Triple(Icons.Default.HelpOutline, if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B), if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0))
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgTint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = tint)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "Route",
                color = labelColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(
                uiState.routeStatus.displayText,
                color = statusColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun NautiDashboardRow(
    compact: Boolean,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleColor = if (isDark) Color.White else TideNodeInk
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF777B82)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = if (compact) 6.dp else 2.dp, vertical = 7.dp)
                .testTag("route_dashboard_nauti"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.AutoAwesome, null, tint = TideNodeCyan)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Nauti KI",
                color = titleColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            Text(
                "Törn, Wetter oder Gezeiten",
                color = subtitleColor,
                fontSize = 12.sp,
            )
        }
        Icon(
            imageVector = Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = TideNodeCyan,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun DashboardPassageRow(
    uiState: RoutePlanningUiState,
    onRefresh: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val rowBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.32f)
    val iconBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.40f) else Color(0x1724579F)
    val iconTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val labelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF656970)
    val valueColor = if (isDark) Color.White else TideNodeInk

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(rowBg)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Schedule, null, tint = iconTint, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Sicheres Abfahrtsfenster",
                color = labelColor,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            )
            Text(
                dashboardPassageText(uiState),
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
            uiState.passageWindow?.let { window ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Engstelle: ${window.bottleneckName ?: "Unbekannt"}",
                        color = labelColor.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    window.anchoredHighWater?.let { hw ->
                        Spacer(Modifier.width(8.dp))
                        Text(
                            " (HW: ${hw.format(DateTimeFormatter.ofPattern("HH:mm"))})",
                            color = labelColor.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
        if (uiState.isSearchingPassageWindow) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = iconTint,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(10.dp))
        } else {
            IconButton(
                onClick = onRefresh,
                enabled = uiState.hasCompleteRouteInput,
            ) {
                Icon(Icons.Default.Refresh, "Passagefenster aktualisieren", tint = iconTint)
            }
        }
    }
}

@Composable
private fun DashboardMetrics(uiState: RoutePlanningUiState) {
    val metrics = uiState.routeMetrics
    val isLoading = uiState.isCalculating

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardMetric(
            label = "REISEZEIT",
            value = metrics?.travelTime?.toTravelText() ?: "–",
            icon = Icons.Default.HourglassTop,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        DashboardMetric(
            label = "ANKUNFT",
            value =
                metrics?.arrival?.format(
                    DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY),
                ) ?: "–",
            icon = Icons.Default.Flag,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        DashboardMetric(
            label = "DISTANZ",
            value = metrics?.distanceNm?.let { String.format(Locale.GERMANY, "%.1f nm", it) } ?: "–",
            icon = Icons.Default.Straighten,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardMetric(
            label = "WuK (Netto)",
            value =
                metrics?.worstUnderKeelClearanceMeters?.let {
                    String.format(Locale.GERMANY, "%.2f m", it)
                } ?: "–",
            icon = Icons.Default.Water,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        DashboardMetric(
            label = "ERF. TIEFE",
            value =
                metrics?.let {
                    String.format(Locale.GERMANY, "%.2f m", it.requiredDepthMeters)
                } ?: "–",
            icon = Icons.Default.Straighten,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        DashboardMetric(
            label = "DIESEL",
            value =
                metrics?.totalDieselLiters?.let {
                    String.format(Locale.GERMANY, "%.1f l", it)
                } ?: "–",
            icon = Icons.Default.LocalGasStation,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DashboardMetric(
            label = "ENGSTELLE",
            value = metrics?.worstClearanceName ?: "–",
            icon = Icons.Default.Navigation,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        DashboardMetric(
            label = "rwK (Mittel)",
            value = metrics?.averageTrueCourseDegrees?.let {
                String.format(Locale.GERMANY, "%03d°", it.roundToInt())
            } ?: "–",
            icon = Icons.Default.CompassCalibration,
            isLoading = isLoading,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun DashboardMetric(
    label: String,
    value: String,
    icon: ImageVector,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val metricBg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.22f)
    val iconBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.40f) else Color(0x1524579F)
    val iconTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val labelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF676B72)
    val valueColor = if (isDark) Color.White else TideNodeInk

    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(metricBg)
                .padding(10.dp),
    ) {
        Box(
            Modifier
                .size(31.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            maxLines = 1,
        )
        if (isLoading && value == "–") {
            Box(
                Modifier
                    .width(40.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(labelColor.copy(alpha = 0.3f * alpha))
            )
        } else {
            Text(
                value,
                color = valueColor.copy(alpha = if (isLoading) alpha else 1f),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DashboardActions(
    uiState: RoutePlanningUiState,
    onNavigate: () -> Unit,
    onSave: () -> Unit,
) {
    val routeAvailable = uiState.routeMetrics != null || uiState.hasCompleteRouteInput
    val navigationEnabled = uiState.hasCompleteRouteInput || uiState.routeMetrics != null

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onSave,
            enabled = routeAvailable,
            modifier = Modifier.weight(1f).height(50.dp).testTag("route_dashboard_save"),
            shape = RoundedCornerShape(26.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = TideNodeBlue,
                    contentColor = Color.White,
                    disabledContainerColor = TideNodeBlue.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f),
                ),
        ) {
            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Speichern", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
        Button(
            onClick = onNavigate,
            enabled = navigationEnabled,
            modifier = Modifier.weight(1f).height(50.dp).testTag("route_dashboard_navigation"),
            shape = RoundedCornerShape(26.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = TideNodeCyan,
                    contentColor = Color.White,
                    disabledContainerColor = TideNodeCyan.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.6f),
                ),
        ) {
            Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Fahrt starten", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }
}

private val RouteStatus.displayText: String
    get() =
        when (this) {
            RouteStatus.BEFAHRBAR -> "Befahrbar"
            RouteStatus.EINGESCHRAENKT -> "Befahrbar mit Einschränkungen"
            RouteStatus.NICHT_BEFAHRBAR -> "Nicht befahrbar"
            RouteStatus.UNVOLLSTAENDIG -> "Unvollständig"
        }

private fun RouteDashboardMode.expand(): RouteDashboardMode =
    when (this) {
        RouteDashboardMode.COMPACT -> RouteDashboardMode.SUMMARY
        RouteDashboardMode.SUMMARY -> RouteDashboardMode.FULL
        RouteDashboardMode.FULL -> RouteDashboardMode.FULL
    }

private fun RouteDashboardMode.collapse(): RouteDashboardMode =
    when (this) {
        RouteDashboardMode.COMPACT -> RouteDashboardMode.COMPACT
        RouteDashboardMode.SUMMARY -> RouteDashboardMode.COMPACT
        RouteDashboardMode.FULL -> RouteDashboardMode.SUMMARY
    }

private fun Duration.toTravelText(): String {
    val totalMinutes = toMinutes().coerceAtLeast(0)
    return "${totalMinutes / 60}h ${totalMinutes % 60}m"
}

private fun dashboardPassageText(uiState: RoutePlanningUiState): String {
    if (uiState.isSearchingPassageWindow) return "Wird berechnet…"
    val window = uiState.passageWindow ?: return "Kein Fenster gefunden"
    val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

    val dep = uiState.departure
    return if (window.contains(dep)) {
        // Wenn wir zum gewählten Zeitpunkt bereits im Fenster sind,
        // zeigen wir an, wie lange es noch offen ist.
        "Noch offen bis ${window.end.format(formatter)} Uhr"
    } else {
        "${window.start.format(formatter)} – ${window.end.format(formatter)} Uhr"
    }
}
