package com.example.trnberechnung.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var selectedStationName by remember { mutableStateOf("Cuxhaven") }

    LaunchedEffect(Unit) {
        if (weather == null) {
            viewModel.loadWeatherForLocation()
        }
    }

    val scrollState = rememberScrollState()
    val dailyForecast = remember(forecast) { aggregateToDays(forecast) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NauticalBackground)
            .verticalScroll(scrollState)
    ) {

        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
            Text(
                "WETTER",
                modifier = Modifier.testTag("screen_header_weather"),
                style = MaterialTheme.typography.labelSmall,
                color = NauticalTextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Text(
                "DWD-WETTER NORDSEE",
                style = MaterialTheme.typography.labelSmall,
                color = NauticalTextSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.sp
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
        ) {
            items(weatherStations) { (name, coords) ->
                LocationChip(
                    name = name,
                    isSelected = name == selectedStationName,
                    weather = if (name == selectedStationName) weather else null,
                    onClick = {
                        selectedStationName = name
                        viewModel.loadWeatherForLocation(coords.first, coords.second)
                    }
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NauticalPrimary)
            }
            return@Column
        }

        if (error != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(containerColor = NauticalNoGoBg)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(error ?: "", color = NauticalNoGo)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.loadWeatherForLocation() },
                        colors = ButtonDefaults.buttonColors(containerColor = NauticalPrimary)
                    ) { Text("Erneut laden", color = NauticalTextOnPrimary) }
                }
            }
            return@Column
        }

        val w = weather ?: return@Column

        HeroWeatherCard(
            stationName = selectedStationName,
            weather = w,
            dailyForecast = dailyForecast,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)
        )

        if (forecast.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = NauticalSurface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "⏰  STÜNDLICHE VORHERSAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NauticalTextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    HourlyForecastRow(forecastData = forecast, currentWeather = w)
                }
            }
        }

        val windKn = w.windSpeed?.let { (it / 1.852).toInt() }
        val gustKn = w.windGustSpeed?.let { (it / 1.852).toInt() }
        val visKm = w.visibility?.let { it / 1000.0 }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DetailTile(
                    modifier = Modifier.weight(1f),
                    icon = "≈", label = "WIND",
                    value = "${windKn ?: "-"} kn",
                    subValue = "Bft ${kmhToBeaufort(w.windSpeed ?: 0.0)} · ${windDirectionToText(w.windDirection ?: 0)}"
                )
                DetailTile(
                    modifier = Modifier.weight(1f),
                    icon = "◎", label = "BÖEN",
                    value = "${gustKn ?: "-"} kn",
                    subValue = gustCategory(w.windGustSpeed)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DetailTile(
                    modifier = Modifier.weight(1f),
                    icon = "🌡", label = "GEFÜHLT",
                    value = "${w.dewPoint?.let { "%.0f".format(it) } ?: "-"}°C",
                    subValue = "Feuchte ${w.relativeHumidity ?: "-"}%"
                )
                DetailTile(
                    modifier = Modifier.weight(1f),
                    icon = "⏱", label = "LUFTDRUCK",
                    value = "${w.pressureMsl?.let { "%.0f".format(it) } ?: "-"} hPa",
                    subValue = "Taupunkt ${w.dewPoint?.let { "%.1f".format(it) } ?: "-"}°C"
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DetailTile(
                    modifier = Modifier.weight(1f),
                    icon = "👁", label = "SICHTWEITE",
                    value = "${visKm?.let { "%.0f".format(it) } ?: "-"} km",
                    subValue = visibilityCategory(w.visibility)
                )
                DetailTile(
                    modifier = Modifier.weight(1f),
                    icon = "☁", label = "BEWÖLKUNG",
                    value = "${w.cloudCover ?: "-"}%",
                    subValue = cloudCoverText(w.cloudCover)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (dailyForecast.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = NauticalSurface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "📅  7-TAGE-VORHERSAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = NauticalTextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    val weekMin = dailyForecast.minOf { it.lowTemp }
                    val weekMax = dailyForecast.maxOf { it.highTemp }
                    dailyForecast.forEachIndexed { index, day ->
                        if (index > 0) HorizontalDivider(color = NauticalDivider, thickness = 0.5.dp)
                        WeekForecastRow(day = day, weekMin = weekMin, weekMax = weekMax)
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationChip(name: String, isSelected: Boolean, weather: WeatherDto?, onClick: () -> Unit) {
    val chipBg = if (isSelected) NauticalSurface else Color(0xFF131F2E)
    val chipBorder = if (isSelected) NauticalPrimary else NauticalDivider
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(chipBg)
            .border(if (isSelected) 1.5.dp else 1.dp, chipBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (isSelected && weather != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(iconToEmoji(weather.icon ?: weather.condition), fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(name, fontWeight = FontWeight.Bold, color = NauticalTextPrimary, fontSize = 14.sp)
                }
                Text(
                    "${weather.temperature?.let { "%.0f".format(it) } ?: "-"}° · ${translateCondition(weather.icon ?: weather.condition)}",
                    color = NauticalTextSecondary, fontSize = 10.sp, maxLines = 1
                )
            }
        } else {
            Text(name, fontWeight = FontWeight.Normal, color = NauticalTextSecondary, fontSize = 14.sp)
        }
    }
}

@Composable
private fun HeroWeatherCard(
    stationName: String,
    weather: WeatherDto,
    dailyForecast: List<DailyForecast>,
    modifier: Modifier = Modifier
) {
    val gradient = Brush.linearGradient(colors = listOf(Color(0xFF1A6B9E), Color(0xFF1B8A7E)))
    val today = dailyForecast.firstOrNull()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(gradient)
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(stationName, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
                Text(iconToEmoji(weather.icon ?: weather.condition), fontSize = 42.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${weather.temperature?.let { "%.0f".format(it) } ?: "-"}°",
                    fontWeight = FontWeight.Bold, fontSize = 52.sp, color = Color.White
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text(
                        translateCondition(weather.icon ?: weather.condition),
                        fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White
                    )
                    if (today != null) {
                        Text(
                            "H: ${today.highTemp}°  T: ${today.lowTemp}°",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyForecastRow(forecastData: List<WeatherDto>, currentWeather: WeatherDto) {
    val berlinZone = ZoneId.of("Europe/Berlin")
    val now = OffsetDateTime.now()
    val nextHours = forecastData
        .filter { dto ->
            try { !OffsetDateTime.parse(dto.timestamp ?: "").isBefore(now.minusMinutes(30)) }
            catch (_: Exception) { false }
        }
        .take(12)

    val allItems: List<Pair<String?, WeatherDto>> = buildList {
        add(null to currentWeather)
        addAll(nextHours.map { it.timestamp to it })
    }

    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(allItems) { (ts, dto) ->
            val label = if (ts == null) "Jetzt" else try {
                OffsetDateTime.parse(ts).atZoneSameInstant(berlinZone)
                    .format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (_: Exception) { "–" }
            val windKn = dto.windSpeed?.let { (it / 1.852).toInt() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(label, color = NauticalTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(iconToEmoji(dto.icon ?: dto.condition), fontSize = 22.sp)
                Text(
                    "${dto.temperature?.let { "%.0f".format(it) } ?: "-"}°",
                    color = NauticalTextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp
                )
                Text("${windKn ?: "-"} kn", color = NauticalTextSecondary, fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun DetailTile(modifier: Modifier = Modifier, icon: String, label: String, value: String, subValue: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(icon, color = NauticalTextSecondary, fontSize = 13.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Text(label, color = NauticalTextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, color = NauticalPrimary, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(subValue, color = NauticalTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun WeekForecastRow(day: DailyForecast, weekMin: Int, weekMax: Int) {
    val range = (weekMax - weekMin).coerceAtLeast(1).toFloat()
    val startFrac = ((day.lowTemp - weekMin) / range).coerceIn(0f, 1f)
    val endFrac = ((day.highTemp - weekMin) / range).coerceIn(0f, 1f)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(day.dayLabel, fontWeight = FontWeight.Bold, color = NauticalTextPrimary, fontSize = 15.sp, modifier = Modifier.width(50.dp))
        Text(iconToEmoji(day.condition), fontSize = 20.sp, modifier = Modifier.width(32.dp))
        Text(
            "${day.maxPrecipProb}%",
            color = if (day.maxPrecipProb >= 40) NauticalAccentWarm else NauticalTextSecondary,
            fontSize = 12.sp, modifier = Modifier.width(36.dp)
        )
        Text("${day.lowTemp}°", color = NauticalTextSecondary, fontSize = 13.sp, modifier = Modifier.width(30.dp))
        Box(modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(NauticalDivider)) {
            val cs: Array<Pair<Float, Color>> = arrayOf(
                0f to Color.Transparent,
                startFrac to Color.Transparent,
                startFrac to NauticalSecondary,
                endFrac to NauticalGo,
                endFrac to Color.Transparent,
                1f to Color.Transparent
            )
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(colorStops = cs)
                )
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "${day.highTemp}°", fontWeight = FontWeight.Bold, color = NauticalTextPrimary,
            fontSize = 13.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.End
        )
    }
}

private fun iconToEmoji(iconOrCondition: String?): String = when (iconOrCondition) {
    "clear-day", "clear-night", "dry" -> "☀️"
    "partly-cloudy-day", "partly-cloudy-night" -> "⛅"
    "cloudy" -> "☁️"
    "fog" -> "🌫️"
    "rain" -> "🌧️"
    "sleet" -> "🌨️"
    "snow" -> "❄️"
    "hail" -> "🧊"
    "thunderstorm" -> "⛈️"
    "wind" -> "💨"
    else -> conditionEmoji(translateCondition(iconOrCondition))
}

@Composable
private fun AssessmentChip(label: String, isOk: Boolean, isWarning: Boolean) {
    val bg = when {
        !isOk && !isWarning -> NauticalNoGoBg
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

        val icons = hours.mapNotNull { it.icon }

        val iconPriority = mapOf(
            "thunderstorm" to 10, "hail" to 9, "snow" to 8,
            "sleet" to 7, "rain" to 6, "fog" to 5, "wind" to 4,
            "cloudy" to 3, "partly-cloudy-night" to 2, "partly-cloudy-day" to 2,
            "clear-night" to 1, "clear-day" to 1
        )

        val iconCounts = icons.groupBy { it }.mapValues { it.value.size }
        val dominantIcon = iconCounts.entries
            .filter { it.value >= 2 }
            .maxByOrNull { (iconPriority[it.key] ?: 0) * 100 + it.value }
            ?.key
            ?: iconCounts.maxByOrNull { it.value }?.key
            ?: "clear-day"

        val conditions = hours.mapNotNull { it.condition }
        val dominantCondition = conditions
            .groupBy { it }
            .maxByOrNull { it.value.size }
            ?.key ?: "dry"

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
