package com.example.trnberechnung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.logic.DecisionLogic
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Wadden Sea harbour stations with DWD-verified coordinates
private val weatherStations = listOf(
    "Cuxhaven" to Pair(53.87, 8.70),
    "Norderney" to Pair(53.71, 7.15),
    "Wilhelmshaven" to Pair(53.52, 8.14),
    "Emden" to Pair(53.36, 7.21),
    "Borkum" to Pair(53.59, 6.66),
    "Helgoland" to Pair(54.18, 7.89),
    "Büsum" to Pair(54.12, 8.86),
    "Husum" to Pair(54.47, 9.05),
    "Norddeich" to Pair(53.63, 7.16),
    "Wangerooge" to Pair(53.79, 7.87),
    "Langeoog" to Pair(53.74, 7.50),
    "Spiekeroog" to Pair(53.77, 7.69),
    "Bremerhaven" to Pair(53.54, 8.58),
    "Dangast" to Pair(53.45, 8.11)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherOverlayScreen(viewModel: TideViewModel) {
    val weather by viewModel.currentWeather.collectAsState()
    val forecast by viewModel.forecastData.collectAsState()
    val isLoading by viewModel.weatherLoading.collectAsState()
    val error by viewModel.weatherError.collectAsState()

    // Lade Wetter beim ersten Aufruf (Cuxhaven als Default)
    LaunchedEffect(Unit) {
        if (weather == null) {
            viewModel.loadWeatherForLocation()
        }
    }

    val scrollState = rememberScrollState()

    // Aggregate hourly data into daily summaries
    val dailyForecast = remember(forecast) { aggregateToDays(forecast) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NauticalBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // ── Header ──
        Text(
            "WETTERDASHBOARD",
            style = MaterialTheme.typography.labelMedium,
            color = NauticalTextSecondary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ── Stationsauswahl ──
        var selectedStationName by remember { mutableStateOf("Cuxhaven") }
        var expanded by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.menuAnchor()
                ) {
                    Text(
                        selectedStationName,
                        color = NauticalPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("▼", color = NauticalPrimary, fontSize = 14.sp)
                }
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    containerColor = NauticalSurface
                ) {
                    weatherStations.forEach { (name, coords) ->
                        DropdownMenuItem(
                            text = { Text(name, color = NauticalTextPrimary) },
                            onClick = {
                                selectedStationName = name
                                viewModel.loadWeatherForLocation(coords.first, coords.second)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }

        // ── Ladezustand ──
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NauticalPrimary)
            }
            return@Column
        }

        if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = NauticalNoGoBg)
            ) {
                Text(error ?: "", modifier = Modifier.padding(16.dp), color = NauticalNoGo)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { viewModel.loadWeatherForLocation() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = NauticalPrimary)
            ) {
                Text("Erneut laden", color = NauticalTextOnPrimary)
            }
            return@Column
        }

        val w = weather ?: return@Column

        // ══════════════════════════════════════════════════
        // ── SEGEL-BEWERTUNG (Ampel-Banner) ──
        // ══════════════════════════════════════════════════
        val (isGo, isWarning, summaryText) = remember(w) {
            DecisionLogic.evaluate(w)
        }

        val bannerBg = when {
            !isGo -> NauticalNoGoBg
            isWarning -> Color(0xFF2E2A0A)   // dunkles Amber
            else -> NauticalGoBg
        }
        val bannerBorder = when {
            !isGo -> NauticalNoGo
            isWarning -> NauticalAccentWarm
            else -> NauticalGo
        }
        val bannerIcon = when {
            !isGo -> Icons.Default.Clear
            isWarning -> Icons.Default.Warning
            else -> Icons.Default.CheckCircle
        }
        val bannerLabel = when {
            !isGo -> "TÖRN NICHT EMPFOHLEN"
            isWarning -> "EINSCHRÄNKUNGEN"
            else -> "GUTE SEGELBEDINGUNGEN"
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, bannerBorder.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = bannerBg),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        bannerIcon,
                        contentDescription = null,
                        tint = bannerBorder,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            "SEGEL-BEWERTUNG",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = NauticalTextSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            bannerLabel,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = bannerBorder
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    summaryText,
                    color = NauticalTextSecondary,
                    fontSize = 12.sp
                )

                // Detail-Chips
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val windBft = kmhToBeaufort(w.windSpeed ?: 0.0)
                    AssessmentChip(
                        label = "Wind Bft $windBft",
                        isOk = windBft < 6,
                        isWarning = windBft in 6..7
                    )
                    AssessmentChip(
                        label = "Böen ${w.windGustSpeed?.let { "%.0f".format(it) } ?: "-"}",
                        isOk = (w.windGustSpeed ?: 0.0) < 60.0,
                        isWarning = (w.windGustSpeed ?: 0.0) in 60.0..89.9
                    )
                    AssessmentChip(
                        label = "Sicht ${w.visibility?.let { if (it >= 1000) "%.1f km".format(it / 1000.0) else "${it}m" } ?: "-"}",
                        isOk = (w.visibility ?: 10000) >= 2000,
                        isWarning = (w.visibility ?: 10000) in 1000..1999
                    )
                }
            }
        }

        // ══════════════════════════════════════════════════
        // ── AKTUELL ──
        // ══════════════════════════════════════════════════

        Text(
            "AKTUELL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = NauticalTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // ── Aktuell: Wind, Temperatur, Sicht ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Search,
                title = "Wind",
                value = "${w.windSpeed?.let { "%.0f".format(it) } ?: "-"} km/h",
                subValue = "Bft ${kmhToBeaufort(w.windSpeed ?: 0.0)} | ${windDirectionToText(w.windDirection ?: 0)}"
            )
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Info,
                title = "Luft",
                value = "${w.temperature?.let { "%.1f".format(it) } ?: "-"}°C",
                subValue = "Taupunkt ${w.dewPoint?.let { "%.1f".format(it) } ?: "-"}°C"
            )
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                title = "Sicht",
                value = "${w.visibility?.let { "%.1f".format(it / 1000.0) } ?: "-"} km",
                subValue = "${w.visibility ?: "-"} m"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Zweite Reihe: Böen, Druck, Bewölkung ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Warning,
                title = "Böen",
                value = "${w.windGustSpeed?.let { "%.0f".format(it) } ?: "-"} km/h",
                subValue = "Bft ${kmhToBeaufort(w.windGustSpeed ?: 0.0)}"
            )
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Refresh,
                title = "Druck",
                value = "${w.pressureMsl?.let { "%.1f".format(it) } ?: "-"} hPa",
                subValue = "Solar ${w.solar?.let { "%.2f".format(it) } ?: "-"} kWh/m²"
            )
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Info,
                title = "Wolken",
                value = "${w.cloudCover ?: "-"}%",
                subValue = "${((w.cloudCover ?: 0) * 8) / 100}/8 Okta"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Dritte Reihe: Feuchtigkeit, Taupunkt, Niederschlag ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.AccountCircle,
                title = "Feuchte",
                value = "${w.relativeHumidity ?: "-"}%",
                subValue = "Taup. ${w.dewPoint?.let { "%.1f".format(it) } ?: "-"}°C"
            )
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Info,
                title = "Niederschl.",
                value = "${w.precipitation?.let { "%.1f".format(it) } ?: "0.0"} mm",
                subValue = "Wsk. ${w.precipitationProbability ?: "-"}%"
            )
            WeatherSquareCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Info,
                title = "Sonne",
                value = "${w.sunshine?.let { "%.0f".format(it) } ?: "-"} min",
                subValue = "letzte Stunde"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ══════════════════════════════════════════════════
        // ── 7-TAGE VORHERSAGE (erweitert) ──
        // ══════════════════════════════════════════════════
        Text(
            "7-TAGE VORHERSAGE",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = NauticalTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (dailyForecast.isEmpty()) {
            Text("Keine Vorhersagedaten verfügbar", color = NauticalTextSecondary, fontSize = 12.sp)
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(dailyForecast) { day ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .width(105.dp)
                            .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            // Tag
                            Text(
                                day.dayLabel,
                                fontWeight = FontWeight.Bold,
                                color = NauticalTextPrimary,
                                fontSize = 13.sp
                            )
                            // Wetter-Emoji + Condition
                            Text(conditionEmoji(day.condition), fontSize = 22.sp)
                            Text(
                                day.condition,
                                fontSize = 9.sp,
                                color = NauticalTextSecondary,
                                maxLines = 1
                            )
                            // Temperatur
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${day.highTemp}°",
                                    fontWeight = FontWeight.Bold,
                                    color = NauticalTextPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "${day.lowTemp}°",
                                    fontSize = 11.sp,
                                    color = NauticalTextSecondary
                                )
                            }
                            // Wind
                            Text(
                                "💨 ${day.maxWind} km/h",
                                fontSize = 9.sp,
                                color = if (day.maxWind >= 62) NauticalNoGo
                                       else if (day.maxWind >= 39) NauticalAccentWarm
                                       else NauticalSecondary
                            )
                            // Niederschlag
                            if (day.totalPrecip > 0.1) {
                                Text(
                                    "🌧 ${"%.1f".format(day.totalPrecip)} mm",
                                    fontSize = 9.sp,
                                    color = NauticalSecondary
                                )
                            }
                            // Niederschlagswahrscheinlichkeit
                            if (day.maxPrecipProb > 0) {
                                Text(
                                    "☔ ${day.maxPrecipProb}%",
                                    fontSize = 9.sp,
                                    color = if (day.maxPrecipProb >= 70) NauticalAccentWarm
                                           else NauticalTextSecondary
                                )
                            }
                            // Sichtweite (wenn schlecht)
                            if (day.minVisibility != null && day.minVisibility < 5000) {
                                Text(
                                    "🌫 ${"%.1f".format(day.minVisibility / 1000.0)} km",
                                    fontSize = 9.sp,
                                    color = if (day.minVisibility < 1000) NauticalNoGo
                                           else NauticalAccentWarm
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── WINDRICHTUNG ──
        Text(
            "WINDRICHTUNG",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = NauticalTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        windArrow(w.windDirection ?: 0),
                        fontSize = 40.sp
                    )
                    Text(
                        "${w.windDirection ?: "-"}° ${windDirectionToText(w.windDirection ?: 0)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        color = NauticalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Böen bis ${w.windGustSpeed?.let { "%.0f".format(it) } ?: "-"} km/h",
                        color = NauticalAccentWarm,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── NIEDERSCHLAG & SONNENSCHEIN ──
        Text(
            "NIEDERSCHLAG & SONNENSCHEIN",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = NauticalTextSecondary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Niederschlag", color = NauticalTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "${w.precipitation?.let { "%.1f".format(it) } ?: "0.0"} mm",
                        color = NauticalTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                    Text(
                        translateCondition(w.condition),
                        color = NauticalTextSecondary,
                        fontSize = 11.sp
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Sonnenschein", color = NauticalTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "${w.sunshine?.let { "%.0f".format(it) } ?: "-"} min",
                        color = NauticalSunshine,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                    Text(
                        "letzte Stunde",
                        color = NauticalTextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ══════════════════════════════════════════════════
        // ── DATENQUELLE & AKTUALISIERUNG ──
        // ══════════════════════════════════════════════════
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
                .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalInfoBg),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = NauticalInfoText,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "DATENQUELLE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = NauticalInfoText,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Aktualisierungszeitpunkt aus dem Timestamp der Wetterdaten
                val updateTime = w.timestamp?.let { ts ->
                    try {
                        val parsed = java.time.OffsetDateTime.parse(ts)
                        val formatter = java.time.format.DateTimeFormatter.ofPattern(
                            "dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMAN
                        )
                        parsed.format(formatter)
                    } catch (_: Exception) {
                        ts.take(16).replace("T", " ")
                    }
                } ?: "Unbekannt"

                Text(
                    "Letzte Aktualisierung: $updateTime",
                    color = NauticalTextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Quelle: Bright Sky API (Open-Source) → Deutscher Wetterdienst (DWD)",
                    color = NauticalTextSecondary,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Die Wetterdaten werden stündlich vom DWD erhoben und über die Bright Sky API bereitgestellt. " +
                    "Vorhersagen basieren auf dem MOSMIX-Modell des DWD.",
                    color = NauticalTextSecondary,
                    fontSize = 10.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

// ── Assessment Chip ──

@Composable
private fun AssessmentChip(label: String, isOk: Boolean, isWarning: Boolean) {
    val bg = when {
        !isOk && !isWarning -> NauticalNoGoBg   // actually bad (no-go)
        !isOk -> NauticalNoGoBg
        isWarning -> Color(0xFF2E2A0A)
        else -> NauticalGoBg
    }
    val fg = when {
        !isOk -> NauticalNoGo
        isWarning -> NauticalAccentWarm
        else -> NauticalGo
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .border(1.dp, fg.copy(alpha = 0.3f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

// ── Daily aggregation from hourly data ──

data class DailyForecast(
    val dayLabel: String,
    val condition: String,
    val highTemp: Int,
    val lowTemp: Int,
    val maxWind: Int,
    val totalPrecip: Double,
    val maxPrecipProb: Int,
    val minVisibility: Int?
)

private fun aggregateToDays(hourlyData: List<WeatherDto>): List<DailyForecast> {
    if (hourlyData.isEmpty()) return emptyList()

    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val dayNames = listOf("So", "Mo", "Di", "Mi", "Do", "Fr", "Sa")
    val today = LocalDate.now()

    val berlinZone = ZoneId.of("Europe/Berlin")

    // Group by LOCAL date (Europe/Berlin) instead of UTC date.
    // UTC grouping causes evening hours (22-24 MESZ) to land on the wrong day,
    // leading to temperature discrepancies vs. Google/other local-time services.
    val grouped = hourlyData.groupBy { dto ->
        try {
            val utc = OffsetDateTime.parse(dto.timestamp ?: "")
            utc.atZoneSameInstant(berlinZone).toLocalDate().format(fmt)
        } catch (_: Exception) {
            dto.timestamp?.take(10) ?: ""
        }
    }.filterKeys { it.isNotEmpty() }

    return grouped.entries.take(7).mapIndexed { index, (dateStr, hours) ->
        val date = try { LocalDate.parse(dateStr, fmt) } catch (_: Exception) { today.plusDays(index.toLong()) }
        val dayLabel = if (date == today) "Heute" else dayNames[date.dayOfWeek.value % 7]

        val temps = hours.mapNotNull { it.temperature }
        val winds = hours.mapNotNull { it.windSpeed }
        val precips = hours.mapNotNull { it.precipitation }
        val precipProbs = hours.mapNotNull { it.precipitationProbability }
        val visibilities = hours.mapNotNull { it.visibility }

        // Dominant icon: Nutze das icon-Feld statt condition!
        // condition kennt nur Niederschlagsarten (dry/rain/snow...),
        // icon kennt auch Bewölkung (cloudy, partly-cloudy-day, clear-day...)
        val icons = hours.mapNotNull { it.icon }

        // Priorität: Schlecht-Wetter-Icons > Bewölkung > Klar
        // So wird z.B. "cloudy" nicht von ein paar "clear-day" Stunden verdrängt
        val iconPriority = mapOf(
            "thunderstorm" to 10, "hail" to 9, "snow" to 8,
            "sleet" to 7, "rain" to 6, "fog" to 5, "wind" to 4,
            "cloudy" to 3, "partly-cloudy-night" to 2, "partly-cloudy-day" to 2,
            "clear-night" to 1, "clear-day" to 1
        )

        // Wähle das Icon mit der höchsten Priorität, das mindestens 2x vorkommt
        // Fallback: häufigstes Icon
        val iconCounts = icons.groupBy { it }.mapValues { it.value.size }
        val dominantIcon = iconCounts.entries
            .filter { it.value >= 2 } // mindestens 2 Stunden
            .maxByOrNull { (iconPriority[it.key] ?: 0) * 100 + it.value }
            ?.key
            ?: iconCounts.maxByOrNull { it.value }?.key
            ?: "clear-day"

        // Fallback auf condition wenn icon nicht vorhanden
        val conditions = hours.mapNotNull { it.condition }
        val dominantCondition = conditions
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: "dry"

        // Verwende icon-basierten Text, fallback auf condition
        val displayCondition = translateCondition(dominantIcon)
            .let { if (it == dominantIcon) translateCondition(dominantCondition) else it }

        DailyForecast(
            dayLabel = dayLabel,
            condition = displayCondition,
            highTemp = temps.maxOrNull()?.toInt() ?: 0,
            lowTemp = temps.minOrNull()?.toInt() ?: 0,
            maxWind = winds.maxOrNull()?.toInt() ?: 0,
            totalPrecip = precips.sum(),
            maxPrecipProb = precipProbs.maxOrNull() ?: 0,
            minVisibility = visibilities.minOrNull()
        )
    }
}

// ── UI Components ──

@Composable
fun WeatherSquareCard(
    modifier: Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    subValue: String
) {
    Card(
        modifier = modifier
            .aspectRatio(0.9f)
            .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = NauticalPrimary, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(title, color = NauticalTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(value, color = NauticalTextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(subValue, color = NauticalTextSecondary, fontSize = 10.sp, maxLines = 1)
            }
        }
    }
}

// ── Helper Functions ──

fun kmhToBeaufort(kmh: Double): Int {
    return when {
        kmh < 1 -> 0; kmh < 6 -> 1; kmh < 12 -> 2; kmh < 20 -> 3
        kmh < 29 -> 4; kmh < 39 -> 5; kmh < 50 -> 6; kmh < 62 -> 7
        kmh < 75 -> 8; kmh < 89 -> 9; kmh < 103 -> 10; kmh < 118 -> 11
        else -> 12
    }
}

fun windDirectionToText(degrees: Int): String {
    return when ((degrees + 22) / 45 % 8) {
        0 -> "N"; 1 -> "NO"; 2 -> "O"; 3 -> "SO"
        4 -> "S"; 5 -> "SW"; 6 -> "W"; 7 -> "NW"; else -> "-"
    }
}

private fun windArrow(degrees: Int): String {
    // Arrow points in the direction the wind is coming FROM
    return when ((degrees + 22) / 45 % 8) {
        0 -> "⬇"; 1 -> "↙"; 2 -> "⬅"; 3 -> "↖"
        4 -> "⬆"; 5 -> "↗"; 6 -> "➡"; 7 -> "↘"; else -> "•"
    }
}

fun translateCondition(condition: String?): String {
    return when (condition) {
        "dry" -> "Trocken"
        "fog" -> "Nebel"
        "rain" -> "Regen"
        "sleet" -> "Schneeregen"
        "snow" -> "Schnee"
        "hail" -> "Hagel"
        "thunderstorm" -> "Gewitter"
        "wind" -> "Stürmisch"
        "partly-cloudy-day", "partly-cloudy-night" -> "Teilw. bewölkt"
        "cloudy" -> "Bewölkt"
        "clear-day", "clear-night" -> "Klar"
        else -> condition ?: "Unbekannt"
    }
}

private fun conditionEmoji(condition: String): String {
    return when (condition) {
        "Trocken" -> "☀️"
        "Klar" -> "☀️"
        "Teilw. bewölkt" -> "⛅"
        "Bewölkt" -> "☁️"
        "Regen" -> "🌧️"
        "Schnee" -> "❄️"
        "Schneeregen" -> "🌨️"
        "Nebel" -> "🌫️"
        "Gewitter" -> "⛈️"
        "Hagel" -> "🧊"
        "Stürmisch" -> "💨"
        else -> "🌤️"
    }
}

private fun visibilityCategory(meters: Int?): String {
    if (meters == null) return "-"
    return when {
        meters < 1000 -> "Sehr schlecht"
        meters < 4000 -> "Schlecht"
        meters < 10000 -> "Mäßig"
        meters < 20000 -> "Gut"
        else -> "Sehr gut"
    }
}

private fun gustCategory(gustKmh: Double?): String {
    if (gustKmh == null) return "-"
    return when {
        gustKmh >= 90 -> "Extrem!"
        gustKmh >= 60 -> "Stark"
        gustKmh >= 40 -> "Mäßig"
        gustKmh >= 20 -> "Leicht"
        else -> "Ruhig"
    }
}

private fun cloudCoverText(percent: Int?): String {
    if (percent == null) return "-"
    return when {
        percent < 13 -> "Wolkenlos"
        percent < 38 -> "Heiter"
        percent < 63 -> "Bewölkt"
        percent < 88 -> "Stark bew."
        else -> "Bedeckt"
    }
}
