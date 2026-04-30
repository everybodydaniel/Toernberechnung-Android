package com.example.trnberechnung.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.TideEvent
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TideGraphScreen(viewModel: TideViewModel) {
    val tideEvents by viewModel.currentTideEvents.collectAsState()
    val allStations by viewModel.allStations.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val tideLoading by viewModel.tideLoading.collectAsState()

    var stationDropdownExpanded by remember { mutableStateOf(false) }

    // BSH-Daten laden wenn noch nicht geschehen
    LaunchedEffect(Unit) {
        if (allStations.isEmpty()) {
            viewModel.loadData()
        }
    }

    // Events für heute filtern
    val todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val todayEvents = tideEvents.filter { it.timestamp.startsWith(todayStr) }

    // Fallback: wenn keine Station ausgewählt, nimm die erste
    LaunchedEffect(allStations) {
        if (selectedStation == null && allStations.isNotEmpty()) {
            viewModel.selectStation(allStations.first())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NauticalBackground)
            .padding(16.dp)
    ) {
        // Header
        Text(
            "TIDENKURVE",
            style = MaterialTheme.typography.labelMedium,
            color = NauticalTextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Station-Dropdown
        ExposedDropdownMenuBox(
            expanded = stationDropdownExpanded,
            onExpandedChange = { stationDropdownExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            OutlinedTextField(
                value = selectedStation?.gaugeLabel ?: selectedStation?.area ?: "Station wählen…",
                onValueChange = {},
                readOnly = true,
                label = { Text("Pegelstation", color = NauticalTextSecondary) },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = stationDropdownExpanded)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NauticalPrimary,
                    unfocusedBorderColor = NauticalDivider,
                    focusedLabelColor = NauticalPrimary,
                    unfocusedLabelColor = NauticalTextSecondary,
                    cursorColor = NauticalPrimary,
                    focusedTextColor = NauticalTextPrimary,
                    unfocusedTextColor = NauticalTextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(
                expanded = stationDropdownExpanded,
                onDismissRequest = { stationDropdownExpanded = false },
                containerColor = NauticalSurface
            ) {
                allStations.forEach { station ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                station.gaugeLabel ?: station.area,
                                color = NauticalTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = if (station == selectedStation) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            viewModel.selectStation(station)
                            stationDropdownExpanded = false
                        }
                    )
                }
            }
        }

        // Tidenkurve Canvas
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(bottom = 16.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (todayEvents.isEmpty()) {
                // Fallback: Sinuskurve wie vorher
                Box(modifier = Modifier.padding(16.dp)) {
                    TideCurveCanvas(emptyList())
                    Text(
                        if (tideLoading) "Lade Daten..." else "Keine Daten – bitte Station auswählen",
                        color = NauticalTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                Box(modifier = Modifier.padding(16.dp)) {
                    TideCurveCanvas(todayEvents)
                    // JETZT-Label
                    Text(
                        "JETZT",
                        color = NauticalNowLine,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        Text(
            "HEUTE",
            style = MaterialTheme.typography.labelMedium,
            color = NauticalTextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Event-Liste
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (todayEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (tideLoading) "Lade Gezeitendaten..." else "Keine Gezeitendaten für heute verfügbar",
                        color = NauticalTextSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(todayEvents) { event ->
                        TideEventRow(event)
                        HorizontalDivider(
                            color = NauticalDivider.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TideCurveCanvas(events: List<TideEvent>) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val middleY = height / 2f

        // Zeichne Achsen
        drawLine(
            color = NauticalGridLine,
            start = Offset(0f, middleY),
            end = Offset(width, middleY),
            strokeWidth = 2f
        )

        // Subtile Hilfslinien
        drawLine(
            color = NauticalGridLine.copy(alpha = 0.3f),
            start = Offset(0f, height * 0.25f),
            end = Offset(width, height * 0.25f),
            strokeWidth = 1f
        )
        drawLine(
            color = NauticalGridLine.copy(alpha = 0.3f),
            start = Offset(0f, height * 0.75f),
            end = Offset(width, height * 0.75f),
            strokeWidth = 1f
        )

        if (events.size >= 2) {
            // Echte Tidenkurve basierend auf HW/NW-Werten
            val values = events.mapNotNull { it.value }
            val maxVal = values.maxOrNull() ?: 4.0
            val minVal = values.minOrNull() ?: 0.0
            val range = (maxVal - minVal).coerceAtLeast(0.5)

            // Parse timestamps zu Minuten seit Mitternacht
            val eventsWithMinutes = events.mapNotNull { event ->
                try {
                    val cleanTs = event.timestamp
                        .replace(Regex("\\+\\d{2}:\\d{2}$"), "")
                        .replace(Regex("\\+\\d{2}$"), "")
                        .trim()
                    val dt = java.time.LocalDateTime.parse(
                        cleanTs,
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    )
                    val minutesSinceMidnight = dt.hour * 60 + dt.minute
                    Triple(minutesSinceMidnight, event.value ?: 0.0, event.type)
                } catch (_: Exception) { null }
            }.sortedBy { it.first }

            if (eventsWithMinutes.isNotEmpty()) {
                val totalMinutes = 24 * 60
                val path = Path()
                var started = false

                // Interpoliere Sinuskurve zwischen den bekannten Punkten
                for (x in 0..width.toInt()) {
                    val minute = (x.toFloat() / width * totalMinutes).toInt()
                    
                    // Finde umgebende Events
                    val before = eventsWithMinutes.lastOrNull { it.first <= minute }
                    val after = eventsWithMinutes.firstOrNull { it.first > minute }

                    val waterLevel = when {
                        before == null && after != null -> after.second
                        after == null && before != null -> before.second
                        before != null && after != null -> {
                            val progress = (minute - before.first).toFloat() / 
                                          (after.first - before.first).toFloat()
                            // Sinusförmige Interpolation (wie Gezeiten natürlicherweise verlaufen)
                            val cosInterp = (1 - kotlin.math.cos(progress * Math.PI)) / 2.0
                            before.second + (after.second - before.second) * cosInterp
                        }
                        else -> (maxVal + minVal) / 2.0
                    }

                    val normalizedY = ((waterLevel - minVal) / range).toFloat().coerceIn(0f, 1f)
                    val yPos = height - (normalizedY * height * 0.8f + height * 0.1f)

                    if (!started) {
                        path.moveTo(x.toFloat(), yPos)
                        started = true
                    } else {
                        path.lineTo(x.toFloat(), yPos)
                    }
                }

                drawPath(
                    path = path,
                    color = NauticalTideBlue,
                    style = Stroke(width = 4f, cap = StrokeCap.Round)
                )
            }
        } else {
            // Fallback: generische Sinuskurve
            val path = Path()
            val waveLength = width / 2f
            val amplitude = height / 3f

            for (x in 0..width.toInt()) {
                val xFloat = x.toFloat()
                val yFloat = middleY - (sin((xFloat / waveLength) * 2 * Math.PI) * amplitude).toFloat()

                if (x == 0) path.moveTo(xFloat, yFloat)
                else path.lineTo(xFloat, yFloat)
            }

            drawPath(
                path = path,
                color = NauticalTideBlue,
                style = Stroke(width = 6f, cap = StrokeCap.Round)
            )
        }

        // JETZT-Linie (dynamisch positioniert)
        val now = java.time.LocalDateTime.now()
        val minutesSinceMidnight = now.hour * 60 + now.minute
        val nowX = (minutesSinceMidnight.toFloat() / (24 * 60)) * width
        drawLine(
            color = NauticalNowLine,
            start = Offset(nowX, 0f),
            end = Offset(nowX, height),
            strokeWidth = 3f
        )
    }
}

@Composable
fun TideEventRow(event: TideEvent) {
    val isHigh = event.type == "HW"
    
    // Zeit aus dem Timestamp extrahieren
    val timeStr = try {
        val cleanTs = event.timestamp
            .replace(Regex("\\+\\d{2}:\\d{2}$"), "")
            .replace(Regex("\\+\\d{2}$"), "")
            .trim()
        val dt = java.time.LocalDateTime.parse(
            cleanTs,
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        )
        dt.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
    } catch (_: Exception) {
        event.timestamp.substringAfter(" ").take(5)
    }

    val heightStr = event.value?.let { "%.2f m".format(it) } ?: "–"
    val typeStr = if (isHigh) "Hochwasser" else "Niedrigwasser"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (isHigh) "▲" else "▼",
            color = if (isHigh) NauticalPrimary else NauticalSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            timeStr,
            fontWeight = FontWeight.Bold,
            color = NauticalTextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.width(60.dp)
        )
        Text(
            typeStr,
            color = NauticalTextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            heightStr,
            fontWeight = FontWeight.Bold,
            color = if (isHigh) NauticalPrimary else NauticalTextSecondary,
            fontSize = 16.sp
        )
    }
}
