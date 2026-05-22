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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.model.TideEvent
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TideGraphScreen(viewModel: TideViewModel) {
    val tideEvents by viewModel.currentTideEvents.collectAsState()
    val allStations by viewModel.allStations.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val tideLoading by viewModel.tideLoading.collectAsState()

    var stationDropdownExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (allStations.isEmpty()) {
            viewModel.loadData()
        }
    }

    val todayStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    val todayEvents = tideEvents.filter { it.timestamp.startsWith(todayStr) }

    val now = LocalDateTime.now()
    val windowStart = now.minusHours(18)
    val windowEnd = now.plusHours(18)
    val windowEvents = remember(tideEvents, now.hour) {
        tideEvents.mapNotNull { event ->
            try {
                val cleanTs = event.timestamp
                    .replace("T", " ")
                    .replace(Regex("Z$"), "")
                    .replace(Regex("\\+\\d{2}:\\d{2}$"), "")
                    .replace(Regex("\\+\\d{2}$"), "")
                    .trim()
                val dt = try {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (_: Exception) {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                event to dt
            } catch (_: Exception) { null }
        }.filter { (_, dt) ->
            !dt.isBefore(windowStart) && !dt.isAfter(windowEnd)
        }
    }

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

        Text(
            "TIDENKURVE",
            style = MaterialTheme.typography.labelMedium,
            color = NauticalTextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

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

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .padding(bottom = 16.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            if (windowEvents.size < 2) {
                Box(modifier = Modifier.padding(16.dp)) {
                    TideCurveCanvas(emptyList(), windowStart, windowEnd, now)
                    Text(
                        if (tideLoading) "Lade Daten..." else "Keine Daten – bitte Station auswählen",
                        color = NauticalTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                Box(modifier = Modifier.padding(top = 12.dp, bottom = 8.dp, start = 32.dp, end = 12.dp)) {
                    TideCurveCanvas(windowEvents.map { it.first }, windowStart, windowEnd, now)
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
private fun TideCurveCanvas(
    events: List<TideEvent>,
    windowStart: LocalDateTime,
    windowEnd: LocalDateTime,
    now: LocalDateTime
) {
    val tickColor = NauticalTextSecondary
    val tideColor = NauticalTideBlue
    val gridColor = NauticalGridLine
    val nowColor = NauticalNowLine
    val gradientColor = NauticalTideBlue.copy(alpha = 0.18f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelTextSize = with(density) { 10.sp.toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val plotPaddingTop = 8f
        val plotPaddingBottom = 24f  
        val plotHeight = height - plotPaddingTop - plotPaddingBottom
        val plotBottomY = height - plotPaddingBottom

        val windowMinutes = java.time.Duration.between(windowStart, windowEnd).toMinutes().toDouble()
        if (windowMinutes <= 0) return@Canvas

        val pts = events.mapNotNull { ev ->
            try {
                val cleanTs = ev.timestamp
                    .replace("T", " ")
                    .replace(Regex("Z$"), "")
                    .replace(Regex("\\+\\d{2}:\\d{2}$"), "")
                    .replace(Regex("\\+\\d{2}$"), "")
                    .trim()
                val dt = try {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (_: Exception) {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                val minutesFromStart = java.time.Duration.between(windowStart, dt).toMinutes().toDouble()
                Triple(minutesFromStart, ev.value ?: 0.0, ev.type)
            } catch (_: Exception) { null }
        }.sortedBy { it.first }

        val values = pts.map { it.second }
        val maxVal = (values.maxOrNull() ?: 4.0)
        val minVal = (values.minOrNull() ?: 0.0)
        val pad = ((maxVal - minVal) * 0.15).coerceAtLeast(0.3)
        val yMax = maxVal + pad
        val yMin = minVal - pad
        val yRange = (yMax - yMin).coerceAtLeast(0.5)

        fun yForLevel(level: Double): Float =
            plotBottomY - ((level - yMin) / yRange * plotHeight).toFloat()

        fun xForMinute(min: Double): Float =
            (min / windowMinutes * width).toFloat()

        for (frac in listOf(0.25f, 0.5f, 0.75f)) {
            val y = plotPaddingTop + plotHeight * frac
            drawLine(
                color = gridColor.copy(alpha = 0.25f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
        }

        val hourPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(255, 122, 138, 158)  
            textSize = labelTextSize
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        var hourTick = windowStart.withMinute(0).withSecond(0).withNano(0)

        hourTick = hourTick.plusHours(((6 - hourTick.hour % 6) % 6).toLong())
        while (!hourTick.isAfter(windowEnd)) {
            val minutes = java.time.Duration.between(windowStart, hourTick).toMinutes().toDouble()
            val x = xForMinute(minutes)
            drawLine(
                color = gridColor.copy(alpha = 0.2f),
                start = Offset(x, plotPaddingTop),
                end = Offset(x, plotBottomY),
                strokeWidth = 1f
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%02d:00".format(hourTick.hour),
                x,
                height - 6f,
                hourPaint
            )
            hourTick = hourTick.plusHours(6)
        }

        if (pts.size >= 2) {
            val path = Path()
            val fillPath = Path()
            var started = false
            val step = 2  
            for (xPx in 0..width.toInt() step step) {
                val minute = xPx.toDouble() / width * windowMinutes
                val before = pts.lastOrNull { it.first <= minute }
                val after = pts.firstOrNull { it.first > minute }
                val level: Double = when {
                    before != null && after != null -> {
                        val span = after.first - before.first
                        val progress = if (span > 0.0) ((minute - before.first) / span) else 0.0
                        val cosInterp = (1 - kotlin.math.cos(progress * Math.PI)) / 2.0
                        before.second + (after.second - before.second) * cosInterp
                    }
                    before != null -> before.second
                    after != null -> after.second
                    else -> (yMax + yMin) / 2.0
                }
                val yPos = yForLevel(level)
                if (!started) {
                    path.moveTo(xPx.toFloat(), yPos)
                    fillPath.moveTo(xPx.toFloat(), plotBottomY)
                    fillPath.lineTo(xPx.toFloat(), yPos)
                    started = true
                } else {
                    path.lineTo(xPx.toFloat(), yPos)
                    fillPath.lineTo(xPx.toFloat(), yPos)
                }
            }
            fillPath.lineTo(width, plotBottomY)
            fillPath.close()

            drawPath(fillPath, color = gradientColor)
            drawPath(
                path = path,
                color = tideColor,
                style = Stroke(width = 4f, cap = StrokeCap.Round)
            )

            for ((minutes, level, type) in pts) {
                if (minutes < 0 || minutes > windowMinutes) continue
                val cx = xForMinute(minutes)
                val cy = yForLevel(level)
                drawCircle(
                    color = if (type == "HW") NauticalPrimary else NauticalSecondary,
                    radius = 5f,
                    center = Offset(cx, cy)
                )
                drawCircle(
                    color = androidx.compose.ui.graphics.Color.White,
                    radius = 2f,
                    center = Offset(cx, cy)
                )
            }
        }

        val nowMinutes = java.time.Duration.between(windowStart, now).toMinutes().toDouble()
        if (nowMinutes in 0.0..windowMinutes) {
            val nowX = xForMinute(nowMinutes)
            drawLine(
                color = nowColor,
                start = Offset(nowX, plotPaddingTop),
                end = Offset(nowX, plotBottomY),
                strokeWidth = 2f
            )
            val nowPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(255, 255, 82, 82)
                textSize = labelTextSize
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
            drawContext.canvas.nativeCanvas.drawText("JETZT", nowX, plotPaddingTop - 1f, nowPaint)
        }
    }
}

@Composable
fun TideEventRow(event: TideEvent) {
    val isHigh = event.type == "HW"

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
