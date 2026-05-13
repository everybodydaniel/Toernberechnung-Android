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

    // Go / No-Go Ergebnis
    private val _decision = MutableStateFlow<String>("Keine Bewertung")
    val decision: StateFlow<String> = _decision

    // ── NEU: Aktuelle Gezeiten-Events für die ausgewählte Station ──
    private val _currentTideEvents = MutableStateFlow<List<TideEvent>>(emptyList())
    val currentTideEvents: StateFlow<List<TideEvent>> = _currentTideEvents

    // ── NEU: Nächstes HW/NW-Paar (für automatische Berechnung) ──
    private val _nextHW = MutableStateFlow<TideEvent?>(null)
    val nextHW: StateFlow<TideEvent?> = _nextHW

    private val _nextNW = MutableStateFlow<TideEvent?>(null)
    val nextNW: StateFlow<TideEvent?> = _nextNW

    // ── NEU: Alle BSH-Stationen (für Hafen-Dropdown) ──
    private val _allStations = MutableStateFlow<List<TideStationData>>(emptyList())
    val allStations: StateFlow<List<TideStationData>> = _allStations

    // ── NEU: Lade-Status für Tidendaten ──
    private val _tideLoading = MutableStateFlow(false)
    val tideLoading: StateFlow<Boolean> = _tideLoading

    init {
        // Sofort die lokalen Häfen laden (immer verfügbar)
        _allStations.value = LOCAL_HARBOURS
    }

    fun loadData() {
        viewModelScope.launch {
            _tideLoading.value = true
            try {
                val apiData = repository.getDataFromApi()
                _data.value = apiData
                // BSH-Stationen mit lokalen Häfen zusammenführen
                if (apiData.isNotEmpty()) {
                    // BSH-Stationen haben Gezeitendaten → bevorzugt
                    val merged = LOCAL_HARBOURS.map { harbour ->
                        // Suche passende BSH-Station in der Nähe (< 20km)
                        val bshMatch = apiData.minByOrNull { bsh ->
                            haversineKm(harbour.latitude, harbour.longitude, bsh.latitude, bsh.longitude)
                        }
                        if (bshMatch != null && haversineKm(harbour.latitude, harbour.longitude, bshMatch.latitude, bshMatch.longitude) < 20.0) {
                            // Hafen-Name behalten, aber Gezeitendaten von BSH übernehmen
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
                // Fallback: lokale DB oder hardcoded Häfen
                try {
                    val localData = repository.getDataFromDatabase().map { it.toModel() }
                    if (localData.isNotEmpty()) {
                        _data.value = localData
                    }
                } catch (_: Exception) {}
                // _allStations bleibt auf LOCAL_HARBOURS
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
            // Station hat bereits Gezeitendaten (z.B. durch merge)
            updateTideEvents(station)
            _tideLoading.value = false
            loadWeatherForLocation(station.latitude, station.longitude)
        } else {
            // Gezeitendaten aus BSH-API laden
            viewModelScope.launch {
                try {
                    val apiData = repository.getDataFromApi()
                    if (apiData.isNotEmpty()) {
                        // Finde nächste BSH-Station
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

                            // Aktualisiere auch die Station in allStations
                            _allStations.value = _allStations.value.map { s ->
                                if (s.gaugeLabel == station.gaugeLabel) enriched else s
                            }
                        }
                    }
                } catch (_: Exception) {
                    // API-Fehler – bleibt ohne Gezeitendaten
                } finally {
                    _tideLoading.value = false
                }
                loadWeatherForLocation(station.latitude, station.longitude)
            }
        }
    }

    /**
     * Setzt die Gezeiten-Events für eine Station und findet das nächste HW/NW-Paar.
     */
    private fun updateTideEvents(station: TideStationData) {
        _currentTideEvents.value = station.events

        val now = LocalDateTime.now()
        val eventsWithTime = station.events.mapNotNull { event ->
            try {
                // Robust parsing for BSH formats: "2026-04-29 18:07:00+02:00" or "2026-04-30T16:25:00Z"
                val cleanTs = event.timestamp
                    .replace("T", " ") // Handle 'T' separator
                    .replace(Regex("Z$"), "") // Handle Zulu time
                    .replace(Regex("\\+\\d{2}:\\d{2}$"), "") // Handle +HH:mm
                    .replace(Regex("\\+\\d{2}$"), "") // Handle +HH
                    .trim()
                
                // Try multiple patterns
                val dt = try {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (_: Exception) {
                    LocalDateTime.parse(cleanTs, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                Pair(event, dt)
            } catch (_: Exception) { null }
        }

        // Finde nächstes HW nach jetzt
        val nextHwEvent = eventsWithTime
            .filter { it.first.type == "HW" && it.second.isAfter(now) }
            .minByOrNull { it.second }?.first

        // Finde nächstes NW nach jetzt
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

                // Fehlende current_weather Felder aus nächster Forecast-Stunde ergänzen
                val mergedWeather = mergeWithForecast(weather, forecast)
                _currentWeather.value = mergedWeather

                // Go/No-Go Logik
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

    /**
     * Ergänzt fehlende (null) Felder im current_weather mit Werten aus
     * der nächstgelegenen Forecast-Stunde. BrightSky current_weather liefert
     * oft null für Wind, Böen, Sonnenschein, Niederschlagswsk. etc.
     */
    private fun mergeWithForecast(current: WeatherDto?, forecast: List<WeatherDto>): WeatherDto? {
        if (current == null) {
            // Kein current_weather – nehme die neueste Forecast-Stunde
            return forecast.firstOrNull()
        }
        if (forecast.isEmpty()) return current

        // Finde die nächstgelegene Forecast-Stunde zum aktuellen Zeitpunkt
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

        // Null-Felder aus Forecast auffüllen
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

    // Crew & Logs (unverändert)
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
        viewModelScope.launch { repository.insertLog(log) }
    }

    fun deleteLog(log: LogbookEntry) {
        viewModelScope.launch { repository.deleteLog(log) }
    }

    fun deleteAllLogs() {
        viewModelScope.launch { repository.deleteAllLogs() }
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