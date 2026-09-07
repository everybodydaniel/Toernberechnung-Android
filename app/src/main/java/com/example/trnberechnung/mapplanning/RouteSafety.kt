package com.example.trnberechnung.mapplanning

import java.time.ZonedDateTime

data class MarineWeatherAssessment(
    val windKnots: Double,
    val gustKnots: Double,
    val visibilityKilometers: Double,
    val precipitationChancePercent: Double,
    val precipitationMillimeters: Double,
)

object WeatherSafetyEvaluator {
    fun evaluate(assessment: MarineWeatherAssessment?): WeatherStatus {
        if (assessment == null) return WeatherStatus.UNVOLLSTAENDIG

        return when {
            assessment.windKnots >= 28 ||
                assessment.gustKnots >= 34 ||
                assessment.visibilityKilometers < 1 ->
                WeatherStatus.NICHT_BEFAHRBAR

            assessment.windKnots >= 20 ||
                assessment.gustKnots >= 27 ||
                assessment.visibilityKilometers < 5 ||
                assessment.precipitationChancePercent >= 60 ||
                assessment.precipitationMillimeters >= 3 ->
                WeatherStatus.EINGESCHRAENKT

            else -> WeatherStatus.BEFAHRBAR
        }
    }

    /**
     * Selects the nearest hourly sample for every arrival time. Every waypoint
     * must have a sample no farther than 90 minutes away.
     */
    fun evaluateRoute(
        waypointArrivals: List<ZonedDateTime>,
        hourlyAssessments: List<Pair<ZonedDateTime, MarineWeatherAssessment>>,
    ): WeatherStatus {
        if (waypointArrivals.isEmpty()) return WeatherStatus.UNVOLLSTAENDIG

        val statuses =
            waypointArrivals.map { arrival ->
                val nearest =
                    hourlyAssessments.minByOrNull { (time) ->
                        kotlin.math.abs(
                            java.time.Duration.between(arrival.toInstant(), time.toInstant())
                                .toMinutes(),
                        )
                    }
                val differenceMinutes =
                    nearest?.let { (time) ->
                        kotlin.math.abs(
                            java.time.Duration.between(arrival.toInstant(), time.toInstant())
                                .toMinutes(),
                        )
                    }
                if (nearest == null || differenceMinutes == null || differenceMinutes > 90) {
                    WeatherStatus.UNVOLLSTAENDIG
                } else {
                    evaluate(nearest.second)
                }
            }

        return statuses.reduce(::combineWeatherStatus)
    }

    fun evaluateAll(assessments: List<MarineWeatherAssessment?>): WeatherStatus {
        if (assessments.isEmpty()) return WeatherStatus.UNVOLLSTAENDIG
        return assessments.map(::evaluate).reduce(::combineWeatherStatus)
    }

    private fun combineWeatherStatus(
        first: WeatherStatus,
        second: WeatherStatus,
    ): WeatherStatus =
        listOf(first, second).maxBy(WeatherStatus::precedence)

    fun checkWindAgainstCurrent(
        assessment: MarineWeatherAssessment?,
        isSeegat: Boolean,
    ): Boolean {
        if (assessment == null || !isSeegat) return false
        // Wind gegen Strom: Wenn Wind > 4 Bft (ca. 11-16 kn) und gegenläufig
        // Da wir keine Strömungsrichtung haben, warnen wir pauschal ab 15kn im Seegat
        return assessment.windKnots >= 15.0
    }
}

data class ClearanceSample(
    val waypointName: String,
    val clearanceMeters: Double?,
    val isValid: Boolean = true,
    val waterLevelQuality: WaterLevelQuality = WaterLevelQuality.LOCAL_OFFICIAL,
    val anchoredHighWater: ZonedDateTime? = null,
    val arrivalTime: ZonedDateTime? = null,
    val safeWindowEnd: ZonedDateTime? = null,
)

object UnderKeelSafetyEvaluator {
    data class EvaluationResult(
        val status: RouteStatus,
        val reason: SafetyFailureReason,
        val message: String? = null
    )

    fun evaluate(
        samples: List<ClearanceSample>,
        safetyMarginMeters: Double,
        allLegsValid: Boolean = true,
    ): EvaluationResult {
        require(safetyMarginMeters >= 0) {
            "Der Sicherheitsabstand darf nicht negativ sein."
        }
        if (samples.isEmpty()) {
            return EvaluationResult(RouteStatus.UNVOLLSTAENDIG, SafetyFailureReason.UNAVAILABLE_DATA)
        }

        var worstReason = SafetyFailureReason.NONE

        val statuses =
            samples.map { sample ->
                val status = when {
                    sample.clearanceMeters != null && sample.clearanceMeters <= -0.01 -> {
                        worstReason = SafetyFailureReason.INSUFFICIENT_DEPTH
                        RouteStatus.NICHT_BEFAHRBAR
                    }

                    !sample.isValid || sample.clearanceMeters == null -> {
                        if (worstReason == SafetyFailureReason.NONE) worstReason = SafetyFailureReason.UNAVAILABLE_DATA
                        RouteStatus.UNVOLLSTAENDIG
                    }

                    sample.waterLevelQuality == WaterLevelQuality.STALE ||
                        sample.waterLevelQuality == WaterLevelQuality.OUTSIDE_FORECAST_HORIZON ||
                        sample.waterLevelQuality == WaterLevelQuality.UNAVAILABLE -> {
                        if (worstReason == SafetyFailureReason.NONE) worstReason = SafetyFailureReason.UNAVAILABLE_DATA
                        RouteStatus.UNVOLLSTAENDIG
                    }

                    sample.clearanceMeters < safetyMarginMeters -> {
                        if (worstReason == SafetyFailureReason.NONE) worstReason = SafetyFailureReason.INSUFFICIENT_DEPTH
                        RouteStatus.EINGESCHRAENKT
                    }

                    sample.waterLevelQuality == WaterLevelQuality.MANUAL ||
                        sample.waterLevelQuality == WaterLevelQuality.CONFIRMED_COMPARISON ->
                        RouteStatus.EINGESCHRAENKT

                    else -> RouteStatus.BEFAHRBAR
                }
                status
            }

        val maxStatus = statuses.maxBy(RouteStatus::precedence)

        // Zusätzliche Prüfung: Wenn die Ankunft am Zielhafen nach dem Ende des sicheren Fensters liegt
        val lastSample = samples.lastOrNull()
        val arrival = lastSample?.arrivalTime
        val windowEnd = lastSample?.safeWindowEnd

        val (finalStatus, finalReason) = if (arrival != null && windowEnd != null && arrival.isAfter(windowEnd.minusMinutes(5))) {
            val status = if (maxStatus.precedence < RouteStatus.EINGESCHRAENKT.precedence) RouteStatus.EINGESCHRAENKT else maxStatus
            status to SafetyFailureReason.TIME_PRESSURE
        } else {
            maxStatus to worstReason
        }

        val constrainedStatus = if (!allLegsValid && finalStatus.precedence < RouteStatus.UNVOLLSTAENDIG.precedence) {
            if (samples.any { it.clearanceMeters != null }) finalStatus else RouteStatus.UNVOLLSTAENDIG
        } else {
            finalStatus
        }

        return EvaluationResult(constrainedStatus, finalReason)
    }
}

object RouteStatusEvaluator {
    fun combine(
        tidalStatus: RouteStatus,
        weatherStatus: WeatherStatus,
    ): RouteStatus {
        val normalizedWeather =
            when (weatherStatus) {
                WeatherStatus.BEFAHRBAR -> RouteStatus.BEFAHRBAR
                WeatherStatus.EINGESCHRAENKT -> RouteStatus.EINGESCHRAENKT
                WeatherStatus.NICHT_BEFAHRBAR -> RouteStatus.NICHT_BEFAHRBAR
                WeatherStatus.UNVOLLSTAENDIG -> RouteStatus.UNVOLLSTAENDIG
            }
        return listOf(tidalStatus, normalizedWeather).maxBy(RouteStatus::precedence)
    }
}

data class RouteAssessmentInput(
    val request: RoutePlanningRequest,
    val routeGeometry: List<GeoPoint>,
    val routeMetrics: RouteMetrics,
    val isScan: Boolean = false,
    val currentProvider: CurrentVectorProvider? = null
)

enum class SafetyFailureReason {
    INSUFFICIENT_DEPTH,
    WEATHER_DANGER,
    TIME_PRESSURE,
    UNAVAILABLE_DATA,
    NONE;

    fun toDisplayString(): String = when (this) {
        INSUFFICIENT_DEPTH -> "Wassertiefe für Tiefgang + Sicherheit zu gering."
        WEATHER_DANGER -> "Wetterbedingungen (Wind/Sicht) zu gefährlich."
        TIME_PRESSURE -> "Ankunft liegt außerhalb des sicheren Gezeitenfensters."
        UNAVAILABLE_DATA -> "Unzureichende Gezeiten- oder Wetterdaten für diese Route."
        NONE -> "Keine Einschränkungen erkannt."
    }
}

data class RouteSafetyAssessment(
    val expectedWaypointCount: Int,
    val clearanceSamples: List<ClearanceSample>,
    val allLegsValid: Boolean,
    val weatherStatus: WeatherStatus,
    val messages: List<String> = emptyList(),
    val maxWindKnots: Double? = null,
    val maxGustKnots: Double? = null,
    val criticalSample: ClearanceSample? = null,
) {
    val worstClearanceSample: ClearanceSample?
        get() = clearanceSamples.filter { it.clearanceMeters != null }
            .minByOrNull { it.clearanceMeters!! }

    val worstClearanceMeters: Double?
        get() = worstClearanceSample?.clearanceMeters

    val bottleneckSample: ClearanceSample?
        get() = criticalSample ?: worstClearanceSample
}

fun interface RouteAssessmentProvider {
    suspend fun assess(input: RouteAssessmentInput): RouteSafetyAssessment
}

fun interface ChartDepthProvider {
    fun depthMetersAt(point: GeoPoint): Double?
}

fun interface TideStationProvider {
    suspend fun getStations(): List<com.example.trnberechnung.model.TideStationData>
}

object IncompleteRouteAssessmentProvider : RouteAssessmentProvider {
    override suspend fun assess(input: RouteAssessmentInput): RouteSafetyAssessment =
        RouteSafetyAssessment(
            expectedWaypointCount = input.request.harbourChain.size,
            clearanceSamples = emptyList(),
            allLegsValid = false,
            weatherStatus = WeatherStatus.UNVOLLSTAENDIG,
            messages = listOf("Gezeiten-, Wasserstands- und Wetterdaten fehlen."),
        )
}

private val RouteStatus.precedence: Int
    get() =
        when (this) {
            RouteStatus.BEFAHRBAR -> 0
            RouteStatus.EINGESCHRAENKT -> 1
            RouteStatus.UNVOLLSTAENDIG -> 2
            RouteStatus.NICHT_BEFAHRBAR -> 3
        }

private val WeatherStatus.precedence: Int
    get() =
        when (this) {
            WeatherStatus.BEFAHRBAR -> 0
            WeatherStatus.EINGESCHRAENKT -> 1
            WeatherStatus.UNVOLLSTAENDIG -> 2
            WeatherStatus.NICHT_BEFAHRBAR -> 3
        }
