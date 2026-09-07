package com.example.trnberechnung.mapplanning

import com.example.trnberechnung.tides.nearestHighWater
import com.example.trnberechnung.tides.tideHeightAt
import com.example.trnberechnung.tides.weatherAt
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class AndroidRouteAssessmentProvider(
    private val tideStationProvider: TideStationProvider,
    private val chartDepthProvider: ChartDepthProvider,
    private val fairwayRouteResolver: FairwayRouteResolver?,
) : RouteAssessmentProvider {

    override suspend fun assess(input: RouteAssessmentInput): RouteSafetyAssessment {
        val stations = tideStationProvider.getStations()

        val fairwayResult = fairwayRouteResolver?.resolve(input.request)
        val samples =
            when (fairwayResult) {
                is FairwayRouteResult.Success -> fairwaySamples(fairwayResult.waypoints)
                else -> {
                    // Fallback auf Rohgeometrie, falls kein präzises Fahrwasser gefunden wurde.
                    // Das stellt sicher, dass für jede Route (z.B. Borkum) ein Fenster berechnet wird.
                    sampleRoute(input.routeGeometry)
                }
            }

        if (samples.isEmpty()) {
            return incompleteAssessment(
                input,
                "Die Routengeometrie enthält keine auswertbaren Wegpunkte.",
            )
        }

        val clearances = mutableListOf<ClearanceSample>()
        val weather = mutableListOf<MarineWeatherAssessment?>()

        val logTag = if (input.isScan) "PassageScan" else "RouteAssessment"
        android.util.Log.d(logTag, "Beurteilung gestartet: Abfahrt=${input.request.departure}, Boot=${input.request.boatSettings}")

        var arrival = input.request.departure
        for ((index, sample) in samples.withIndex()) {
            if (index > 0) {
                val previous = samples[index - 1]
                val legDistanceNm = sample.cumulativeDistanceNm - previous.cumulativeDistanceNm
                val course = RouteMetricsCalculator.initialBearingDegrees(previous.point, sample.point)
                val sog = input.currentProvider
                    ?.getCurrentVector(previous.point, arrival)
                    ?.let { current ->
                        VectorMath.calculateSogAndHeading(
                            input.request.boatSettings.speedKnots,
                            course,
                            current,
                        ).sogKnots
                    }
                    ?: input.request.boatSettings.speedKnots
                val legSeconds = legDistanceNm / sog.coerceAtLeast(0.1) * 3_600
                arrival = arrival.plusSeconds(legSeconds.toLong())
            }

            val station = stations.minByOrNull {
                RouteMetricsCalculator.haversineNm(sample.point, GeoPoint(it.latitude, it.longitude))
            }

            val tideHeight = station?.tideHeightAt(arrival)
            val correction = input.request.boatSettings.waterLevelCorrectionMeters
            val correctedTideHeight = tideHeight?.let { it + correction }

            // WICHTIG: Katalog-Tiefen (Häfen/Fahrwasser) haben Vorrang vor der SeaMask,
            // da die SeaMask im Watt oft zu grob ist (z.B. Trockenfallen als Tiefwasser erkennt).
            val chartDepth = if (sample.hasCatalogDepth && sample.chartDepthMeters != null) {
                sample.chartDepthMeters
            } else {
                chartDepthProvider.depthMetersAt(sample.point) ?: sample.chartDepthMeters
            }

            val totalWaterDepth = correctedTideHeight?.let { h -> chartDepth?.let { d -> d + h } }

            // WuK (Netto) = tatsächlicher Wasserstand unter dem Kiel (Gesamtwassertiefe - Tiefgang)
            val clearance = totalWaterDepth?.let { total ->
                total - input.request.boatSettings.draftMeters
            }

            val isPointValid = station != null && tideHeight != null && chartDepth != null
            val requiredMargin = input.request.boatSettings.safetyMarginMeters
            val isSafe = (clearance ?: -10.0) >= requiredMargin
            val requiredDepth = input.request.boatSettings.draftMeters + requiredMargin

            // Detailliertes Logging für die Analyse der Engstellen-Zeitberechnung
            if (sample.cumulativeDistanceNm == 0.0 || sample.cumulativeDistanceNm >= (input.routeMetrics.distanceNm - 0.1) || !isSafe || !isPointValid) {
                android.util.Log.d(logTag,
                    "Punkt-Check: ${station?.gaugeLabel ?: "Route"} | " +
                    "Dist=${String.format(Locale.US, "%.2f", sample.cumulativeDistanceNm)}sm | " +
                    "Fahrzeit=${java.time.Duration.between(input.request.departure, arrival).toMinutes()}min | " +
                    "Ankunft=${arrival.format(DateTimeFormatter.ofPattern("HH:mm"))} | " +
                    "ChartDepth=${chartDepth?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}m | " +
                    "Tide=${tideHeight?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}m | " +
                    "Gesamttiefe=${totalWaterDepth?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}m | " +
                    "WuK(Netto)=${clearance?.let { String.format(Locale.US, "%.2f", it) } ?: "N/A"}m | " +
                    "Erf.Tiefe=${String.format(Locale.US, "%.2f", requiredDepth)}m | " +
                    "Ok=$isSafe"
                )
            }

            clearances +=
                ClearanceSample(
                    waypointName = station?.gaugeLabel ?: station?.area ?: "Route",
                    clearanceMeters = clearance,
                    isValid = isPointValid,
                    waterLevelQuality =
                        if (isPointValid) {
                            WaterLevelQuality.LOCAL_OFFICIAL
                        } else {
                            WaterLevelQuality.UNAVAILABLE
                        },
                    anchoredHighWater = station?.nearestHighWater(arrival),
                    arrivalTime = arrival,
                    safeWindowEnd = null,
                )
            weather += station?.weatherAt(arrival)
        }

        val maxWind = weather.filterNotNull().maxByOrNull { it.windKnots }?.windKnots
        val maxGust = weather.filterNotNull().maxByOrNull { it.gustKnots }?.gustKnots

        val safetyMargin = input.request.boatSettings.safetyMarginMeters
        val criticalSample = clearances.filter { it.clearanceMeters != null }
            .minByOrNull { it.clearanceMeters!! - safetyMargin }

        return RouteSafetyAssessment(
            expectedWaypointCount = samples.size,
            clearanceSamples = clearances,
            allLegsValid = clearances.all(ClearanceSample::isValid),
            weatherStatus = WeatherSafetyEvaluator.evaluateAll(weather),
            maxWindKnots = maxWind,
            maxGustKnots = maxGust,
            criticalSample = criticalSample,
            messages =
                buildList {
                    if (fairwayResult is FairwayRouteResult.Incomplete) {
                        add("Hinweis: Fahrwasser-Optimierung unvollständig (${fairwayResult.reason}). Nutze direkte Route.")
                    }

                    // Seegat-Warnung (Wind gegen Strom)
                    samples.zip(clearances).forEach { (s, c) ->
                        if (s.isSeegat) {
                            val w = weather.getOrNull(clearances.indexOf(c))
                            if (WeatherSafetyEvaluator.checkWindAgainstCurrent(w, isSeegat = true)) {
                                add("ACHTUNG: Starkwind im Seegat (${c.waypointName}). Gefahr von Grundseen!")
                            }
                        }
                    }

                    val missingTide = clearances.any { it.isValid && it.clearanceMeters == null }
                    val missingStation = clearances.any { !it.isValid }

                    if (missingTide || missingStation) {
                        add("Eingeschränkte Genauigkeit: Für einige Streckenabschnitte liegen keine Gezeitendaten vor.")
                    }
                }
        )
    }

    private fun incompleteAssessment(input: RouteAssessmentInput, reason: String) =
        RouteSafetyAssessment(
            expectedWaypointCount = input.request.harbourChain.size,
            clearanceSamples = emptyList(),
            allLegsValid = false,
            weatherStatus = WeatherStatus.UNVOLLSTAENDIG,
            messages = listOf(reason)
        )

    private fun sampleRoute(geometry: List<GeoPoint>): List<AssessmentSample> {
        if (geometry.isEmpty()) return emptyList()
        val result = mutableListOf<AssessmentSample>()
        var dist = 0.0
        result.add(AssessmentSample(geometry[0], 0.0))

        for (i in 0 until geometry.size - 1) {
            val step = RouteMetricsCalculator.haversineNm(geometry[i], geometry[i + 1])
            dist += step
            result.add(AssessmentSample(geometry[i + 1], dist))
        }
        return result
    }

    private fun fairwaySamples(waypoints: List<RouteSafetyWaypoint>): List<AssessmentSample> {
        val result = mutableListOf<AssessmentSample>()
        var dist = 0.0
        for (i in waypoints.indices) {
            if (i > 0) {
                dist += RouteMetricsCalculator.haversineNm(waypoints[i - 1].coordinate, waypoints[i].coordinate)
            }
            result.add(
                AssessmentSample(
                    point = waypoints[i].coordinate,
                    cumulativeDistanceNm = dist,
                    chartDepthMeters = waypoints[i].chartDepthMeters,
                    hasCatalogDepth = true,
                    isSeegat = waypoints[i].isSeegat
                )
            )
        }
        return result
    }

    private data class AssessmentSample(
        val point: GeoPoint,
        val cumulativeDistanceNm: Double,
        val chartDepthMeters: Double? = null,
        val hasCatalogDepth: Boolean = false,
        val isSeegat: Boolean = false
    )
}
