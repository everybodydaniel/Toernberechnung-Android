package com.example.trnberechnung.ui.map

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trnberechnung.mapplanning.Harbour
import com.example.trnberechnung.mapplanning.HarbourCatalog
import com.example.trnberechnung.mapplanning.HarbourId
import com.example.trnberechnung.mapplanning.IntermediateStop
import com.example.trnberechnung.mapplanning.MAP_PLANNING_ZONE_ID
import com.example.trnberechnung.mapplanning.RoutePlanningUiState
import com.example.trnberechnung.mapplanning.RoutePlanningViewModel
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.TideNodeCyan
import com.example.trnberechnung.ui.components.TideNodeInk
import com.example.trnberechnung.ui.components.tideNodeGlass
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val routeDateFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)
private val routeTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerSheet(
    viewModel: RoutePlanningViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetBg = if (isDark) Color(0xFF0F172A) else Color.White
    val sheetContentColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val handleColor = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = sheetBg,
        contentColor = sheetContentColor,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 42.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(handleColor),
            )
        },
        modifier = modifier.testTag("route_planner_sheet"),
    ) {
        RoutePlannerSheetContent(
            uiState = uiState,
            onStartSelected = viewModel::selectStart,
            onDestinationSelected = viewModel::selectDestination,
            onAddStops = viewModel::addIntermediateStops,
            onRemoveStop = viewModel::removeIntermediateStop,
            onDepartureChanged = viewModel::updateDeparture,
            onRefreshPassageWindow = viewModel::refreshPassageWindow,
            onDismiss = onDismiss,
        )
    }
}

@Composable
fun RoutePlannerSheetContent(
    uiState: RoutePlanningUiState,
    onStartSelected: (HarbourId?) -> Unit,
    onDestinationSelected: (HarbourId?) -> Unit,
    onAddStops: (Iterable<HarbourId>) -> Int,
    onRemoveStop: (java.util.UUID) -> Unit,
    onDepartureChanged: (ZonedDateTime) -> Unit,
    onRefreshPassageWindow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStopPicker by remember { mutableStateOf(false) }
    val berlinDeparture =
        remember(uiState.departure) {
            uiState.departure.withZoneSameInstant(MAP_PLANNING_ZONE_ID)
        }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val contentBg = if (isDark) Color(0xFF0F172A) else Color.White
    val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val titleColor = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF71757B)
    val iconBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.50f) else Color(0x1F2563EB)
    val iconTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue

    LazyColumn(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .background(contentBg)
                .padding(horizontal = 16.dp)
                .testTag("route_planner_content"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            PlannerSheetHeader(onDismiss)
        }
        item {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(cardBg)
                        .border(1.dp, cardBorder, RoundedCornerShape(28.dp))
                        .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Törn planen",
                            color = titleColor,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 30.sp,
                        )
                        Text(
                            "Route, Abfahrt und Zwischenstopps",
                            color = subtitleColor,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Route, null, tint = iconTint)
                    }
                }

                HarbourSelector(
                    label = "Starthafen",
                    selected = uiState.startHarbourId?.let(HarbourCatalog::get),
                    excluded = setOfNotNull(uiState.destinationHarbourId),
                    onSelected = onStartSelected,
                    testTag = "route_start_selector",
                )

                StopsHeader(
                    enabled =
                        HarbourCatalog.all.any { harbour ->
                            harbour.id != uiState.startHarbourId &&
                                harbour.id != uiState.destinationHarbourId &&
                                uiState.intermediateStops.none { it.harbourId == harbour.id }
                        },
                    onAdd = { showStopPicker = true },
                )
                if (uiState.intermediateStops.isEmpty()) {
                    Text(
                        "Noch keine Zwischenstopps",
                        modifier = Modifier.padding(horizontal = 10.dp),
                        color = Color(0xFF858990),
                        fontSize = 14.sp,
                    )
                } else {
                    uiState.intermediateStops.forEachIndexed { index, stop ->
                        IntermediateStopRow(
                            order = index + 1,
                            stop = stop,
                            onRemove = { onRemoveStop(stop.id) },
                        )
                    }
                }

                HarbourSelector(
                    label = "Zielhafen",
                    selected = uiState.destinationHarbourId?.let(HarbourCatalog::get),
                    excluded = setOfNotNull(uiState.startHarbourId),
                    onSelected = onDestinationSelected,
                    testTag = "route_destination_selector",
                )

                DepartureRow(
                    departure = berlinDeparture,
                    onDepartureChanged = onDepartureChanged,
                )
            }
        }

        item {
            PassageWindowCard(
                uiState = uiState,
                onRefresh = onRefreshPassageWindow,
            )
        }
        item {
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showStopPicker) {
        IntermediateStopsPicker(
            existingStops = uiState.intermediateStops,
            startHarbourId = uiState.startHarbourId,
            destinationHarbourId = uiState.destinationHarbourId,
            onAdd = {
                onAddStops(it)
                showStopPicker = false
            },
            onDismiss = { showStopPicker = false },
        )
    }
}

@Composable
private fun PlannerSheetHeader(onDismiss: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (isDark) Color.White else TideNodeInk
    val closeBg = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.72f)
    val closeTint = if (isDark) Color(0xFFF8FAFC) else TideNodeInk

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(48.dp))
        Text(
            "Törn planen",
            modifier = Modifier.weight(1f),
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 21.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(
            onClick = onDismiss,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(closeBg)
                    .testTag("route_planner_close"),
        ) {
            Icon(Icons.Default.Close, "Törnplanung schließen", tint = closeTint)
        }
    }
}

@Composable
private fun HarbourSelector(
    label: String,
    selected: Harbour?,
    excluded: Set<HarbourId>,
    onSelected: (HarbourId?) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF73777D)
    val selectorBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF5F6FA).copy(alpha = 0.88f)
    val iconBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.50f) else Color(0x1624579F)
    val iconTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val nameColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF858990)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            color = labelColor,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(selectorBg)
                        .clickable { expanded = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                        .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Anchor, null, tint = iconTint, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        selected?.name ?: "Nicht gewählt",
                        color = nameColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    selected?.let {
                        Text(
                            it.subtitle,
                            color = subtitleColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(Icons.Default.KeyboardArrowDown, null, tint = TideNodeCyan)
            }
            val menuBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF7FAFC)
            val menuTextPrimary = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
            val menuTextSecondary = if (isDark) Color(0xFF94A3B8) else Color(0xFF858990)

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier =
                    Modifier
                        .background(menuBg)
                        .testTag("${testTag}_menu"),
            ) {
                DropdownMenuItem(
                    text = { Text("Nicht gewählt", color = menuTextPrimary) },
                    onClick = {
                        onSelected(null)
                        expanded = false
                    },
                )
                HarbourCatalog.all.forEach { harbour ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(harbour.name, fontWeight = FontWeight.Bold, color = menuTextPrimary)
                                Text(harbour.subtitle, color = menuTextSecondary, fontSize = 12.sp)
                            }
                        },
                        onClick = {
                            onSelected(harbour.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StopsHeader(
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Route, null, tint = Color(0xFF777B82), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            "Zwischenstopps",
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF666A71),
        )
        IconButton(
            onClick = onAdd,
            enabled = enabled,
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) TideNodeCyan else Color(0xFFD5D8DC),
                    )
                    .testTag("route_add_stops"),
        ) {
            Icon(Icons.Default.Add, "Zwischenstopps hinzufügen", tint = Color.White)
        }
    }
}

@Composable
private fun IntermediateStopRow(
    order: Int,
    stop: IntermediateStop,
    onRemove: () -> Unit,
) {
    val harbour = HarbourCatalog[stop.harbourId]
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val rowBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF5F6FA).copy(alpha = 0.86f)
    val badgeBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.50f) else Color(0x1E24579F)
    val badgeText = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val nameColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF858990)
    val closeIconTint = if (isDark) Color(0xFF94A3B8) else Color(0xFF8A8E94)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(rowBg)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
                .testTag("route_stop_${stop.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(badgeBg),
            contentAlignment = Alignment.Center,
        ) {
            Text("$order", color = badgeText, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                harbour.name,
                fontWeight = FontWeight.Bold,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                harbour.subtitle,
                color = subtitleColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, "Zwischenstopp entfernen", tint = closeIconTint)
        }
    }
}

@Composable
private fun DepartureRow(
    departure: ZonedDateTime,
    onDepartureChanged: (ZonedDateTime) -> Unit,
) {
    val context = LocalContext.current
    val openDatePicker = {
        DatePickerDialog(
            context,
            { _, year, month, day ->
                onDepartureChanged(
                    ZonedDateTime.of(
                        LocalDate.of(year, month + 1, day),
                        departure.toLocalTime(),
                        MAP_PLANNING_ZONE_ID,
                    ),
                )
            },
            departure.year,
            departure.monthValue - 1,
            departure.dayOfMonth,
        ).show()
    }
    val openTimePicker = {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                onDepartureChanged(
                    ZonedDateTime.of(
                        departure.toLocalDate(),
                        LocalTime.of(hour, minute),
                        MAP_PLANNING_ZONE_ID,
                    ),
                )
            },
            departure.hour,
            departure.minute,
            true,
        ).show()
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val rowBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF5F6FA).copy(alpha = 0.9f)

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(rowBg)
                .padding(12.dp),
    ) {
        val stackControls = this.maxWidth < 290.dp
        val useCompactChips = this.maxWidth < 370.dp

        if (stackControls) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DepartureLabel()
                DepartureControls(
                    departure = departure,
                    showChipIcons = false,
                    onDateClick = openDatePicker,
                    onTimeClick = openTimePicker,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DepartureLabel(modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                DepartureControls(
                    departure = departure,
                    showChipIcons = !useCompactChips,
                    onDateClick = openDatePicker,
                    onTimeClick = openTimePicker,
                )
            }
        }
    }
}

@Composable
private fun DepartureLabel(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labelColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color(0xFF13B8AA)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.CalendarMonth, null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Abfahrt",
            color = labelColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DepartureControls(
    departure: ZonedDateTime,
    showChipIcons: Boolean,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlannerTimeChip(
            text = departure.format(routeDateFormatter),
            icon = Icons.Default.CalendarMonth.takeIf { showChipIcons },
            contentDescription = "Abfahrtsdatum wählen",
            testTag = "route_departure_date",
            onClick = onDateClick,
        )
        Spacer(Modifier.width(6.dp))
        PlannerTimeChip(
            text = departure.format(routeTimeFormatter),
            icon = Icons.Default.Schedule.takeIf { showChipIcons },
            contentDescription = "Abfahrtszeit wählen",
            testTag = "route_departure_time",
            onClick = onTimeClick,
        )
    }
}

@Composable
private fun PlannerTimeChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    contentDescription: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val chipBg = if (isDark) Color(0xFF1E293B) else Color(0xFFDDE0E6)
    val chipText = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val iconTint = if (isDark) Color(0xFF94A3B8) else Color(0xFF60646A)

    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(chipBg)
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 9.dp)
                .testTag(testTag)
                .semantics { this.contentDescription = contentDescription },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(15.dp), tint = iconTint)
            Spacer(Modifier.width(5.dp))
        }
        Text(text, color = chipText, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PassageWindowCard(
    uiState: RoutePlanningUiState,
    onRefresh: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val titleColor = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF73777D)
    val refreshTint = if (isDark) Color(0xFF60A5FA) else TideNodeInk

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                .padding(18.dp)
                .testTag("route_passage_window"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Schedule, null, tint = titleColor)
        Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Sichere Passagefenster",
                    color = titleColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                )
                Spacer(Modifier.height(4.dp))
                if (uiState.isSearchingPassageWindow) {
                    Text("Wird berechnet…", color = subtitleColor)
                } else if (uiState.passageWindows.isEmpty()) {
                    Text(
                        "Für diesen Tag liegt kein sicheres Passagefenster vor.",
                        color = subtitleColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    uiState.passageWindows.forEach { window ->
                        Column(Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${window.start.format(routeTimeFormatter)} – ${window.end.format(routeTimeFormatter)} Uhr",
                                color = subtitleColor,
                                fontWeight = FontWeight.Bold,
                            )
                            window.anchoredHighWater?.let { hw ->
                                Text(
                                    "Wattenhoch: ${hw.format(routeTimeFormatter)} Uhr",
                                    color = titleColor.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                uiState.passageWindows.firstOrNull()?.bottleneckName?.let {
                    Text(
                        "Engstelle: $it",
                        color = subtitleColor,
                        fontSize = 12.sp,
                    )
                }
            }
        if (uiState.isSearchingPassageWindow) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = titleColor,
            )
        } else {
            IconButton(
                onClick = onRefresh,
                enabled = uiState.hasCompleteRouteInput,
            ) {
                Icon(Icons.Default.Refresh, "Passagefenster aktualisieren", tint = refreshTint)
            }
        }
    }
}

@Composable
private fun IntermediateStopsPicker(
    existingStops: List<IntermediateStop>,
    startHarbourId: HarbourId?,
    destinationHarbourId: HarbourId?,
    onAdd: (List<HarbourId>) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val pending = remember { mutableStateListOf<HarbourId>() }
    val unavailable =
        remember(existingStops, startHarbourId, destinationHarbourId) {
            emptySet<HarbourId>()
        }
    val filtered =
        remember(query) {
            val needle = query.trim()
            HarbourCatalog.all.filter { harbour ->
                needle.isBlank() ||
                    harbour.name.contains(needle, ignoreCase = true) ||
                    harbour.subtitle.contains(needle, ignoreCase = true)
            }
        }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetBg = if (isDark) Color(0xFF0F172A) else Color.White
    val handleColor = if (isDark) Color(0xFF475569) else Color(0xFFB8BBC2)
    val backBtnBg = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.72f)
    val backBtnTint = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)

    var dragOffset by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.92f)
                        .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                        .background(sheetBg)
                        .clickable(
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                            indication = null,
                        ) { /* consume clicks so they don't close */ }
                        .padding(16.dp)
                        .testTag("intermediate_stops_picker"),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .fillMaxWidth()
                        .height(28.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragStart = { dragOffset = 0f },
                                onVerticalDrag = { change, amount ->
                                    change.consume()
                                    if (amount > 0) dragOffset += amount
                                },
                                onDragEnd = {
                                    if (dragOffset > 100f) onDismiss()
                                    dragOffset = 0f
                                },
                                onDragCancel = { dragOffset = 0f },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(width = 42.dp, height = 5.dp)
                            .clip(CircleShape)
                            .background(handleColor),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(backBtnBg),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Zwischenstoppauswahl schließen",
                            tint = backBtnTint,
                        )
                    }
                    Text(
                        "Zwischenstopps",
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 21.sp,
                        color = titleColor,
                    )
                    Spacer(Modifier.size(46.dp))
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().testTag("intermediate_stop_search"),
                    placeholder = { Text("Hafen suchen", color = if (isDark) Color(0xFF94A3B8) else Color(0xFF9CA3AF)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF6B7280)) },
                    singleLine = true,
                    shape = RoundedCornerShape(22.dp),
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.74f),
                            unfocusedContainerColor = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.64f),
                            focusedBorderColor = if (isDark) Color(0xFF334155) else TideNodeBlue.copy(alpha = 0.55f),
                            unfocusedBorderColor = if (isDark) Color(0xFF334155) else Color.White.copy(alpha = 0.76f),
                            focusedTextColor = titleColor,
                            unfocusedTextColor = titleColor,
                        ),
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id.rawValue }) { harbour ->
                        val enabled = harbour.id !in unavailable
                        val order = pending.indexOf(harbour.id).takeIf { it >= 0 }?.plus(1)
                        StopPickerRow(
                            harbour = harbour,
                            enabled = enabled,
                            selectionOrder = order,
                            onToggle = {
                                if (harbour.id in pending) {
                                    pending.remove(harbour.id)
                                } else {
                                    pending += harbour.id
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { onAdd(pending.toList()) },
                    enabled = pending.isNotEmpty(),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("intermediate_stops_add"),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = TideNodeBlue,
                            disabledContainerColor = if (isDark) Color(0xFF334155) else Color(0xFFD7DADE),
                        ),
                ) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (pending.isEmpty()) {
                            "Zwischenstopps auswählen"
                        } else {
                            "${pending.size} Zwischenstopp" +
                                if (pending.size == 1) " hinzufügen" else "s hinzufügen"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun StopPickerRow(
    harbour: Harbour,
    enabled: Boolean,
    selectionOrder: Int?,
    onToggle: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val rowBg = if (isDark) {
        Color(0xFF1E293B).copy(alpha = if (enabled) 0.70f else 0.35f)
    } else {
        Color.White.copy(alpha = if (enabled) 0.58f else 0.30f)
    }
    val iconBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.40f) else Color(0x1724579F)
    val iconTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val nameColor = if (isDark) {
        if (enabled) Color(0xFFF8FAFC) else Color(0xFF64748B)
    } else {
        if (enabled) TideNodeInk else Color(0xFF9A9DA2)
    }
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF8A8E94)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(rowBg)
                .clickable(enabled = enabled, onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(iconBg),
            contentAlignment = Alignment.Center,
        ) {
            if (selectionOrder != null) {
                Text(
                    "$selectionOrder",
                    color = iconTint,
                    fontWeight = FontWeight.ExtraBold,
                )
            } else {
                Icon(Icons.Default.Anchor, null, tint = iconTint)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                harbour.name,
                color = nameColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (enabled) harbour.subtitle else "Bereits in der Route",
                color = subtitleColor,
                fontSize = 12.sp,
            )
        }
        Checkbox(
            checked = selectionOrder != null,
            onCheckedChange = if (enabled) ({ onToggle() }) else null,
            enabled = enabled,
        )
    }
}
