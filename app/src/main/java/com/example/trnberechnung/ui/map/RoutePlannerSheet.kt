package com.example.trnberechnung.ui.map

import android.app.DatePickerDialog
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.runtime.LaunchedEffect
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
import com.example.trnberechnung.mapplanning.MAX_PLANNING_SPEED_KNOTS
import com.example.trnberechnung.mapplanning.MIN_PLANNING_SPEED_KNOTS
import com.example.trnberechnung.mapplanning.PLANNING_SPEED_STEP_KNOTS
import com.example.trnberechnung.mapplanning.PassageWindow
import com.example.trnberechnung.mapplanning.RoutePlanningUiState
import com.example.trnberechnung.mapplanning.RoutePlanningViewModel
import com.example.trnberechnung.mapplanning.WaterLevelQuality
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
private val passageWindowRangeFormatter =
    DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerSheet(
    viewModel: RoutePlanningViewModel,
    onDismiss: () -> Unit,
    onCalculationCompleted: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val discardAndDismiss: () -> Unit = {
        viewModel.discardPlanning()
        onDismiss()
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val sheetBg = if (isDark) Color(0xFF0F172A) else Color.White
    val sheetContentColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val handleColor = if (isDark) Color(0xFF475569) else Color(0xFFCBD5E1)

    ModalBottomSheet(
        onDismissRequest = discardAndDismiss,
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
            onSpeedChanged = viewModel::updatePlanningSpeed,
            onCalculate = {
                if (uiState.hasCalculatedResult) {
                    onCalculationCompleted()
                } else if (!uiState.isWorking) {
                    viewModel.calculateRoute()
                }
            },
            onDismiss = discardAndDismiss,
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
    onSpeedChanged: (Double) -> Unit,
    onCalculate: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showStopPicker by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val berlinDeparture =
        remember(uiState.departure) {
            uiState.departure.withZoneSameInstant(MAP_PLANNING_ZONE_ID)
        }

    LaunchedEffect(uiState.hasCalculatedResult, uiState.error) {
        if (uiState.hasCalculatedResult || uiState.error != null) {
            listState.animateScrollToItem(1)
        }
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val contentBg = if (isDark) Color(0xFF0F172A) else Color.White
    val cardBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val titleColor = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF71757B)
    val iconBg = if (isDark) Color(0xFF1E3A8A).copy(alpha = 0.50f) else Color(0x1F2563EB)
    val iconTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .fillMaxHeight(0.96f)
                .background(contentBg)
                .testTag("route_planner_content"),
    ) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .align(Alignment.TopCenter),
        ) {
            PlannerSheetHeader(
                onDismiss = onDismiss,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            LazyColumn(
                state = listState,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
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

                        SpeedRow(
                            speedKnots = uiState.boatSettings.speedKnots,
                            enabled = !uiState.isWorking,
                            onSpeedChanged = onSpeedChanged,
                        )
                    }
                }

                item {
                    PassageWindowCard(uiState = uiState)
                }
                item {
                    Spacer(Modifier.height(4.dp))
                }
            }

            PlannerCalculateFooter(
                enabled = uiState.canCalculate,
                isWorking = uiState.isWorking,
                hasCalculatedResult = uiState.hasCalculatedResult,
                onCalculate = onCalculate,
            )
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
private fun PlannerSheetHeader(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val textColor = if (isDark) Color.White else TideNodeInk
    val closeBg = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.72f)
    val closeTint = if (isDark) Color(0xFFF8FAFC) else TideNodeInk

    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
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
private fun PlannerCalculateFooter(
    enabled: Boolean,
    isWorking: Boolean,
    hasCalculatedResult: Boolean,
    onCalculate: () -> Unit,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val footerBg = if (isDark) Color(0xFF0F172A) else Color.White

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(footerBg)
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Button(
            onClick = onCalculate,
            enabled = enabled && !isWorking,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("route_calculate"),
            shape = RoundedCornerShape(28.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = TideNodeBlue,
                    contentColor = Color.White,
                    disabledContainerColor = if (isDark) Color(0xFF334155) else Color(0xFFCBD5E1),
                    disabledContentColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                ),
        ) {
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
            } else {
                Icon(Icons.Default.Route, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = if (hasCalculatedResult) "Planungsergebnis anzeigen" else "Törn berechnen",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
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
                        LocalTime.NOON,
                        MAP_PLANNING_ZONE_ID,
                    ),
                )
            },
            departure.year,
            departure.monthValue - 1,
            departure.dayOfMonth,
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

        if (stackControls) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DepartureLabel()
                DepartureControls(
                    departure = departure,
                    onDateClick = openDatePicker,
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
                    onDateClick = openDatePicker,
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
            "Abfahrtstag",
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
    onDateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlannerTimeChip(
            text = departure.format(routeDateFormatter),
            icon = Icons.Default.CalendarMonth,
            contentDescription = "Abfahrtsdatum wählen",
            testTag = "route_departure_date",
            onClick = onDateClick,
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
private fun SpeedRow(
    speedKnots: Double,
    enabled: Boolean,
    onSpeedChanged: (Double) -> Unit,
) {
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
        val stackControls = maxWidth < 330.dp
        if (stackControls) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SpeedLabel()
                SpeedControls(
                    speedKnots = speedKnots,
                    enabled = enabled,
                    onSpeedChanged = onSpeedChanged,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedLabel(modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                SpeedControls(
                    speedKnots = speedKnots,
                    enabled = enabled,
                    onSpeedChanged = onSpeedChanged,
                )
            }
        }
    }
}

@Composable
private fun SpeedLabel(modifier: Modifier = Modifier) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val labelColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(TideNodeBlue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Speed, contentDescription = null, tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Reisegeschwindigkeit",
            color = labelColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SpeedControls(
    speedKnots: Double,
    enabled: Boolean,
    onSpeedChanged: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val controlBg = if (isDark) Color(0xFF1E293B) else Color.White
    val disabledBg = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val controlTint = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val valueColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val canDecrease = enabled && speedKnots > MIN_PLANNING_SPEED_KNOTS
    val canIncrease = enabled && speedKnots < MAX_PLANNING_SPEED_KNOTS

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                onSpeedChanged(
                    (speedKnots - PLANNING_SPEED_STEP_KNOTS)
                        .coerceIn(MIN_PLANNING_SPEED_KNOTS, MAX_PLANNING_SPEED_KNOTS),
                )
            },
            enabled = canDecrease,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canDecrease) controlBg else disabledBg)
                    .testTag("route_speed_decrease"),
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Reisegeschwindigkeit verringern",
                tint = if (canDecrease) controlTint else controlTint.copy(alpha = 0.35f),
            )
        }
        Text(
            text = String.format(Locale.GERMANY, "%.1f kn", speedKnots),
            modifier = Modifier.width(78.dp).testTag("route_speed_value"),
            color = valueColor,
            fontWeight = FontWeight.ExtraBold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
        IconButton(
            onClick = {
                onSpeedChanged(
                    (speedKnots + PLANNING_SPEED_STEP_KNOTS)
                        .coerceIn(MIN_PLANNING_SPEED_KNOTS, MAX_PLANNING_SPEED_KNOTS),
                )
            },
            enabled = canIncrease,
            modifier =
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (canIncrease) controlBg else disabledBg)
                    .testTag("route_speed_increase"),
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Reisegeschwindigkeit erhöhen",
                tint = if (canIncrease) controlTint else controlTint.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun PassageWindowCard(
    uiState: RoutePlanningUiState,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val cardBorder = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
    val titleColor = if (isDark) Color(0xFF60A5FA) else TideNodeBlue
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF73777D)
    val resultBg = if (isDark) Color(0xFF0F172A) else Color(0xFFF5F7FB)
    val valueColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val errorColor = if (isDark) Color(0xFFFCA5A5) else Color(0xFFB42318)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(cardBg)
                .border(1.dp, cardBorder, RoundedCornerShape(26.dp))
                .padding(18.dp)
                .testTag("route_passage_window"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = titleColor)
            Spacer(Modifier.width(10.dp))
            Text(
                "Abfahrtsfenster",
                modifier = Modifier.weight(1f),
                color = titleColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )
            if (uiState.isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                    color = titleColor,
                )
            }
        }

        when {
            uiState.isWorking -> {
                Text(
                    text =
                        if (uiState.isSearchingPassageWindow) {
                            "Passagefenster wird berechnet…"
                        } else {
                            "Route, Fahrzeit und Ankunft werden berechnet…"
                        },
                    modifier = Modifier.testTag("route_passage_loading"),
                    color = subtitleColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            uiState.error != null -> {
                Text(
                    text = uiState.error,
                    modifier = Modifier.testTag("route_passage_error"),
                    color = errorColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            !uiState.hasCalculatedResult -> {
                Text(
                    text =
                        if (uiState.hasCompleteRouteInput) {
                            "Noch nicht berechnet. Starte die Törnplanung mit der Schaltfläche unten."
                        } else {
                            "Wähle Start- und Zielhafen, um den Törn zu berechnen."
                        },
                    modifier = Modifier.testTag("route_passage_neutral"),
                    color = subtitleColor,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            uiState.passageWindows.isEmpty() -> {
                Text(
                    "Für den gewählten Abfahrtstag wurde kein sicheres Abfahrtsfenster gefunden.",
                    modifier = Modifier.testTag("route_passage_empty"),
                    color = subtitleColor,
                    fontWeight = FontWeight.SemiBold,
                )
                RouteDepthDetails(
                    uiState = uiState,
                    labelColor = subtitleColor,
                    valueColor = valueColor,
                )
            }

            else -> {
                val primaryWindow = uiState.passageWindow ?: uiState.passageWindows.first()
                val alternatives = uiState.passageWindows.filterNot { it == primaryWindow }
                val selectedDepartureDate = uiState.departure.toLocalDate()

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(resultBg)
                            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
                            .padding(14.dp)
                            .testTag("route_passage_result"),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "MÖGLICHE ABFAHRT",
                        color = subtitleColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        primaryWindow.formatRange(),
                        color = valueColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    primaryWindow.recommendedDeparture?.let { recommended ->
                        PlannerResultLine(
                            label = "Empfohlene Abfahrt",
                            value = recommended.formatTimeWithOptionalDate(selectedDepartureDate),
                            labelColor = subtitleColor,
                            valueColor = titleColor,
                        )
                    }
                    primaryWindow.anchoredHighWater?.let { highWater ->
                        PlannerResultLine(
                            label = "HW-Referenz",
                            value = highWater.formatTimeWithOptionalDate(selectedDepartureDate),
                            labelColor = subtitleColor,
                            valueColor = valueColor,
                        )
                    }

                    RouteDepthDetails(
                        uiState = uiState,
                        labelColor = subtitleColor,
                        valueColor = valueColor,
                        fallbackBottleneck = primaryWindow.bottleneckName,
                    )

                    PlannerResultLine(
                        label = "Datenqualität",
                        value = primaryWindow.waterLevelQuality.toDisplayText(),
                        labelColor = subtitleColor,
                        valueColor = valueColor,
                    )
                    primaryWindow.waterLevelDetail?.let { detail ->
                        Text(
                            detail,
                            color = subtitleColor,
                            fontSize = 12.sp,
                        )
                    }
                }

                if (alternatives.isNotEmpty()) {
                    Text(
                        "Alternative Zeitfenster (${alternatives.size})",
                        color = titleColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                    alternatives.forEach { window ->
                        Text(
                            text = window.formatRange(),
                            color = subtitleColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteDepthDetails(
    uiState: RoutePlanningUiState,
    labelColor: Color,
    valueColor: Color,
    fallbackBottleneck: String? = null,
) {
    val metrics = uiState.routeMetrics ?: return
    metrics.worstUnderKeelClearanceMeters?.let { clearance ->
        PlannerResultLine(
            label = "Geringste WuK",
            value = String.format(Locale.GERMANY, "%.2f m", clearance),
            labelColor = labelColor,
            valueColor = valueColor,
        )
    }
    (metrics.worstClearanceName ?: fallbackBottleneck)?.let { bottleneck ->
        PlannerResultLine(
            label = "Engstelle",
            value = bottleneck,
            labelColor = labelColor,
            valueColor = valueColor,
        )
    }
}

@Composable
private fun PlannerResultLine(
    label: String,
    value: String,
    labelColor: Color,
    valueColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            color = labelColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = valueColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}

private fun WaterLevelQuality.toDisplayText(): String =
    when (this) {
        WaterLevelQuality.LOCAL_OFFICIAL -> "Lokale amtliche Daten"
        WaterLevelQuality.MANUAL -> "Manuelle Korrektur"
        WaterLevelQuality.CONFIRMED_COMPARISON -> "Bestätigter Vergleichspegel"
        WaterLevelQuality.STALE -> "Veraltete Prognose"
        WaterLevelQuality.OUTSIDE_FORECAST_HORIZON -> "Astronomische Gezeitendaten"
        WaterLevelQuality.UNAVAILABLE -> "Nicht verfügbar"
    }

internal fun PassageWindow.formatRange(): String =
    "${start.format(passageWindowRangeFormatter)} – ${end.format(passageWindowRangeFormatter)}"

private fun ZonedDateTime.formatTimeWithOptionalDate(
    referenceDate: LocalDate,
    includeSuffix: Boolean = true,
): String {
    val formatted =
        if (toLocalDate() == referenceDate) {
            format(routeTimeFormatter)
        } else {
            "${format(routeDateFormatter)}, ${format(routeTimeFormatter)}"
        }
    return if (includeSuffix) "$formatted Uhr" else formatted
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
                        .navigationBarsPadding()
                        .imePadding()
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
