package com.example.trnberechnung.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.trnberechnung.logic.DecisionLogic
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.model.TideEvent
import com.example.trnberechnung.model.toModel
import com.example.trnberechnung.repository.TideRepository
import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.model.ChecklistItem
import com.example.trnberechnung.model.ChecklistItemType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TideViewModel(
    private val repository: TideRepository
) : ViewModel() {

    private val _data = MutableStateFlow<List<TideStationData>>(emptyList())
    val data: StateFlow<List<TideStationData>> = _data

    val allLogs: StateFlow<List<LogbookEntry>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCrew: StateFlow<List<CrewMember>> = repository.allCrew
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedStation = MutableStateFlow<TideStationData?>(null)
    val selectedStation: StateFlow<TideStationData?> = _selectedStation

    private val _currentWeather = MutableStateFlow<WeatherDto?>(null)
    val currentWeather: StateFlow<WeatherDto?> = _currentWeather

    private val _forecastData = MutableStateFlow<List<WeatherDto>>(emptyList())
    val forecastData: StateFlow<List<WeatherDto>> = _forecastData

    private val _weatherLoading = MutableStateFlow(false)
    val weatherLoading: StateFlow<Boolean> = _weatherLoading

    private val _weatherError = MutableStateFlow<String?>(null)
    val weatherError: StateFlow<String?> = _weatherError

    private val _decision = MutableStateFlow<String>("Keine Bewertung")
    val decision: StateFlow<String> = _decision

    private val _currentTideEvents = MutableStateFlow<List<TideEvent>>(emptyList())
    val currentTideEvents: StateFlow<List<TideEvent>> = _currentTideEvents

    private val _nextHW = MutableStateFlow<TideEvent?>(null)
    val nextHW: StateFlow<TideEvent?> = _nextHW

    private val _nextNW = MutableStateFlow<TideEvent?>(null)
    val nextNW: StateFlow<TideEvent?> = _nextNW

    private val _allStations = MutableStateFlow<List<TideStationData>>(emptyList())
    val allStations: StateFlow<List<TideStationData>> = _allStations

    private val _tideLoading = MutableStateFlow(false)
    val tideLoading: StateFlow<Boolean> = _tideLoading

    init {

        _allStations.value = LOCAL_HARBOURS
    }

    fun loadData() {
        viewModelScope.launch {
            _tideLoading.value = true
            try {
                val apiData = repository.getDataFromApi()
                _data.value = apiData

                if (apiData.isNotEmpty()) {

                    val merged = LOCAL_HARBOURS.map { harbour ->

                        val bshMatch = apiData.minByOrNull { bsh ->
                            haversineKm(harbour.latitude, harbour.longitude, bsh.latitude, bsh.longitude)
                        }
                        if (bshMatch != null && haversineKm(harbour.latitude, harbour.longitude, bshMatch.latitude, bshMatch.longitude) < 20.0) {

                            harbour.copy(
                                events = bshMatch.events,
                                meanHighWater = bshMatch.meanHighWater,
                                meanLowWater = bshMatch.meanLowWater,
                                gaugeZeroNhn = bshMatch.gaugeZeroNhn,
                                chartDatumGauge = bshMatch.chartDatumGauge
                            )
                        } else {
                            harbour
                        }
                    }
                    _allStations.value = merged
                }
            } catch (e: Exception) {

                try {
                    val localData = repository.getDataFromDatabase().map { it.toModel() }
                    if (localData.isNotEmpty()) {
                        _data.value = localData
                    }
                } catch (_: Exception) {}

            } finally {
                _tideLoading.value = false
            }
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        return r * 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    fun selectStation(station: TideStationData) {
        _selectedStation.value = station
        _tideLoading.value = true

        if (station.events.isNotEmpty()) {

            updateTideEvents(station)
            _tideLoading.value = false
            loadWeatherForLocation(station.latitude, station.longitude)
        } else {

            viewModelScope.launch {
                try {
                    val apiData = repository.getDataFromApi()
                    if (apiData.isNotEmpty()) {

                        val nearest = apiData.minByOrNull { bsh ->
                            haversineKm(station.latitude, station.longitude, bsh.latitude, bsh.longitude)
                        }
                        if (nearest != null && haversineKm(station.latitude, station.longitude, nearest.latitude, nearest.longitude) < 50.0) {
                            val enriched = station.copy(
                                events = nearest.events,
                                meanHighWater = nearest.meanHighWater,
                                meanLowWater = nearest.meanLowWater,
                                gaugeZeroNhn = nearest.gaugeZeroNhn,
                                chartDatumGauge = nearest.chartDatumGauge
                            )
                            _selectedStation.value = enriched
                            updateTideEvents(enriched)

                            _allStations.value = _allStations.value.map { s ->
                                if (s.gaugeLabel == station.gaugeLabel) enriched else s
                            }
                        }
                    }
                } catch (_: Exception) {

                } finally {
                    _tideLoading.value = false
                }
                loadWeatherForLocation(station.latitude, station.longitude)
            }
        }
    }

    private fun updateTideEvents(station: TideStationData) {
        _currentTideEvents.value = station.events

        val now = LocalDateTime.now()
        val eventsWithTime = station.events.mapNotNull { event ->
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
                Pair(event, dt)
            } catch (_: Exception) { null }
        }

        val nextHwEvent = eventsWithTime
            .filter { it.first.type == "HW" && it.second.isAfter(now) }
            .minByOrNull { it.second }?.first

        val nextNwEvent = eventsWithTime
            .filter { it.first.type == "NW" && it.second.isAfter(now) }
            .minByOrNull { it.second }?.first

        _nextHW.value = nextHwEvent
        _nextNW.value = nextNwEvent
    }

    fun loadWeatherForLocation(lat: Double = 53.87, lon: Double = 8.70) {
        _weatherLoading.value = true
        _weatherError.value = null

        viewModelScope.launch {
            try {
                val weather = repository.getWeatherData(lat, lon)

                _weatherError.value = if (weather == null) "Keine Wetterdaten verfügbar" else null

                val today = LocalDate.now()
                val endDate = today.plusDays(7)
                val fmt = DateTimeFormatter.ISO_LOCAL_DATE

                val forecast = repository.getForecastData(
                    lat,
                    lon,
                    today.format(fmt),
                    endDate.format(fmt)
                )

                _forecastData.value = forecast

                val mergedWeather = mergeWithForecast(weather, forecast)
                _currentWeather.value = mergedWeather

                val tide = _selectedStation.value
                    ?: com.example.trnberechnung.model.TideStationData(
                        area = "Allgemein", region = "Nordsee",
                        latitude = lat, longitude = lon,
                        waterLevel = null, meanHighWater = null, meanLowWater = null,
                        forecastTimestamp = "", events = emptyList()
                    )
                val result = DecisionLogic.calculateDecision(tide, mergedWeather)
                _decision.value = result

            } catch (e: Exception) {
                _weatherError.value = "Wetter-Fehler: ${e.message}"
            } finally {
                _weatherLoading.value = false
            }
        }
    }

    private fun mergeWithForecast(current: WeatherDto?, forecast: List<WeatherDto>): WeatherDto? {
        if (current == null) {

            return forecast.firstOrNull()
        }
        if (forecast.isEmpty()) return current

        val now = java.time.LocalDateTime.now()
        val nearest = forecast.minByOrNull { entry ->
            try {
                val ts = java.time.LocalDateTime.parse(
                    entry.timestamp?.replace("+00:00", "")?.replace("Z", "") ?: "",
                    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
                )
                kotlin.math.abs(java.time.Duration.between(now, ts).toMinutes())
            } catch (_: Exception) {
                Long.MAX_VALUE
            }
        } ?: return current

        return current.copy(
            windSpeed = current.windSpeed ?: nearest.windSpeed,
            windDirection = current.windDirection ?: nearest.windDirection,
            windGustSpeed = current.windGustSpeed ?: nearest.windGustSpeed,
            condition = current.condition ?: nearest.condition,
            icon = current.icon ?: nearest.icon,
            cloudCover = current.cloudCover ?: nearest.cloudCover,
            pressureMsl = current.pressureMsl ?: nearest.pressureMsl,
            relativeHumidity = current.relativeHumidity ?: nearest.relativeHumidity,
            precipitation = current.precipitation ?: nearest.precipitation,
            visibility = current.visibility ?: nearest.visibility,
            sunshine = current.sunshine ?: nearest.sunshine,
            dewPoint = current.dewPoint ?: nearest.dewPoint,
            solar = current.solar ?: nearest.solar,
            precipitationProbability = current.precipitationProbability ?: nearest.precipitationProbability,
            temperature = current.temperature ?: nearest.temperature
        )
    }

    fun saveCrew(member: CrewMember) {
        viewModelScope.launch { repository.insertCrew(member) }
    }

    fun updateCrew(member: CrewMember) {
        viewModelScope.launch { repository.updateCrew(member) }
    }

    fun deleteCrew(member: CrewMember) {
        viewModelScope.launch { repository.deleteCrew(member) }
    }

    fun saveLog(log: LogbookEntry) {

        val prevAnkunft: String = allLogs.value.firstOrNull()?.details?.let { details ->
            val segs = if ("|" in details) details.split("|") else details.split("\n", ";")
            segs.firstOrNull { it.startsWith("bsb:") }?.substringAfter(":")?.trim().orEmpty()
        }.orEmpty()
        val finalLog = if (prevAnkunft.isNotBlank() && !log.details.contains("bsa:")) {
            val newDetails = if (log.details.isBlank()) "bsa:$prevAnkunft"
                else "${log.details}|bsa:$prevAnkunft"
            log.copy(details = newDetails)
        } else {
            log
        }
        viewModelScope.launch { repository.insertLog(finalLog) }
    }

    fun updateLog(log: LogbookEntry) {
        viewModelScope.launch { repository.updateLog(log) }
    }

    fun deleteLog(log: LogbookEntry) {
        viewModelScope.launch { repository.deleteLog(log) }
    }

    fun deleteAllLogs() {
        viewModelScope.launch { repository.deleteAllLogs() }
    }

    fun getChecklistForTrip(tripId: Int): StateFlow<List<ChecklistItem>> =
        repository.getChecklistForTrip(tripId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun initializeChecklist(tripId: Int) {
        viewModelScope.launch {
            if (repository.checklistCountForTrip(tripId) == 0) {
                repository.insertChecklistItems(defaultChecklist(tripId))
            }
        }
    }

    fun updateChecklistItem(item: ChecklistItem) {
        viewModelScope.launch { repository.updateChecklistItem(item) }
    }

    private fun defaultChecklist(tripId: Int): List<ChecklistItem> {
        var order = 0
        fun check(category: String, label: String) = ChecklistItem(
            tripId = tripId, category = category, label = label,
            type = ChecklistItemType.CHECK.name, sortOrder = order++
        )
        fun text(category: String, label: String) = ChecklistItem(
            tripId = tripId, category = category, label = label,
            type = ChecklistItemType.TEXT.name, sortOrder = order++
        )
        return listOf(
            check("Crew", "Einweisung der Crew"),
            check("Crew", "Rettungswesten angelegt"),
            check("Crew", "Mann-über-Bord-Manöver besprochen"),
            check("Boot", "Treibstoff ausreichend"),
            check("Boot", "Bilge kontrolliert"),
            check("Boot", "Aufbauhöhe relevant"),
            text("Boot", "Aufbauhöhe (m)"),
            check("Technik", "Motor getestet"),
            check("Technik", "Batteriestand geprüft"),
            check("Technik", "Navigationslichter funktionsfähig"),
            check("Navigation", "Seekarte/Plotter aktuell"),
            check("Navigation", "Route geplant"),
            check("Wetter", "Wetterbericht eingeholt"),
            text("Wetter", "Bemerkungen")
        )
    }

    companion object {
        /** Lokale Hafen-Liste – nur echte Wattenmeer-Häfen innerhalb des Routing-Grids */
        val LOCAL_HARBOURS = listOf(
            // ── Deutschland – Ostfriesische Inseln (Häfen) ──
            harbour("Borkum", 53.5572, 6.7525),
            harbour("Juist", 53.6732, 7.0015),
            harbour("Norderney", 53.7012, 7.1585),
            harbour("Baltrum", 53.7215, 7.3715),
            harbour("Langeoog", 53.7285, 7.5095),
            harbour("Spiekeroog", 53.7645, 7.6955),
            harbour("Wangerooge", 53.7852, 7.8965),
            // ── Deutschland – Festlandküste (Häfen) ──
            harbour("Emden", 53.3382, 7.1945),
            harbour("Norddeich", 53.6265, 7.1615),
            harbour("Nessmersiel", 53.6865, 7.3615),
            harbour("Dornumersiel", 53.6865, 7.4785),
            harbour("Bensersiel", 53.6785, 7.5705),
            harbour("Neuharlingersiel", 53.7015, 7.7055),
            harbour("Harlesiel", 53.7125, 7.8105),
            harbour("Horumersiel", 53.6862, 8.0195),
            harbour("Hooksiel", 53.6425, 8.0825),
            harbour("Dangast", 53.4472, 8.1175),
            harbour("Wilhelmshaven", 53.5142, 8.1465),
            // ── Niederlande – Wattenmeer (Häfen) ──
            harbour("Delfzijl", 53.3305, 6.9335),
            harbour("Termunterzijl", 53.3032, 7.0405),
            harbour("Eemshaven", 53.4445, 6.8365)
        )

        private fun harbour(name: String, lat: Double, lon: Double) = TideStationData(
            area = name,
            region = "Nordsee",
            latitude = lat,
            longitude = lon,
            waterLevel = null,
            meanHighWater = null,
            meanLowWater = null,
            gaugeLabel = name,
            forecastTimestamp = "",
            events = emptyList()
        )
    }
}