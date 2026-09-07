package com.example.trnberechnung.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.logic.TideTimes
import com.example.trnberechnung.model.TideEvent
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.TideViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin


@Composable
fun WeatherScreen(
    viewModel: TideViewModel,
    topOverlayClearance: Dp = 0.dp,
    bottomOverlayClearance: Dp = 0.dp
) {
    val adaptiveLayout = currentAdaptiveLayout()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Wetter, 1 = Gezeiten
    val weather by viewModel.currentWeather.collectAsState()
    val forecast by viewModel.forecastData.collectAsState()
    val selectedStation by viewModel.selectedStation.collectAsState()
    val allStations by viewModel.allStations.collectAsState()
    val tideEvents by viewModel.currentTideEvents.collectAsState()
    val lastWeatherUpdate by viewModel.lastWeatherUpdate.collectAsState()
    var showStationDialog by remember { mutableStateOf(false) }

    val weatherScrollState = rememberScrollState()
    val tideScrollState = rememberScrollState()

    val dailyForecast = remember(forecast) { aggregateToDays(forecast) }

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val revierBgGradient = if (isDarkTheme) {
        Brush.verticalGradient(colors = listOf(Color(0xFF0F172A), Color(0xFF020617)))
    } else {
        Brush.verticalGradient(colors = listOf(Color(0xFF3A8DBC), Color(0xFF1B4E7A)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_weather")
            .background(revierBgGradient)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.height(topOverlayClearance + 6.dp))
            if (adaptiveLayout.isTablet) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = adaptiveLayout.horizontalScreenPadding),
                    contentAlignment = Alignment.Center
                ) {
                    WeatherTabSwitcher(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        modifier = Modifier
                            .widthIn(max = adaptiveLayout.compactContentMaxWidth)
                            .fillMaxWidth()
                    )
                }
            } else {
                WeatherTabSwitcher(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
            }
            Spacer(
                modifier = Modifier.height(
                    if (adaptiveLayout.isTablet) TabletLayoutTokens.SectionSpacing else 12.dp
                )
            )

            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (targetTab == 0) {
                            val animCond = "${weather?.icon} ${weather?.condition}"
                            WeatherAnimationLayer(animCond)
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(
                                    if (targetTab == 0) weatherScrollState else tideScrollState,
                                )
                                .padding(
                                    if (adaptiveLayout.isTablet) {
                                        PaddingValues(
                                            horizontal = adaptiveLayout.horizontalScreenPadding,
                                            vertical = TabletLayoutTokens.CardPadding
                                        )
                                    } else {
                                        PaddingValues(16.dp)
                                    }
                                ),
                            horizontalAlignment = if (adaptiveLayout.isTablet) {
                                Alignment.CenterHorizontally
                            } else {
                                Alignment.Start
                            },
                            verticalArrangement = Arrangement.spacedBy(
                                if (adaptiveLayout.isTablet) TabletLayoutTokens.SectionSpacing else 16.dp
                            )
                        ) {
                            if (adaptiveLayout.isTablet) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = adaptiveLayout.mainContentMaxWidth)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(TabletLayoutTokens.SectionSpacing)
                                ) {
                                    if (targetTab == 0) {
                                        val isLoading by viewModel.weatherLoading.collectAsState()
                                        // Only a cold start shows the skeleton. The app reloads itself
                                        // every few minutes, and blanking the whole tab each time would
                                        // make it look broken.
                                        if (isLoading && weather == null) {
                                            WeatherSkeleton()
                                        } else {
                                            WeatherContent(
                                                stationName = selectedStation?.gaugeLabel ?: "Standort wählen",
                                                weather = weather,
                                                forecast = forecast,
                                                dailyForecast = dailyForecast,
                                                lastUpdated = lastWeatherUpdate,
                                                onStationClick = { showStationDialog = true },
                                                viewModel = viewModel
                                            )
                                        }
                                    } else {
                                        val tideLoading by viewModel.tideLoading.collectAsState()
                                        TideContent(
                                            station = selectedStation,
                                            events = tideEvents,
                                            loading = tideLoading,
                                            onStationClick = { showStationDialog = true }
                                        )
                                    }
                                }
                            } else {
                                if (targetTab == 0) {
                                    val isLoading by viewModel.weatherLoading.collectAsState()
                                    // Only a cold start shows the skeleton. The app reloads itself
                                    // every few minutes, and blanking the whole tab each time would
                                    // make it look broken.
                                    if (isLoading && weather == null) {
                                        WeatherSkeleton()
                                    } else {
                                        WeatherContent(
                                            stationName = selectedStation?.gaugeLabel ?: "Standort wählen",
                                            weather = weather,
                                            forecast = forecast,
                                            dailyForecast = dailyForecast,
                                            lastUpdated = lastWeatherUpdate,
                                            onStationClick = { showStationDialog = true },
                                            viewModel = viewModel
                                        )
                                    }
                                } else {
                                    val tideLoading by viewModel.tideLoading.collectAsState()
                                    TideContent(
                                        station = selectedStation,
                                        events = tideEvents,
                                        loading = tideLoading,
                                        onStationClick = { showStationDialog = true }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(maxOf(32.dp, bottomOverlayClearance)))
                        }
                    }
                }
            }
        }
    }

    if (showStationDialog) {
        StationSelectionDialog(
            stations = allStations,
            selectedStation = selectedStation,
            onDismiss = { showStationDialog = false },
            onStationSelected = {
                viewModel.selectStation(it)
                showStationDialog = false
            }
        )
    }
}

/**
 * Turns the last successful load into a self-updating "wie frisch sind diese Daten" line.
 *
 * The header used to state "Aktualisiert vor 5 Min." as a literal, which was wrong the moment it
 * was written. Now that the app reloads on its own every few minutes, this label is the skipper's
 * only way to tell whether that is actually happening - so it ticks on its own once a minute
 * instead of only redrawing when new weather arrives.
 */
@Composable
private fun rememberFreshnessLabel(lastUpdated: Long?): String {
    if (lastUpdated == null) return "Wird geladen …"
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lastUpdated) {
        while (true) {
            kotlinx.coroutines.delay(30_000)
            now = System.currentTimeMillis()
        }
    }
    val minutes = ((now - lastUpdated).coerceAtLeast(0L) / 60_000L)
    return when {
        minutes < 1 -> "Gerade aktualisiert"
        minutes < 60 -> "Aktualisiert vor $minutes Min."
        else -> "Aktualisiert vor ${minutes / 60} Std."
    }
}

@Composable
fun WeatherTabSwitcher(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val isTablet = currentAdaptiveLayout().isTablet
    Row(
        modifier = if (isTablet) {
            modifier
                .fillMaxWidth()
                .height(TabletLayoutTokens.PrimaryControlHeight)
                .clip(RoundedCornerShape(30.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .padding(5.dp)
                .semantics {
                    contentDescription = "Ansicht auswählen"
                }
        } else {
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.15f))
                .padding(4.dp)
                .semantics {
                    contentDescription = "Ansicht auswählen"
                }
        }
    ) {
        TabItem(
            text = "Wetter",
            icon = "⛅",
            isSelected = selectedTab == 0,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(0) }
        )
        TabItem(
            text = "Gezeiten",
            icon = "🌊",
            isSelected = selectedTab == 1,
            modifier = Modifier.weight(1f),
            onClick = { onTabSelected(1) }
        )
    }
}

@Composable
fun TabItem(text: String, icon: String, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val isTablet = currentAdaptiveLayout().isTablet
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(if (isTablet) 25.dp else 20.dp))
            .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(
                onClickLabel = "$text anzeigen",
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                icon,
                fontSize = if (isTablet) 17.sp else 14.sp,
                modifier = Modifier.semantics { contentDescription = "" }
            )
            Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
            Text(
                text,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = if (isTablet) 17.sp else 14.sp
            )
        }
    }
}

@Composable
fun WeatherContent(
    stationName: String,
    weather: WeatherDto?,
    forecast: List<WeatherDto>,
    dailyForecast: List<DailyForecast>,
    lastUpdated: Long?,
    onStationClick: () -> Unit,
    viewModel: TideViewModel
) {
    val isTablet = currentAdaptiveLayout().isTablet
    val nowBerlin = remember { LocalDateTime.now(ZoneId.of("Europe/Berlin")) }
    val filteredForecast = remember(forecast) {
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Europe/Berlin"))
        forecast.filter {
            try {
                val timestampStr = it.timestamp?.replace(" ", "T") ?: ""
                val dt = if (timestampStr.contains("+") || timestampStr.endsWith("Z")) {
                    java.time.OffsetDateTime.parse(timestampStr).atZoneSameInstant(java.time.ZoneId.of("Europe/Berlin"))
                } else {
                    java.time.LocalDateTime.parse(timestampStr).atZone(java.time.ZoneId.of("Europe/Berlin"))
                }
                dt.isAfter(now.minusMinutes(30))
            } catch (e: Exception) { true }
        }.sortedBy { it.timestamp }
    }

    // Hero Weather
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.clickable(
                    onClickLabel = "Standort ändern",
                    onClick = onStationClick
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    stationName,
                    fontSize = if (isTablet) 38.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Standort wählen",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = if (isTablet) Modifier.size(TabletLayoutTokens.StandardIconSize) else Modifier
                )
            }
            Text(
                "${weather?.temperature?.toInt() ?: "--"}°",
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Aktuelle Temperatur: ${weather?.temperature?.toInt() ?: "--"} Grad" },
                fontSize = if (isTablet) 102.sp else 86.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                translateCondition(weather?.condition),
                modifier = Modifier.fillMaxWidth(),
                fontSize = if (isTablet) 27.sp else 22.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.95f),
                textAlign = TextAlign.Center
            )
            val today = dailyForecast.firstOrNull()
            if (today != null) {
                Text(
                    "Höchstwert: ${today.highTemp}°  Tiefstwert: ${today.lowTemp}°",
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = if (isTablet) 18.sp else 15.sp,
                    textAlign = TextAlign.Center
                )
            }
            Text(
                "Gefühlt ${weather?.dewPoint?.toInt() ?: "--"}° · ${rememberFreshnessLabel(lastUpdated)}",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = if (isTablet) 10.dp else 8.dp),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isTablet) 15.sp else 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }

    // Hourly Forecast
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Info,
                    "Vorhersage Info",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "48-STUNDEN-VORHERSAGE",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(if (isTablet) 24.dp else 20.dp),
                modifier = Modifier.semantics { contentDescription = "Stündliche Vorhersage" }
            ) {
                items(filteredForecast.take(24)) { hour ->
                    val time = try {
                        OffsetDateTime.parse(hour.timestamp).atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (e: Exception) { "--:--" }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .then(if (isTablet) Modifier.widthIn(min = 72.dp) else Modifier)
                            .semantics(mergeDescendants = true) {}
                    ) {
                        Text(time, color = Color.White, fontSize = if (isTablet) 15.sp else 12.sp)
                        Text(
                            iconToEmoji(hour.icon ?: hour.condition),
                            fontSize = if (isTablet) 30.sp else 24.sp,
                            modifier = Modifier
                                .padding(vertical = if (isTablet) 6.dp else 4.dp)
                                .semantics { contentDescription = translateCondition(hour.condition) }
                        )
                        Text(
                            "${hour.temperature?.toInt() ?: "--"}°",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isTablet) 17.sp else 14.sp
                        )
                        Spacer(modifier = Modifier.height(if (isTablet) 6.dp else 4.dp))
                        Text(
                            "${(hour.windSpeed?.div(1.852))?.toInt() ?: "--"} kn",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isTablet) 12.sp else 10.sp
                        )
                        Text(
                            "B ${(hour.windGustSpeed?.div(1.852))?.toInt() ?: "--"}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isTablet) 12.sp else 10.sp
                        )
                    }
                }
            }
        }
    }

    // Wind Compass & Details
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Air,
                    "Wind Icon",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "WIND IM REVIER",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 20.dp else 16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        val speed = (weather?.windSpeed?.div(1.852))?.toInt() ?: "--"
                        val gusts = (weather?.windGustSpeed?.div(1.852))?.toInt() ?: "--"
                        val dir = windDirectionToText(weather?.windDirection ?: 0)
                        contentDescription = "Wind: $speed Knoten aus $dir. Böen bis $gusts Knoten."
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(if (isTablet) 136.dp else 110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    WindCompass(degrees = weather?.windDirection ?: 0)
                }
                Spacer(modifier = Modifier.width(if (isTablet) 32.dp else 24.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(if (isTablet) 10.dp else 8.dp)
                ) {
                    WindDetailRow("Grundwind", "${(weather?.windSpeed?.div(1.852))?.toInt() ?: "--"} kn")
                    WindDetailRow("Böen", "${(weather?.windGustSpeed?.div(1.852))?.toInt() ?: "--"} kn")
                    WindDetailRow("Richtung", "${windDirectionToText(weather?.windDirection ?: 0)} · ${weather?.windDirection ?: 0}°")
                }
            }
        }
    }

    // Wind Karte
    var selectedWindHour by remember { mutableStateOf(0) }

    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Map,
                    "Karte",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "WINDKARTE OSTFRIESLAND",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }

            // Time Selector for Wind Map
            Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(if (isTablet) 12.dp else 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Zeitpunkt für Windkarte wählen" }
            ) {
                items(12) { i ->
                    val forecastItem = if (i == 0) null else filteredForecast.getOrNull(i - 1)
                    val time = if (i == 0) "Jetzt" else try {
                        OffsetDateTime.parse(forecastItem?.timestamp).atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (e: Exception) { "--:--" }
                    val day = if (i == 0) {
                        try {
                            OffsetDateTime.now(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("EEE"))
                        } catch (e: Exception) { "" }
                    } else {
                        try {
                            OffsetDateTime.parse(forecastItem?.timestamp).atZoneSameInstant(ZoneId.of("Europe/Berlin")).format(DateTimeFormatter.ofPattern("EEE"))
                        } catch (e: Exception) { "" }
                    }

                    Column(
                        modifier = Modifier
                            .then(
                                if (isTablet) {
                                    Modifier
                                        .widthIn(min = 72.dp)
                                        .heightIn(min = 48.dp)
                                } else {
                                    Modifier
                                }
                            )
                            .clip(RoundedCornerShape(if (isTablet) 24.dp else 20.dp))
                            .background(if (selectedWindHour == i) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable(
                                onClickLabel = "Windkarte für $time anzeigen",
                                onClick = { selectedWindHour = i }
                            )
                            .padding(
                                horizontal = if (isTablet) 16.dp else 12.dp,
                                vertical = if (isTablet) 10.dp else 8.dp
                            ), // Increased vertical padding for touch target
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            day,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isTablet) 12.sp else 10.sp
                        )
                        Text(
                            time,
                            color = Color.White,
                            fontSize = if (isTablet) 15.sp else 12.sp,
                            fontWeight = if (selectedWindHour == i) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTablet) 310.dp else 250.dp)
                    .clip(RoundedCornerShape(if (isTablet) 18.dp else 12.dp))
                    .background(Color.White.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                val displayWeather = if (selectedWindHour == 0) weather else filteredForecast.getOrNull(selectedWindHour - 1)
                WindMapComponent(
                    modifier = Modifier.fillMaxSize(),
                    stations = viewModel.allStations.collectAsState().value,
                    currentWeather = displayWeather,
                    isLive = selectedWindHour == 0
                )

                // Overlay information removed
            }
        }
    }

    // Wind & Böen Graph
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Timeline,
                    "Graph",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "WIND UND BÖEN",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 8.dp else 6.dp))
            Row(
                modifier = Modifier.padding(start = if (isTablet) 48.dp else 40.dp),
                horizontalArrangement = Arrangement.spacedBy(if (isTablet) 20.dp else 16.dp)
            ) {
                Text(
                    "● GRUNDWIND",
                    color = Color(0xFF4FC3F7),
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "● BÖEN",
                    color = Color(0xFFFFB74D),
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 30.dp else 24.dp))
            Box(modifier = Modifier.semantics { contentDescription = "Grafik der Wind- und Böenvorhersage für die nächsten 12 Stunden." }) {
                WindGustGraph(filteredForecast.take(12))
            }
        }
    }

    // 7-Day Forecast
    if (dailyForecast.isNotEmpty()) {
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.DateRange,
                        "Kalender",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                    )
                    Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                    Text(
                        "7-TAGE-VORHERSAGE",
                        fontSize = if (isTablet) 12.sp else 10.sp,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() }
                    )
                }
                Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))
                val weekMin = dailyForecast.minOf { it.lowTemp }
                val weekMax = dailyForecast.maxOf { it.highTemp }
                dailyForecast.forEachIndexed { index, day ->
                    if (index > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.1f), thickness = 0.5.dp)
                    WeatherWeekForecastRow(day = day, weekMin = weekMin, weekMax = weekMax)
                }
            }
        }
    }
}

@Composable
fun WindCompass(degrees: Int) {
    val isTablet = currentAdaptiveLayout().isTablet
    Canvas(modifier = Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2

        drawCircle(
            color = Color.White.copy(alpha = 0.1f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Cardinal directions
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = (if (isTablet) 12.sp else 10.sp).toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            alpha = 150
        }

        drawContext.canvas.nativeCanvas.drawText(
            "N",
            center.x,
            center.y - radius + (if (isTablet) 18.dp else 15.dp).toPx(),
            paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "S",
            center.x,
            center.y + radius - (if (isTablet) 6.dp else 5.dp).toPx(),
            paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "W",
            center.x - radius + (if (isTablet) 12.dp else 10.dp).toPx(),
            center.y + (if (isTablet) 6.dp else 5.dp).toPx(),
            paint
        )
        drawContext.canvas.nativeCanvas.drawText(
            "O",
            center.x + radius - (if (isTablet) 12.dp else 10.dp).toPx(),
            center.y + (if (isTablet) 6.dp else 5.dp).toPx(),
            paint
        )

        // Arrow
        val angleRad = (degrees - 90) * PI / 180.0
        val arrowLen = radius - (if (isTablet) 24.dp else 20.dp).toPx()
        val endX = center.x + (arrowLen * cos(angleRad)).toFloat()
        val endY = center.y + (arrowLen * sin(angleRad)).toFloat()

        drawLine(
            color = Color(0xFF4FC3F7),
            start = center,
            end = Offset(endX, endY),
            strokeWidth = (if (isTablet) 3.5.dp else 3.dp).toPx(),
            cap = StrokeCap.Round
        )

        drawCircle(
            color = Color.White,
            radius = (if (isTablet) 5.dp else 4.dp).toPx(),
            center = center
        )

        // Direction label in center
        val centerPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = (if (isTablet) 17.sp else 14.sp).toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            windDirectionToText(degrees),
            center.x,
            center.y + (if (isTablet) 36.dp else 30.dp).toPx(),
            centerPaint
        )
    }
}

@Composable
fun WindGustGraph(forecast: List<WeatherDto>) {
    val isTablet = currentAdaptiveLayout().isTablet
    val density = LocalDensity.current
    val labelTextSize = with(density) { (if (isTablet) 12.sp else 10.sp).toPx() }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTablet) 200.dp else 160.dp)
            .padding(
                start = if (isTablet) 48.dp else 40.dp,
                end = if (isTablet) 20.dp else 16.dp,
                bottom = if (isTablet) 24.dp else 20.dp
            ) // Adjusted padding for labels
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (forecast.isEmpty()) return@Canvas

            val width = size.width
            val height = size.height
            val plotPaddingTop = (if (isTablet) 12.dp else 10.dp).toPx()
            val plotPaddingBottom = (if (isTablet) 24.dp else 20.dp).toPx()
            val plotHeight = height - plotPaddingTop - plotPaddingBottom
            val plotBottomY = height - plotPaddingBottom

            val windSpeeds = forecast.map { (it.windSpeed ?: 0.0) / 1.852 }
            val gustSpeeds = forecast.map { (it.windGustSpeed ?: 0.0) / 1.852 }

            val maxVal = (gustSpeeds.maxOrNull() ?: 10.0).coerceAtLeast(40.0).toFloat()
            val stepX = width / (forecast.size - 1)

            fun y(v: Double) = plotBottomY - (v.toFloat() / maxVal * plotHeight)

            // Grid lines and Y-axis labels (Lefthand side)
            val gridColor = Color.White.copy(alpha = 0.1f)
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                alpha = 140
                textSize = labelTextSize
                textAlign = android.graphics.Paint.Align.RIGHT
                isAntiAlias = true
            }

            val ySteps = listOf(0f, 10f, 20f, 30f, 40f)
            ySteps.forEach { step ->
                if (step <= maxVal) {
                    val yPos = y(step.toDouble())
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, yPos),
                        end = Offset(width, yPos),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        "${step.toInt()} kn",
                        -(if (isTablet) 10.dp else 8.dp).toPx(),
                        yPos + labelTextSize / 3,
                        labelPaint
                    )
                }
            }

            // Fill area between wind and gust
            val fillPath = Path()
            if (windSpeeds.isNotEmpty()) {
                fillPath.moveTo(0f, y(windSpeeds[0]))
                for (i in 1 until forecast.size) {
                    fillPath.lineTo(i * stepX, y(windSpeeds[i]))
                }
                for (i in forecast.size - 1 downTo 0) {
                    fillPath.lineTo(i * stepX, y(gustSpeeds[i]))
                }
                fillPath.close()
                drawPath(fillPath, color = Color.White.copy(alpha = 0.1f))

                // Wind line
                val windPath = Path()
                windPath.moveTo(0f, y(windSpeeds[0]))
                for (i in 1 until forecast.size) {
                    windPath.lineTo(i * stepX, y(windSpeeds[i]))
                }
                drawPath(
                    windPath,
                    color = Color(0xFF4FC3F7),
                    style = Stroke(
                        width = (if (isTablet) 2.5.dp else 2.dp).toPx(),
                        cap = StrokeCap.Round
                    )
                )

                // Gust line
                val gustPath = Path()
                gustPath.moveTo(0f, y(gustSpeeds[0]))
                for (i in 1 until forecast.size) {
                    gustPath.lineTo(i * stepX, y(gustSpeeds[i]))
                }
                drawPath(
                    gustPath,
                    color = Color(0xFFFFB74D),
                    style = Stroke(
                        width = (if (isTablet) 2.5.dp else 2.dp).toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f),
                        cap = StrokeCap.Round
                    )
                )
            }

            // Time labels (X-axis)
            val timePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                alpha = 140
                textSize = labelTextSize
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
            }

            forecast.forEachIndexed { i, hour ->
                if (i % 3 == 0) { // Every 3 hours
                    val x = i * stepX
                    val timeText = try {
                        val dt = OffsetDateTime.parse(hour.timestamp).atZoneSameInstant(ZoneId.of("Europe/Berlin"))
                        dt.format(DateTimeFormatter.ofPattern("HH:mm"))
                    } catch (e: Exception) { "" }
                    drawContext.canvas.nativeCanvas.drawText(
                        timeText,
                        x,
                        height + (if (isTablet) 15.dp else 12.dp).toPx(),
                        timePaint
                    )
                }
            }
        }
    }
}

@Composable
fun WeatherWeekForecastRow(day: DailyForecast, weekMin: Int, weekMax: Int) {
    val isTablet = currentAdaptiveLayout().isTablet
    var expanded by remember { mutableStateOf(false) }
    val range = (weekMax - weekMin).coerceAtLeast(1).toFloat()
    val startFrac = ((day.lowTemp - weekMin) / range).coerceIn(0f, 1f)
    val endFrac = ((day.highTemp - weekMin) / range).coerceIn(0f, 1f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .then(if (isTablet) Modifier.heightIn(min = 56.dp) else Modifier)
            .padding(vertical = if (isTablet) 6.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isTablet) 10.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                day.dayLabel,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = if (isTablet) 17.sp else 14.sp,
                modifier = Modifier.width(if (isTablet) 50.dp else 40.dp)
            )
            Text(
                iconToEmoji(day.condition),
                fontSize = if (isTablet) 25.sp else 20.sp,
                modifier = Modifier.width(if (isTablet) 40.dp else 32.dp)
            )
            if (day.maxPrecipProb > 0) {
                Text(
                    "${day.maxPrecipProb}%",
                    color = Color(0xFF4FC3F7),
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    modifier = Modifier.width(if (isTablet) 44.dp else 36.dp),
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.width(if (isTablet) 44.dp else 36.dp))
            }
            Text(
                "${day.lowTemp}°",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = if (isTablet) 16.sp else 13.sp,
                modifier = Modifier.width(if (isTablet) 38.dp else 30.dp),
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = if (isTablet) 10.dp else 8.dp)
                    .height(if (isTablet) 5.dp else 4.dp)
                    .clip(RoundedCornerShape(if (isTablet) 3.dp else 2.dp))
                    .background(Color.White.copy(alpha = 0.1f))
            ) {
                val cs: Array<Pair<Float, Color>> = arrayOf(
                    0f to Color.Transparent,
                    startFrac to Color.Transparent,
                    startFrac to Color(0xFF4FC3F7),
                    endFrac to Color(0xFFFFB74D),
                    endFrac to Color.Transparent,
                    1f to Color.Transparent
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.horizontalGradient(colorStops = cs)))
            }
            Text(
                "${day.highTemp}°",
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = if (isTablet) 16.sp else 13.sp,
                modifier = Modifier.width(if (isTablet) 38.dp else 30.dp),
                textAlign = TextAlign.End
            )

            val rotation by animateFloatAsState(if (expanded) 90f else 0f)
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .size(if (isTablet) 20.dp else 16.dp)
                    .graphicsLayer(rotationZ = rotation)
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = if (isTablet) 50.dp else 40.dp,
                        end = if (isTablet) 20.dp else 16.dp,
                        bottom = if (isTablet) 16.dp else 12.dp
                    )
                    .background(
                        Color.White.copy(alpha = 0.05f),
                        RoundedCornerShape(if (isTablet) 12.dp else 8.dp)
                    )
                    .padding(if (isTablet) 16.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (isTablet) 16.dp else 12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailInfoItem(Icons.Default.Air, "Wind / Böen", "${day.maxWind} / ${day.maxGust} kn")
                    DetailInfoItem(Icons.Default.WaterDrop, "Regenmenge", "%.1f mm".format(day.totalPrecip))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailInfoItem(
                        Icons.Default.Visibility,
                        "Sichtweite",
                        if (day.minVisibility != null) "${day.minVisibility} km" else "--"
                    )
                    DetailInfoItem(
                        Icons.Default.WbSunny,
                        "Sonnenschein",
                        if (day.totalSunshine != null) {
                            if (day.totalSunshine >= 60) {
                                "%.1f h".format(day.totalSunshine / 60.0)
                            } else {
                                "${day.totalSunshine.toInt()} min"
                            }
                        } else "--"
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailInfoItem(
                        Icons.Default.DeviceThermostat,
                        "Luftfeuchte",
                        if (day.avgHumidity != null) "${day.avgHumidity}%" else "--"
                    )
                    DetailInfoItem(
                        Icons.Default.Opacity,
                        "Regenrisiko",
                        "${day.maxPrecipProb}%"
                    )
                }
            }
        }
    }
}

@Composable
private fun DetailInfoItem(icon: ImageVector, label: String, value: String) {
    val isTablet = currentAdaptiveLayout().isTablet
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            null,
            tint = Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
        )
        Spacer(modifier = Modifier.width(if (isTablet) 6.dp else 4.dp))
        Column {
            Text(
                label,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = if (isTablet) 12.sp else 10.sp
            )
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = if (isTablet) 15.sp else 12.sp
            )
        }
    }
}

@Composable
fun WindDetailRow(label: String, value: String) {
    val isTablet = currentAdaptiveLayout().isTablet
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = if (isTablet) 17.sp else 14.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTablet) 17.sp else 14.sp)
    }
}

@Composable
fun TideContent(
    station: TideStationData?,
    events: List<TideEvent>,
    loading: Boolean = false,
    onStationClick: () -> Unit = {}
) {
    val isTablet = currentAdaptiveLayout().isTablet
    val now = LocalDateTime.now()

    val eventsWithTime = remember(events) { TideTimes.sortedByTime(events) }

    val nextEventPair = eventsWithTime.firstOrNull { it.second.isAfter(now) }
    val lastEventPair = eventsWithTime.lastOrNull { it.second.isBefore(now) }

    val isRising = nextEventPair?.first?.type == "HW"
    val statusText = if (isRising) "Steigendes Wasser" else "Fallendes Wasser"
    val nextEventLabel = if (nextEventPair?.first?.type == "HW") "Nächstes Hochwasser" else "Nächstes Niedrigwasser"

    val diffMinutes = nextEventPair?.let { java.time.Duration.between(now, it.second).toMinutes() } ?: 0
    val countdownText = if (diffMinutes > 0) {
        val h = diffMinutes / 60
        val m = diffMinutes % 60
        if (h > 0) "in $h Std. $m Min." else "in $m Min."
    } else ""

    // Hero Tide Card (Screenshot 3 style)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.clickable(
                        onClickLabel = "Station wählen",
                        onClick = { onStationClick() }
                    )
                ) {
                    Text(
                        station?.gaugeLabel ?: station?.area ?: "Unbekannt",
                        fontSize = if (isTablet) 29.sp else 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        "Station wählen",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(if (isTablet) 25.dp else 20.dp)
                    )
                }
                Text(
                    "BSH ${station?.gaugeLabel?.take(4) ?: "PEGEL"}",
                    fontSize = if (isTablet) 15.sp else 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(if (isTablet) 20.dp else 16.dp))

            // Tide Arrow Icon
            Box(
                modifier = Modifier
                    .size(if (isTablet) 60.dp else 48.dp) // Increased touch/focal area
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isRising) Icons.Default.ArrowOutward else Icons.Default.SouthEast,
                    contentDescription = if (isRising) "Steigendes Wasser" else "Fallendes Wasser",
                    tint = Color.White,
                    modifier = Modifier.size(if (isTablet) 34.dp else 28.dp)
                )
            }

            Spacer(modifier = Modifier.height(if (isTablet) 20.dp else 16.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "$statusText. $nextEventLabel um ${nextEventPair?.second?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--"}. $countdownText"
                }
            ) {
                Text(
                    statusText,
                    color = Color.White,
                    fontSize = if (isTablet) 22.sp else 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Text(
                    nextEventLabel,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = if (isTablet) 16.sp else 13.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isTablet) 10.dp else 8.dp))

                Text(
                    nextEventPair?.second?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "--:--",
                    color = Color.White,
                    fontSize = if (isTablet) 44.sp else 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    countdownText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = if (isTablet) 16.sp else 13.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    val windowStart = now.minusHours(12)
    val windowEnd = now.plusHours(60)

    // Include one event before and one event after the window for smooth interpolation
    val windowEvents = remember(eventsWithTime, windowStart, windowEnd) {
        val inWindow = eventsWithTime.filter { (_, dt) -> !dt.isBefore(windowStart) && !dt.isAfter(windowEnd) }
        val before = eventsWithTime.lastOrNull { it.second.isBefore(windowStart) }
        val after = eventsWithTime.firstOrNull { it.second.isAfter(windowEnd) }

        val result = mutableListOf<Pair<TideEvent, LocalDateTime>>()
        before?.let { result.add(it) }
        result.addAll(inWindow)
        after?.let { result.add(it) }
        result.distinctBy { it.second }.sortedBy { it.second }
    }

    // Astronomische Gezeiten (Horizontal Cards)
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.History,
                    "Uhrzeit",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "ASTRONOMISCHE GEZEITEN",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 16.dp else 12.dp))
            LazyRow(
                horizontalArrangement = if (isTablet) {
                    Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
                } else {
                    Arrangement.spacedBy(12.dp)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Liste der nächsten Gezeitenereignisse" }
            ) {
                val upcoming = eventsWithTime.filter { it.second.isAfter(now.minusHours(2)) }.take(4)
                items(upcoming) { (ev, dt) ->
                    TideEventTile(
                        type = ev.type,
                        time = dt.format(DateTimeFormatter.ofPattern("HH:mm")),
                        height = "%.2f m SKN".format(ev.value ?: 0.0),
                        diff = if (ev.type == "HW") "Flut" else "Ebbe"
                    )
                }
            }
        }
    }

    // Gezeitengrundwerte
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Straighten,
                    "Maßstab",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "GEZEITENGRUNDWERTE",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 20.dp else 16.dp))
            if (isTablet) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = TabletLayoutTokens.CompactContentMaxWidth)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TideBasisTile("MHW", station?.meanHighWater?.let { "%.2f m".format(it) } ?: "-- m")
                        TideBasisTile("MNW", station?.meanLowWater?.let { "%.2f m".format(it) } ?: "-- m")
                        val mth = if (station?.meanHighWater != null && station.meanLowWater != null) {
                            "%.2f m".format(station.meanHighWater - station.meanLowWater)
                        } else {
                            "-- m"
                        }
                        TideBasisTile("MTH", mth)
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TideBasisTile("MHW", station?.meanHighWater?.let { "%.2f m".format(it) } ?: "-- m")
                    TideBasisTile("MNW", station?.meanLowWater?.let { "%.2f m".format(it) } ?: "-- m")
                    val mth = if (station?.meanHighWater != null && station.meanLowWater != null) {
                        "%.2f m".format(station.meanHighWater - station.meanLowWater)
                    } else "-- m"
                    TideBasisTile("MTH", mth)
                }
            }
        }
    }

    // Wasserstandsvorhersage Graph
    GlassCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(if (isTablet) TabletLayoutTokens.CardPadding else 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.SsidChart,
                    "Graph",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(if (isTablet) 18.dp else 14.dp)
                )
                Spacer(modifier = Modifier.width(if (isTablet) 8.dp else 6.dp))
                Text(
                    "WASSERSTANDSVORHERSAGE",
                    fontSize = if (isTablet) 12.sp else 10.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Spacer(modifier = Modifier.height(if (isTablet) 24.dp else 20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTablet) 272.dp else 220.dp)
                    .padding(horizontal = if (isTablet) 6.dp else 4.dp)
                    .semantics { contentDescription = "Grafische Darstellung der Wasserstandsvorhersage." }
            ) {
                if (windowEvents.size < 2) {
                    Text(
                        if (loading) "Lade Daten..." else "Keine Daten verfügbar",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = if (isTablet) 15.sp else 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    TideCurveCanvas(
                        windowEvents.map { it.first },
                        windowStart,
                        windowEnd,
                        now,
                        station?.meanHighWater,
                        station?.meanLowWater
                    )
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
    now: LocalDateTime,
    mhw: Double? = null,
    mnw: Double? = null
) {
    val isTablet = currentAdaptiveLayout().isTablet
    val futureColor = Color(0xFF4FC3F7)
    val pastColor = Color.White
    val gridColor = Color.White.copy(alpha = 0.1f)
    val nowColor = Color(0xFFFFA726)
    val density = androidx.compose.ui.platform.LocalDensity.current
    val labelTextSize = with(density) { (if (isTablet) 11.sp else 9.sp).toPx() }
    val dateTextSize = with(density) { (if (isTablet) 12.sp else 10.sp).toPx() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val paddingLeft = (if (isTablet) 42.dp else 35.dp).toPx()
        val paddingRight = (if (isTablet) 42.dp else 35.dp).toPx()
        val paddingTop = (if (isTablet) 24.dp else 20.dp).toPx()
        val paddingBottom = (if (isTablet) 66.dp else 55.dp).toPx()

        val drawWidth = width - paddingLeft - paddingRight
        val drawHeight = height - paddingTop - paddingBottom
        val plotBottomY = height - paddingBottom

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

        if (pts.isEmpty()) return@Canvas

        val yMax = (pts.maxOf { it.second } + 0.4).coerceAtLeast(3.2)
        val yMin = (pts.minOf { it.second } - 0.4).coerceAtMost(0.2)
        val yRange = yMax - yMin

        fun yForLevel(level: Double): Float =
            plotBottomY - ((level - yMin) / yRange * drawHeight).toFloat()

        fun xForMinute(min: Double): Float =
            paddingLeft + (min / windowMinutes * drawWidth).toFloat()

        // Y-Axis Labels & Grid
        val axisPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 100
            textSize = labelTextSize
            isAntiAlias = true
        }

        listOf(0.0, 1.0, 2.0, 3.0, 4.0).forEach { step ->
            if (step in yMin..yMax) {
                val y = yForLevel(step)
                drawLine(gridColor, Offset(paddingLeft, y), Offset(width - paddingRight, y))
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f m".format(step),
                    paddingLeft - (if (isTablet) 6.dp else 5.dp).toPx(),
                    y + labelTextSize / 3,
                    axisPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
                )
            }
        }

        // MHW / MNW Reference Lines
        mhw?.let {
            val y = yForLevel(it)
            drawLine(Color.White.copy(alpha = 0.3f), Offset(paddingLeft, y), Offset(width - paddingRight, y), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawContext.canvas.nativeCanvas.drawText(
                "MHW",
                (if (isTablet) 3.dp else 2.dp).toPx(),
                y - (if (isTablet) 5.dp else 4.dp).toPx(),
                axisPaint.apply {
                    alpha = 180
                    textAlign = android.graphics.Paint.Align.LEFT
                }
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%.2f m".format(it),
                width - (if (isTablet) 3.dp else 2.dp).toPx(),
                y - (if (isTablet) 5.dp else 4.dp).toPx(),
                axisPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
            )
        }
        mnw?.let {
            val y = yForLevel(it)
            drawLine(Color.White.copy(alpha = 0.3f), Offset(paddingLeft, y), Offset(width - paddingRight, y), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            drawContext.canvas.nativeCanvas.drawText(
                "MNW",
                (if (isTablet) 3.dp else 2.dp).toPx(),
                y + labelTextSize + (if (isTablet) 3.dp else 2.dp).toPx(),
                axisPaint.apply { textAlign = android.graphics.Paint.Align.LEFT }
            )
            drawContext.canvas.nativeCanvas.drawText(
                "%.2f m".format(it),
                width - (if (isTablet) 3.dp else 2.dp).toPx(),
                y + labelTextSize + (if (isTablet) 3.dp else 2.dp).toPx(),
                axisPaint.apply { textAlign = android.graphics.Paint.Align.RIGHT }
            )
        }

        // X-Axis (Time & Date)
        val hourPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 150
            textSize = labelTextSize
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val datePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            alpha = 220
            textSize = dateTextSize
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }

        var tick = windowStart.withMinute(0).withSecond(0).withNano(0)
        while (tick.isBefore(windowStart)) tick = tick.plusHours(1)

        var lastDate: LocalDate? = null
        while (!tick.isAfter(windowEnd)) {
            val minutes = java.time.Duration.between(windowStart, tick).toMinutes().toDouble()
            val x = xForMinute(minutes)

            if (tick.hour % 4 == 0) {
                drawLine(gridColor, Offset(x, paddingTop), Offset(x, plotBottomY))
                drawContext.canvas.nativeCanvas.drawText(
                    tick.format(DateTimeFormatter.ofPattern("HH")),
                    x,
                    plotBottomY + (if (isTablet) 20.dp else 16.dp).toPx(),
                    hourPaint
                )

                if (lastDate == null || tick.toLocalDate() != lastDate) {
                    drawContext.canvas.nativeCanvas.drawText(
                        tick.format(DateTimeFormatter.ofPattern("dd.MM.")),
                        x,
                        plotBottomY + (if (isTablet) 42.dp else 34.dp).toPx(),
                        datePaint
                    )
                    lastDate = tick.toLocalDate()
                }
            }
            tick = tick.plusHours(1)
        }

        drawContext.canvas.nativeCanvas.drawText(
            "Gesetzliche Zeit",
            width / 2,
            height - (if (isTablet) 6.dp else 5.dp).toPx(),
            hourPaint.apply { alpha = 80 }
        )

        // The Curve
        val nowMinutes = java.time.Duration.between(windowStart, now).toMinutes().toDouble()
        val step = 2
        val pastPath = Path()
        val futurePath = Path()

        fun getLevelAt(min: Double): Double {
            val beforeIdx = pts.indexOfLast { it.first <= min }
            return if (beforeIdx != -1 && beforeIdx < pts.size - 1) {
                val before = pts[beforeIdx]
                val after = pts[beforeIdx + 1]
                val progress = (min - before.first) / (after.first - before.first)
                val cosInterp = (1 - kotlin.math.cos(progress * Math.PI)) / 2.0
                before.second + (after.second - before.second) * cosInterp
            } else if (beforeIdx != -1) pts[beforeIdx].second else pts.firstOrNull()?.second ?: 0.0
        }

        // Draw past segment
        val pastPixels = (nowMinutes / windowMinutes * drawWidth).toInt().coerceIn(0, drawWidth.toInt())
        if (nowMinutes > 0) {
            for (xPx in 0..pastPixels step step) {
                val minute = (xPx.toDouble() / drawWidth) * windowMinutes
                val xPos = paddingLeft + xPx.toFloat()
                val yPos = yForLevel(getLevelAt(minute))
                if (xPx == 0) pastPath.moveTo(xPos, yPos) else pastPath.lineTo(xPos, yPos)
            }
            // Ensure path reaches exactly the 'now' point
            val yNow = yForLevel(getLevelAt(nowMinutes.coerceIn(0.0, windowMinutes)))
            pastPath.lineTo(xForMinute(nowMinutes.coerceIn(0.0, windowMinutes)), yNow)
        }

        // Draw future segment
        if (nowMinutes < windowMinutes) {
            val yNow = yForLevel(getLevelAt(nowMinutes.coerceIn(0.0, windowMinutes)))
            futurePath.moveTo(xForMinute(nowMinutes.coerceIn(0.0, windowMinutes)), yNow)

            // Adjust loop start to avoid overlap and gaps
            val startFutureMin = nowMinutes.coerceAtLeast(0.0)
            val startX = (startFutureMin / windowMinutes * drawWidth).toInt()
            for (xPx in startX..drawWidth.toInt() step step) {
                val minute = (xPx.toDouble() / drawWidth) * windowMinutes
                if (minute <= nowMinutes) continue
                val xPos = paddingLeft + xPx.toFloat()
                val yPos = yForLevel(getLevelAt(minute))
                futurePath.lineTo(xPos, yPos)
            }
            // Ensure path reaches exactly the end
            futurePath.lineTo(paddingLeft + drawWidth, yForLevel(getLevelAt(windowMinutes)))
        }

        drawPath(
            pastPath,
            pastColor,
            style = Stroke(width = (if (isTablet) 3.5.dp else 3.dp).toPx(), cap = StrokeCap.Round)
        )
        drawPath(
            futurePath,
            futureColor,
            style = Stroke(width = (if (isTablet) 3.5.dp else 3.dp).toPx(), cap = StrokeCap.Round)
        )

        // Points
        for ((minutes, level, type) in pts) {
            if (minutes < 0 || minutes > windowMinutes) continue
            val cx = xForMinute(minutes)
            val cy = yForLevel(level)
            drawCircle(
                if (minutes <= nowMinutes) pastColor else futureColor,
                (if (isTablet) 5.dp else 4.dp).toPx(),
                Offset(cx, cy)
            )
            drawCircle(Color(0xFF1B4E7A), (if (isTablet) 2.5.dp else 2.dp).toPx(), Offset(cx, cy))
        }

        // Now line
        if (nowMinutes in 0.0..windowMinutes) {
            val nowX = xForMinute(nowMinutes)
            drawLine(nowColor, Offset(nowX, paddingTop), Offset(nowX, plotBottomY), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
        }
    }
}

@Composable
fun TideBasisTile(label: String, value: String) {
    val isTablet = currentAdaptiveLayout().isTablet
    Column {
        Text(label, color = Color.White.copy(alpha = 0.6f), fontSize = if (isTablet) 15.sp else 12.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTablet) 24.sp else 20.sp)
    }
}

@Composable
fun TideEventTile(type: String, time: String, height: String, diff: String) {
    val isTablet = currentAdaptiveLayout().isTablet
    Surface(
        modifier = Modifier
            .width(if (isTablet) 160.dp else 130.dp)
            .height(if (isTablet) 152.dp else 110.dp),
        shape = RoundedCornerShape(if (isTablet) 20.dp else 16.dp),
        color = Color.White.copy(alpha = 0.08f),
        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(if (isTablet) 16.dp else 12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isTablet) 30.dp else 24.dp)
                    .clip(CircleShape)
                    .background(if (type == "HW") Color(0xFF4FC3F7).copy(alpha = 0.15f) else Color(0xFF9575CD).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (type == "HW") Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                    null,
                    tint = if (type == "HW") Color(0xFF4FC3F7) else Color(0xFF9575CD),
                    modifier = Modifier.size(if (isTablet) 17.dp else 14.dp)
                )
            }
            Spacer(modifier = Modifier.height(if (isTablet) 10.dp else 8.dp))
            Text(time, color = Color.White, fontWeight = FontWeight.Bold, fontSize = if (isTablet) 24.sp else 20.sp)
            Text(height, color = Color.White.copy(alpha = 0.8f), fontSize = if (isTablet) 15.sp else 12.sp)
            Text(diff, color = Color.White.copy(alpha = 0.5f), fontSize = if (isTablet) 12.sp else 10.sp)
        }
    }
}

@Composable
fun WeatherSkeleton() {
    val isTablet = currentAdaptiveLayout().isTablet
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth().alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(
            if (isTablet) TabletLayoutTokens.SectionSpacing else 16.dp
        )
    ) {
        // Hero Card Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTablet) 272.dp else 220.dp)
                .clip(
                    RoundedCornerShape(
                        if (isTablet) TabletLayoutTokens.CardCornerRadius else 20.dp
                    )
                )
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Hourly Forecast Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTablet) 160.dp else 130.dp)
                .clip(
                    RoundedCornerShape(
                        if (isTablet) TabletLayoutTokens.CardCornerRadius else 20.dp
                    )
                )
                .background(Color.White.copy(alpha = 0.12f))
        )

        // Wind Map Skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTablet) 372.dp else 300.dp)
                .clip(
                    RoundedCornerShape(
                        if (isTablet) TabletLayoutTokens.CardCornerRadius else 20.dp
                    )
                )
                .background(Color.White.copy(alpha = 0.12f))
        )
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val isTablet = currentAdaptiveLayout().isTablet
    val shape = RoundedCornerShape(if (isTablet) TabletLayoutTokens.CardCornerRadius else 20.dp)
    val cardModifier = if (isTablet) {
        modifier.shadow(
            elevation = 2.dp,
            shape = shape,
            clip = false,
            ambientColor = Color.Black.copy(alpha = 0.06f),
            spotColor = Color.Black.copy(alpha = 0.08f)
        )
    } else {
        modifier
    }

    Surface(
        modifier = cardModifier,
        shape = shape,
        color = Color.White.copy(alpha = 0.12f),
        border = BorderStroke(if (isTablet) 1.dp else 0.5.dp, Color.White.copy(alpha = 0.2f)),
        shadowElevation = 0.dp
    ) {
        content()
    }
}

@Composable
fun StationSelectionDialog(
    stations: List<TideStationData>,
    selectedStation: TideStationData?,
    onDismiss: () -> Unit,
    onStationSelected: (TideStationData) -> Unit
) {
    val adaptiveLayout = currentAdaptiveLayout()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !adaptiveLayout.isTablet)
    ) {
        GlassCard(
            modifier = Modifier
                .then(
                    if (adaptiveLayout.isTablet) {
                        Modifier
                            .widthIn(max = adaptiveLayout.overlayMaxWidth)
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 520.dp)
                    } else {
                        Modifier
                            .fillMaxWidth(0.92f)
                            .heightIn(max = 420.dp)
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    if (adaptiveLayout.isTablet) TabletLayoutTokens.CardPadding else 16.dp
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Standort auswählen",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (adaptiveLayout.isTablet) 22.sp else 18.sp
                        )
                        Text(
                            "Ostfriesische Inseln",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = if (adaptiveLayout.isTablet) 15.sp else 12.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = if (adaptiveLayout.isTablet) Modifier.size(48.dp) else Modifier
                    ) {
                        Icon(
                            Icons.Default.Close,
                            null,
                            tint = Color.White,
                            modifier = if (adaptiveLayout.isTablet) {
                                Modifier.size(TabletLayoutTokens.StandardIconSize)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(if (adaptiveLayout.isTablet) 16.dp else 12.dp))
                val sortedStations = remember(stations) {
                    stations.sortedBy { it.gaugeLabel ?: it.area }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(if (adaptiveLayout.isTablet) 6.dp else 4.dp)
                ) {
                    items(sortedStations) { station ->
                        val isSelected = station == selectedStation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (adaptiveLayout.isTablet) {
                                        Modifier.heightIn(min = 52.dp)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clip(RoundedCornerShape(if (adaptiveLayout.isTablet) 14.dp else 10.dp))
                                .background(if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { onStationSelected(station) }
                                .padding(
                                    vertical = if (adaptiveLayout.isTablet) 12.dp else 10.dp,
                                    horizontal = if (adaptiveLayout.isTablet) 14.dp else 10.dp
                                ),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(
                                                width = if (adaptiveLayout.isTablet) 5.dp else 4.dp,
                                                height = if (adaptiveLayout.isTablet) 20.dp else 16.dp
                                            )
                                            .background(
                                                Color(0xFF4FC3F7),
                                                RoundedCornerShape(if (adaptiveLayout.isTablet) 3.dp else 2.dp)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(if (adaptiveLayout.isTablet) 10.dp else 8.dp))
                                }
                                Text(
                                    text = station.gaugeLabel ?: station.area,
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp
                                )
                            }
                            val temp = station.temperature?.toInt() ?: 17
                            Text(
                                text = "$temp°",
                                color = if (isSelected) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.8f),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = if (adaptiveLayout.isTablet) 18.sp else 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// iconToEmoji lives in WeatherOverlayScreen.kt (same package) and is shared with the Nauti in-chat
// widget. The copy that used to sit here differed only in its fallback, which quietly turned every
// unrecognised sky into a sun.

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
        val precipProbs = hours.mapNotNull { it.precipitationProbability }
        val icons = hours.mapNotNull { it.icon }
        val dominantIcon = icons.groupBy { it }.maxByOrNull { it.value.size }?.key ?: "clear-day"

        val windSpeeds = hours.mapNotNull { it.windSpeed }
        val windGusts = hours.mapNotNull { it.windGustSpeed }
        val maxWindKn = if (windSpeeds.isNotEmpty()) (windSpeeds.maxOrNull()!! / 1.852).toInt() else 0
        val maxGustKn = if (windGusts.isNotEmpty()) (windGusts.maxOrNull()!! / 1.852).toInt() else 0

        val totalPrecip = hours.mapNotNull { it.precipitation }.sum()
        val visibilities = hours.mapNotNull { it.visibility }
        val minVisibility = if (visibilities.isNotEmpty()) visibilities.minOrNull() else null

        val humidities = hours.mapNotNull { it.relativeHumidity }
        val avgHumidity = if (humidities.isNotEmpty()) humidities.average().toInt() else null

        val sunshines = hours.mapNotNull { it.sunshine }
        val totalSunshine = if (sunshines.isNotEmpty()) sunshines.sum() else null

        DailyForecast(
            dayLabel = dayLabel,
            condition = dominantIcon,
            highTemp = temps.maxOrNull()?.toInt() ?: 0,
            lowTemp = temps.minOrNull()?.toInt() ?: 0,
            maxWind = maxWindKn,
            maxGust = maxGustKn,
            totalPrecip = totalPrecip,
            maxPrecipProb = precipProbs.maxOrNull() ?: 0,
            minVisibility = minVisibility,
            avgHumidity = avgHumidity,
            totalSunshine = totalSunshine
        )
    }
}
