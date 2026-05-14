package com.example.trnberechnung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.trnberechnung.model.BoatProfileRepository
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.RoutePlanningViewModel
import com.example.trnberechnung.viewmodel.TideViewModel
import org.maplibre.android.geometry.LatLng
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import android.app.TimePickerDialog
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlanningScreen(viewModel: TideViewModel, routeViewModel: RoutePlanningViewModel) {
    val context = LocalContext.current
    val stations by viewModel.data.collectAsState()
    val allStations by viewModel.allStations.collectAsState()
    val weather by viewModel.currentWeather.collectAsState()
    val tideEvents by viewModel.currentTideEvents.collectAsState()
    val routeUiState by routeViewModel.uiState.collectAsState()
    
    val boatProfile = remember { BoatProfileRepository(context) }
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // BSH-Stationen laden
    LaunchedEffect(Unit) {
        if (allStations.isEmpty()) viewModel.loadData()
        if (weather == null) viewModel.loadWeatherForLocation()
    }

    // Hafen-Auswahl
    var selectedStartStation by remember { mutableStateOf<TideStationData?>(null) }
    var selectedEndStation by remember { mutableStateOf<TideStationData?>(null) }
    var showStartDropdown by remember { mutableStateOf(false) }
    var showEndDropdown by remember { mutableStateOf(false) }

    // Karten-Auswahlmodus: "start" oder "end"
    var mapSelectionMode by remember { mutableStateOf("start") }

    // Route-Status
    var startLocation by remember { mutableStateOf("") }
    var destinationLocation by remember { mutableStateOf("") }

    // Auto-recalculate route when tide events or departure time changes
    LaunchedEffect(tideEvents, routeUiState.departureTime) {
        if (selectedStartStation != null && selectedEndStation != null) {
            val startPt = LatLng(selectedStartStation!!.latitude, selectedStartStation!!.longitude)
            val endPt = LatLng(selectedEndStation!!.latitude, selectedEndStation!!.longitude)
            routeViewModel.loadAndCalculate(
                tideEvents = tideEvents,
                departureTime = routeUiState.departureTime,
                customStart = startPt,
                customEnd = endPt
            )
        }
    }

    // Ergebnis – automatisch aus Routensegmenten + Wetter
    var calculationResult by remember { mutableStateOf<CalculationResult?>(null) }
    
    // UI states
    var showResultDialog by remember { mutableStateOf(false) }
    var resultDialogIsGo by remember { mutableStateOf(false) }
    var resultDialogMessage by remember { mutableStateOf("") }

    // Automatische GO/NO-GO Berechnung aus Routensegmenten + Wetter
    LaunchedEffect(routeUiState.routeSegments, weather) {
        val segments = routeUiState.routeSegments
        if (segments.isEmpty()) { calculationResult = null; return@LaunchedEffect }
        
        val hasNoGo = segments.any { it.type == com.example.trnberechnung.model.SegmentType.NO_GO }
        val hasCritical = segments.any { it.type == com.example.trnberechnung.model.SegmentType.CRITICAL }
        val minDepth = segments.minOfOrNull { it.minDepth } ?: 0.0
        val windSpeed = weather?.windSpeed ?: 0.0
        val windBft = kmhToBft(windSpeed)
        
        var isGo = !hasNoGo
        var errorMessage: String? = when {
            hasNoGo -> "❌ Route enthält nicht befahrbare Abschnitte (min. Tiefe: ${"%.1f".format(minDepth)}m)"
            hasCritical -> "⚠ Kritische Abschnitte auf der Route (min. Tiefe: ${"%.1f".format(minDepth)}m)"
            else -> null
        }
        
        var windWarning: String? = null
        if (windBft >= 8) { isGo = false; windWarning = "⛈ STURMWARNUNG Bft $windBft – Törn nicht durchführbar!" }
        else if (windBft >= 6) { windWarning = "⚠ Wind Bft $windBft – erhöhte Vorsicht!" }
        
        var visibilityWarning: String? = null
        weather?.visibility?.let { vis ->
            if (vis < 1000) { isGo = false; visibilityWarning = "🌫 Sicht unter 1 km – Navigation gefährlich!" }
            else if (vis < 2000) { visibilityWarning = "🌫 Eingeschränkte Sicht – erhöhte Vorsicht!" }
        }
        
        var gustWarning: String? = null
        weather?.windGustSpeed?.let { gusts ->
            if (gusts >= 90.0) { isGo = false; gustWarning = "💨 Extreme Böen – Törn nicht durchführbar!" }
            else if (gusts >= 60.0) { gustWarning = "💨 Starke Böen – erhöhte Vorsicht!" }
        }
        
        if (weather?.condition?.lowercase() == "thunderstorm") {
            isGo = false; windWarning = (windWarning ?: "") + "\n⛈ Gewitter – Törn nicht durchführbar!"
        }
        
        calculationResult = CalculationResult(
            waterLevel = 0.0, ukc = minDepth,
            boatDraft = boatProfile.draft.toDouble(), safetyMargin = boatProfile.safetyMargin.toDouble(),
            isGo = isGo, windWarning = windWarning, visibilityWarning = visibilityWarning,
            gustWarning = gustWarning, errorMessage = errorMessage
        )
    }


    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(NauticalBackground)
                .verticalScroll(scrollState)
        ) {
            // NestedScrollConnection that eats ALL scroll, so the parent
            // Column's verticalScroll never steals from the map
            val consumeAllScrollConnection = remember {
                object : NestedScrollConnection {
                    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                        return available // Consume everything
                    }
                }
            }
            // ── Karten-Auswahlmodus ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = mapSelectionMode == "start",
                    onClick = { mapSelectionMode = "start" },
                    label = { Text("🟢 Starthafen", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF1B5E20).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFF1B5E20)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        selectedBorderColor = Color(0xFF1B5E20),
                        enabled = true,
                        selected = mapSelectionMode == "start"
                    ),
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = mapSelectionMode == "end",
                    onClick = { mapSelectionMode = "end" },
                    label = { Text("🔴 Zielhafen", fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFB71C1C).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFFB71C1C)
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        selectedBorderColor = Color(0xFFB71C1C),
                        enabled = true,
                        selected = mapSelectionMode == "end"
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            // ── Karte ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp) // Slightly taller for reset button
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 8.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp))
                    .nestedScroll(consumeAllScrollConnection)
            ) {
                MapScreen(
                    stations = stations,
                    routePoints = routeUiState.route,
                    routeSegments = routeUiState.routeSegments,
                    depthPoints = routeUiState.depthPoints,
                    harbors = allStations,
                    selectedStartHarbor = selectedStartStation,
                    selectedEndHarbor = selectedEndStation,
                    onHarborClick = { harbor ->
                        // Schutzschicht: Jeder Hafen-Klick wird in try-catch verpackt, damit
                        // ein Fehler (z.B. async Tide-API-Fehler) die App nicht crasht.
                        try {
                            if (mapSelectionMode == "start") {
                                if (selectedEndStation?.area == harbor.area) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Start- und Zielhafen dürfen nicht identisch sein!")
                                    }
                                } else {
                                    selectedStartStation = harbor
                                    startLocation = harbor.gaugeLabel ?: harbor.area
                                    viewModel.selectStation(harbor)
                                    mapSelectionMode = "end"
                                    selectedEndStation?.let { end ->
                                        val startPt = LatLng(harbor.latitude, harbor.longitude)
                                        val endPt = LatLng(end.latitude, end.longitude)
                                        routeViewModel.loadAndCalculate(
                                            tideEvents = tideEvents,
                                            departureTime = routeUiState.departureTime,
                                            customStart = startPt,
                                            customEnd = endPt
                                        )
                                    }
                                }
                            } else {
                                if (selectedStartStation?.area == harbor.area) {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Start- und Zielhafen dürfen nicht identisch sein!")
                                    }
                                } else {
                                    selectedEndStation = harbor
                                    destinationLocation = harbor.gaugeLabel ?: harbor.area
                                    mapSelectionMode = "start"
                                    selectedStartStation?.let { start ->
                                        val startPt = LatLng(start.latitude, start.longitude)
                                        val endPt = LatLng(harbor.latitude, harbor.longitude)
                                        routeViewModel.loadAndCalculate(
                                            tideEvents = tideEvents,
                                            departureTime = routeUiState.departureTime,
                                            customStart = startPt,
                                            customEnd = endPt
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("RoutePlanning", "Harbor click failed", e)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Hafen-Auswahl fehlgeschlagen: ${e.message ?: "unbekannt"}")
                            }
                        }
                    },
                    onMapClick = { /* Deaktiviert – Häfen werden über Marker/Dropdown ausgewählt */ },
                    onStationSelected = { station ->
                        try { viewModel.selectStation(station) }
                        catch (e: Exception) { android.util.Log.e("RoutePlanning", "selectStation failed", e) }
                    }
                )

                // Reset Overlay
                if (routeUiState.route.isNotEmpty()) {
                    Button(
                        onClick = { 
                            startLocation = ""
                            destinationLocation = ""
                            selectedStartStation = null
                            selectedEndStation = null
                            mapSelectionMode = "start"
                            routeViewModel.loadAndCalculate()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NauticalNoGo.copy(alpha = 0.9f),
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                            .height(32.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("ROUTE ZURÜCKSETZEN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // ── Hafen-Auswahl & Simulation ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                
                // --- Simulations-Steuerung (Zeit-Wähler) ---
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
                    colors = CardDefaults.cardColors(containerColor = NauticalSurfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "SIMULATIONS-ZEIT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = NauticalTextSecondary
                            )
                            Text(
                                routeUiState.departureTime.format(DateTimeFormatter.ofPattern("EEE, dd. MMM · HH:mm", Locale.GERMANY)),
                                style = MaterialTheme.typography.titleMedium,
                                color = NauticalPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                val current = routeUiState.departureTime
                                // Zuerst Datum, dann (sequenziell) Uhrzeit wählen
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, day ->
                                        TimePickerDialog(
                                            context,
                                            { _, hour, minute ->
                                                val newTime = current
                                                    .withYear(year)
                                                    .withMonth(month + 1)  // DatePicker: 0-basiert
                                                    .withDayOfMonth(day)
                                                    .withHour(hour)
                                                    .withMinute(minute)
                                                routeViewModel.updateDepartureTime(newTime, tideEvents)
                                            },
                                            current.hour,
                                            current.minute,
                                            true
                                        ).show()
                                    },
                                    current.year,
                                    current.monthValue - 1,
                                    current.dayOfMonth
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NauticalPrimary),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ÄNDERN", fontSize = 12.sp)
                        }
                    }
                }

                Text(
                    "HAFEN-AUSWAHL",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NauticalTextSecondary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Hafen-Dropdowns
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = NauticalSurface),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Starthafen
                    ExposedDropdownMenuBox(
                        expanded = showStartDropdown,
                        onExpandedChange = { showStartDropdown = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedStartStation?.gaugeLabel ?: selectedStartStation?.area ?: "Starthafen wählen",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("🟢 Starthafen", color = NauticalTextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showStartDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NauticalTextPrimary,
                                unfocusedTextColor = NauticalTextPrimary,
                                focusedBorderColor = NauticalPrimary,
                                unfocusedBorderColor = NauticalDivider,
                                focusedLabelColor = NauticalPrimary,
                                unfocusedLabelColor = NauticalTextSecondary,
                                cursorColor = NauticalPrimary,
                                focusedContainerColor = NauticalSurface,
                                unfocusedContainerColor = NauticalSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showStartDropdown,
                            onDismissRequest = { showStartDropdown = false },
                            containerColor = NauticalSurface
                        ) {
                            allStations.sortedBy { it.gaugeLabel ?: it.area }.forEach { station ->
                                val isSameAsEnd = selectedEndStation?.area == station.area
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (station.gaugeLabel ?: station.area) + if (isSameAsEnd) " (= Ziel)" else "",
                                            color = if (isSameAsEnd) NauticalTextSecondary.copy(alpha = 0.5f) else NauticalTextPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        if (isSameAsEnd) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Start- und Zielhafen dürfen nicht identisch sein!")
                                            }
                                        } else {
                                            selectedStartStation = station
                                            startLocation = station.gaugeLabel ?: station.area
                                            viewModel.selectStation(station)
                                            showStartDropdown = false
                                            selectedEndStation?.let { end ->
                                                val startPt = LatLng(station.latitude, station.longitude)
                                                val endPt = LatLng(end.latitude, end.longitude)
                                                routeViewModel.loadAndCalculate(
                                                    tideEvents = tideEvents,
                                                    departureTime = routeUiState.departureTime,
                                                    customStart = startPt,
                                                    customEnd = endPt
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isSameAsEnd
                                )
                            }
                        }
                    }
                    // Zielhafen
                    ExposedDropdownMenuBox(
                        expanded = showEndDropdown,
                        onExpandedChange = { showEndDropdown = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedEndStation?.gaugeLabel ?: selectedEndStation?.area ?: "Zielhafen wählen",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("🔴 Zielhafen", color = NauticalTextSecondary) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showEndDropdown) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = NauticalTextPrimary,
                                unfocusedTextColor = NauticalTextPrimary,
                                focusedBorderColor = NauticalPrimary,
                                unfocusedBorderColor = NauticalDivider,
                                focusedLabelColor = NauticalPrimary,
                                unfocusedLabelColor = NauticalTextSecondary,
                                cursorColor = NauticalPrimary,
                                focusedContainerColor = NauticalSurface,
                                unfocusedContainerColor = NauticalSurface
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = showEndDropdown,
                            onDismissRequest = { showEndDropdown = false },
                            containerColor = NauticalSurface
                        ) {
                            allStations.sortedBy { it.gaugeLabel ?: it.area }.forEach { station ->
                                val isSameAsStart = selectedStartStation?.area == station.area
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            (station.gaugeLabel ?: station.area) + if (isSameAsStart) " (= Start)" else "",
                                            color = if (isSameAsStart) NauticalTextSecondary.copy(alpha = 0.5f) else NauticalTextPrimary,
                                            fontSize = 13.sp
                                        )
                                    },
                                    onClick = {
                                        if (isSameAsStart) {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Start- und Zielhafen dürfen nicht identisch sein!")
                                            }
                                        } else {
                                            selectedEndStation = station
                                            destinationLocation = station.gaugeLabel ?: station.area
                                            showEndDropdown = false
                                            selectedStartStation?.let { start ->
                                                val startPt = LatLng(start.latitude, start.longitude)
                                                val endPt = LatLng(station.latitude, station.longitude)
                                                routeViewModel.loadAndCalculate(
                                                    tideEvents = tideEvents,
                                                    departureTime = routeUiState.departureTime,
                                                    customStart = startPt,
                                                    customEnd = endPt
                                                )
                                            }
                                        }
                                    },
                                    enabled = !isSameAsStart
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "PASSAGE-FENSTER (WATT)",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = NauticalTextSecondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            // Warnung oder Go-Box
            calculationResult?.let { res ->
                val boxBg = if (res.isGo) NauticalGoBg else NauticalNoGoBg
                val boxTx = if (res.isGo) NauticalGo else NauticalNoGo
                val icon = if (res.isGo) Icons.Default.CheckCircle else Icons.Default.Clear

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(boxBg)
                        .border(1.dp, boxTx.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, contentDescription = null, tint = boxTx, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (res.isGo) "PASSAGE FREIGEGEBEN" else "PASSAGE GESPERRT",
                        fontWeight = FontWeight.Bold,
                        color = boxTx,
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                if (!res.isGo) {
                    Spacer(modifier = Modifier.height(8.dp))
                    val allWarnings = listOfNotNull(
                        res.errorMessage,
                        res.windWarning,
                        res.visibilityWarning,
                        res.gustWarning,
                        res.timeRangeWarning
                    ).joinToString("\n").ifEmpty {
                        "❌ GEFAHR: Aufsetzen möglich! (Mangelnde UKC / Sturm)"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NauticalNoGoBg)
                            .border(1.dp, NauticalNoGo.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            allWarnings,
                            fontWeight = FontWeight.Bold,
                            color = NauticalNoGo,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                // Gezeitenfenster-Warnung (auch bei GO anzeigen!)
                if (res.isGo && res.timeRangeWarning != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E2A0A))
                            .border(1.dp, NauticalAccentWarm.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            res.timeRangeWarning,
                            fontWeight = FontWeight.Bold,
                            color = NauticalAccentWarm,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                        // 3 Info Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val distanceNm = routeUiState.routeDistanceNm
                    val speed = boatProfile.speed.toDouble()
                    val travelTimeHrs = if (speed > 0) distanceNm / speed else 0.0
                    
                    val timeStr = if (travelTimeHrs > 0) {
                        val h = travelTimeHrs.toInt()
                        val m = ((travelTimeHrs - h) * 60).roundToInt()
                        "${h}h ${m}m"
                    } else "-"
                    
                    val arrivalTime = routeUiState.departureTime.plusMinutes((travelTimeHrs * 60).toLong())
                    
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        subtitle = "REISEZEIT",
                        value = timeStr,
                        desc = "Dauer"
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        subtitle = "ANKUNFT",
                        value = arrivalTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        desc = "UHR"
                    )
                    InfoCard(
                        modifier = Modifier.weight(1f),
                        subtitle = "DISTANZ",
                        value = "%.1f nm".format(distanceNm),
                        desc = "NM"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Button
            Button(
                onClick = {
                    val res = calculationResult
                    val currentStart = startLocation.ifEmpty { "Unbekannter Start" }
                    val currentDest = destinationLocation.ifEmpty { "Unbekanntes Ziel" }
                    
                    val distanceNm = routeUiState.routeDistanceNm
                    val speed = boatProfile.speed.toDouble()
                    val travelTimeHrs = if (speed > 0) distanceNm / speed else 0.0
                    
                    val distStr = "%.1f nm".format(distanceNm)
                    val durStr = "${travelTimeHrs.toInt()}h ${((travelTimeHrs - travelTimeHrs.toInt()) * 60).roundToInt()}m"
                    
                    // Build structured details for logbook
                    val depTime = routeUiState.departureTime
                    val arrTime = depTime.plusMinutes((travelTimeHrs * 60).toLong())
                    val depFmt = depTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                    val arrFmt = arrTime.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))

                    // Weather info
                    val wx = weather
                    val weatherStr = if (wx != null) {
                        val temp = wx.temperature?.let { "%.0f°C".format(it) } ?: ""
                        val wind = wx.windSpeed?.let { "%.0f kn".format(it / 1.852) } ?: ""
                        val cond = translateCondition(wx.condition ?: "")
                        listOf(temp, wind, cond).filter { it.isNotBlank() }.joinToString(" · ")
                    } else ""

                    // Tide events
                    val tideStr = tideEvents.take(4).joinToString(" | ") { ev ->
                        val t = try {
                            val clean = ev.timestamp.replace(Regex("[TZ]"), " ").replace(Regex("\\+.*"), "").trim()
                            LocalDateTime.parse(clean, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        } catch (_: Exception) { ev.timestamp.takeLast(5) }
                        "${ev.type} $t ${ev.value?.let { "%.2fm".format(it) } ?: ""}"
                    }

                    // Crew
                    val crewList = viewModel.allCrew.value
                    val crewStr = if (crewList.isNotEmpty()) {
                        crewList.filter { it.isOnBoard }.joinToString(", ") { "${it.name} (${it.rank})" }
                            .ifEmpty { crewList.joinToString(", ") { "${it.name} (${it.rank})" } }
                    } else ""

                    if (res == null) {
                        val detailLines = listOf(
                            "abfahrt:$depFmt",
                            "ankunft:$arrFmt",
                            "wetter:$weatherStr",
                            "gezeiten:$tideStr",
                            "crew:$crewStr"
                        ).joinToString("|")
                        val entry = LogbookEntry(
                            date = LocalDate.now().toString(),
                            routeDesc = "$currentStart nach $currentDest",
                            distance = distStr,
                            duration = durStr,
                            status = "MANUELL",
                            details = detailLines
                        )
                        viewModel.saveLog(entry)
                        resultDialogIsGo = true
                        resultDialogMessage = "Route gespeichert: $currentStart ➔ $currentDest\nDistanz: $distStr"
                        showResultDialog = true
                    } else {
                        val statusStr = if (res.isGo) "GO" else "NO-GO"
                        val wtStr = "%.2f m".format(res.ukc)
                        val ukcStr = "%.2f m".format(res.ukc - res.boatDraft)
                        val fmwStr = "%.2f m".format(res.safetyMargin)
                        val detailLines = listOf(
                            "abfahrt:$depFmt",
                            "ankunft:$arrFmt",
                            "wt:$wtStr",
                            "ukc:$ukcStr",
                            "fmw:$fmwStr",
                            "wetter:$weatherStr",
                            "gezeiten:$tideStr",
                            "crew:$crewStr"
                        ).joinToString("|")
                        
                        val entry = LogbookEntry(
                            date = LocalDate.now().toString(),
                            routeDesc = "$currentStart nach $currentDest",
                            distance = distStr,
                            duration = durStr,
                            status = statusStr,
                            details = detailLines
                        )
                        viewModel.saveLog(entry)
                        
                        // Show result dialog
                        resultDialogIsGo = res.isGo
                        resultDialogMessage = if (res.isGo) {
                            "Die Passage ist freigegeben.\n\nRoute: $currentStart ➔ $currentDest\nDistanz: $distStr\nReisezeit: $durStr\n\nTörn wurde im Logbuch gespeichert."
                        } else {
                            val reason = listOfNotNull(res.errorMessage, res.windWarning, res.visibilityWarning, res.gustWarning)
                                .joinToString("\n").ifEmpty { "Unzureichende Wassertiefe oder Sturmwarnung" }
                            "Die Passage ist NICHT freigegeben!\n\n⚠ $reason\n\nRoute: $currentStart ➔ $currentDest\n\nTörn wurde als NO-GO im Logbuch gespeichert."
                        }
                        showResultDialog = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (calculationResult?.isGo == true) NauticalPrimary else NauticalNoGo
                ),
                enabled = true
            ) {
                Text(
                    "ROUTE SPEICHERN",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (calculationResult?.isGo == true) NauticalTextOnPrimary else Color.White
                )
            }
        }
        Spacer(modifier = Modifier.height(100.dp))
    }

    // ── Ergebnis-Dialog ──
    if (showResultDialog) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            containerColor = NauticalSurface,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Icon(
                    if (resultDialogIsGo) Icons.Default.CheckCircle else Icons.Default.Clear,
                    contentDescription = null,
                    tint = if (resultDialogIsGo) NauticalGo else NauticalNoGo,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    if (resultDialogIsGo) "PASSAGE FREIGEGEBEN" else "PASSAGE NICHT FREIGEGEBEN",
                    fontWeight = FontWeight.Bold,
                    color = if (resultDialogIsGo) NauticalGo else NauticalNoGo,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column {
                    // Status banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (resultDialogIsGo) NauticalGoBg else NauticalNoGoBg
                            )
                            .border(
                                1.dp,
                                if (resultDialogIsGo) NauticalGo.copy(alpha = 0.3f) else NauticalNoGo.copy(alpha = 0.3f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Text(
                            resultDialogMessage,
                            color = NauticalTextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showResultDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (resultDialogIsGo) NauticalGo else NauticalNoGo
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "VERSTANDEN",
                        fontWeight = FontWeight.Bold,
                        color = if (resultDialogIsGo) NauticalTextOnPrimary else Color.White
                    )
                }
            }
        )
    }
    
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
    )
}
}

@Composable
fun InfoCard(modifier: Modifier = Modifier, subtitle: String, value: String, desc: String) {
    Card(
        modifier = modifier
            .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(subtitle, color = NauticalTextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, color = NauticalTextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(desc, color = NauticalTextSecondary, fontSize = 9.sp)
        }
    }
}



// ── Hilfsfunktionen ──
private fun kmhToBft(kmh: Double): Int = when {
    kmh < 1 -> 0; kmh < 6 -> 1; kmh < 12 -> 2; kmh < 20 -> 3
    kmh < 29 -> 4; kmh < 39 -> 5; kmh < 50 -> 6; kmh < 62 -> 7
    kmh < 75 -> 8; kmh < 89 -> 9; kmh < 103 -> 10; kmh < 118 -> 11
    else -> 12
}

private fun dirToText(deg: Int): String = when ((deg + 22) / 45 % 8) {
    0 -> "N"; 1 -> "NO"; 2 -> "O"; 3 -> "SO"
    4 -> "S"; 5 -> "SW"; 6 -> "W"; 7 -> "NW"; else -> "-"
}

private fun translateCondition(condition: String): String = when (condition.lowercase()) {
    "dry" -> "Trocken"
    "fog" -> "Nebel"
    "rain" -> "Regen"
    "sleet" -> "Schneeregen"
    "snow" -> "Schnee"
    "hail" -> "Hagel"
    "thunderstorm" -> "Gewitter"
    "clear-day", "clear-night" -> "Klar"
    "partly-cloudy-day", "partly-cloudy-night" -> "Teilweise bewölkt"
    "cloudy" -> "Bedeckt"
    "wind" -> "Windig"
    else -> condition.replaceFirstChar { it.uppercase() }
}

fun calculateDistanceNM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
    val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    val distanceKm = r * c
    return distanceKm / 1.852
}

// ── Datenklasse ──
data class CalculationResult(
    val waterLevel: Double,
    val ukc: Double,
    val boatDraft: Double,
    val safetyMargin: Double,
    val isGo: Boolean,
    val windWarning: String? = null,
    val visibilityWarning: String? = null,
    val gustWarning: String? = null,
    val timeRangeWarning: String? = null,
    val errorMessage: String? = null
) {
    val logOutput: String
        get() {
            val warnings = listOfNotNull(windWarning, visibilityWarning, gustWarning, timeRangeWarning, errorMessage)
                .joinToString("\n") { "Warnung: $it" }
            return "Min. Tiefe: %.2fm, Tiefgang: %.2fm%s".format(
                ukc, boatDraft, if (warnings.isNotEmpty()) "\n$warnings" else ""
            )
        }
}
