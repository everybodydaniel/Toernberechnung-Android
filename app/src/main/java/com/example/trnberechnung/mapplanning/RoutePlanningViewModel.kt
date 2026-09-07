package com.example.trnberechnung.mapplanning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Clock
import java.time.ZonedDateTime
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RoutePlanningViewModel(
    private val routeGeometryProvider: RouteGeometryProvider = NauticalRouterV2GeometryProvider(),
    private val routeAssessmentProvider: RouteAssessmentProvider =
        IncompleteRouteAssessmentProvider,
    private val metricRouteResolver: FairwayRouteResolver? = null,
    private val passageWindowScanner: PassageWindowScanner = PassageWindowScanner(),
    private val currentVectorProvider: CurrentVectorProvider? = null,
    clock: Clock = Clock.systemUTC(),
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            RoutePlanningUiState(
                departure =
                    ZonedDateTime.now(clock)
                        .withZoneSameInstant(MAP_PLANNING_ZONE_ID)
                        .withSecond(0)
                        .withNano(0),
            ),
        )
    val uiState: StateFlow<RoutePlanningUiState> = _uiState.asStateFlow()

    private var calculationJob: Job? = null
    private var debounceJob: Job? = null
    private var calculationGeneration = 0L

    fun selectStart(harbourId: HarbourId?) {
        val current = _uiState.value
        if (current.startHarbourId == harbourId) return
        val sanitizedStops =
            RouteStopRules.removeEndpointCollisions(
                existing = current.intermediateStops,
                start = harbourId,
                destination = current.destinationHarbourId,
            )
        _uiState.value =
            current.copy(
                startHarbourId = harbourId,
                intermediateStops = sanitizedStops,
            )
        routeInputChanged()
    }

    fun selectDestination(harbourId: HarbourId?) {
        val current = _uiState.value
        if (current.destinationHarbourId == harbourId) return
        val sanitizedStops =
            RouteStopRules.removeEndpointCollisions(
                existing = current.intermediateStops,
                start = current.startHarbourId,
                destination = harbourId,
            )
        _uiState.value =
            current.copy(
                destinationHarbourId = harbourId,
                intermediateStops = sanitizedStops,
            )
        routeInputChanged()
    }

    fun addIntermediateStops(harbourIds: Iterable<HarbourId>): Int {
        val current = _uiState.value
        val updated =
            RouteStopRules.add(
                existing = current.intermediateStops,
                candidates = harbourIds,
                start = current.startHarbourId,
                destination = current.destinationHarbourId,
            )
        val additions = updated.size - current.intermediateStops.size
        if (additions > 0) {
            _uiState.value = current.copy(intermediateStops = updated)
            routeInputChanged()
        }
        return additions
    }

    fun updateIntermediateStop(
        stopId: UUID,
        harbourId: HarbourId,
    ): Boolean {
        val current = _uiState.value
        val updated =
            RouteStopRules.update(
                existing = current.intermediateStops,
                stopId = stopId,
                harbourId = harbourId,
                start = current.startHarbourId,
                destination = current.destinationHarbourId,
            ) ?: return false
        if (updated != current.intermediateStops) {
            _uiState.value = current.copy(intermediateStops = updated)
            routeInputChanged()
        }
        return true
    }

    fun removeIntermediateStop(stopId: UUID) {
        val current = _uiState.value
        val updated = current.intermediateStops.filterNot { it.id == stopId }
        if (updated != current.intermediateStops) {
            _uiState.value = current.copy(intermediateStops = updated)
            routeInputChanged()
        }
    }

    fun updateDeparture(departure: ZonedDateTime, triggerCalculation: Boolean = true) {
        val normalized = departure.withZoneSameInstant(MAP_PLANNING_ZONE_ID)
        if (_uiState.value.departure == normalized) return
        _uiState.update { it.copy(departure = normalized) }

        if (triggerCalculation) {
            debounceJob?.cancel()
            debounceJob = viewModelScope.launch {
                kotlinx.coroutines.delay(150)
                routeInputChanged()
            }
        }
    }

    fun updateBoatSettings(
        draftMeters: Double,
        safetyMarginMeters: Double,
        speedKnots: Double,
        waterLevelCorrectionMeters: Double,
    ) {
        val settings =
            BoatSettings(
                draftMeters = draftMeters,
                safetyMarginMeters = safetyMarginMeters,
                speedKnots = speedKnots,
                waterLevelCorrectionMeters = waterLevelCorrectionMeters,
            )
        if (_uiState.value.boatSettings == settings) return
        _uiState.update { it.copy(boatSettings = settings) }
        routeInputChanged()
    }

    fun clearRoute() {
        calculationGeneration += 1
        calculationJob?.cancel()
        _uiState.update {
            RoutePlanningUiState(
                departure = it.departure,
                boatSettings = it.boatSettings,
            )
        }
    }

    /**
     * Re-evaluates the current plan without clearing start, destination,
     * intermediate stops, departure or the last visible route first.
     */
    fun refresh() {
        if (_uiState.value.hasCompleteRouteInput) {
            calculateCurrentRoute()
        }
    }

    fun refreshPassageWindow() {
        val snapshot = _uiState.value
        val request = snapshot.toRequestOrNull() ?: return
        val metrics = snapshot.routeMetrics ?: return
        if (snapshot.routeGeometry.size < 2) return

        calculationGeneration += 1
        val generation = calculationGeneration
        calculationJob?.cancel()
        _uiState.update { it.copy(isSearchingPassageWindow = true, error = null) }
        calculationJob =
            viewModelScope.launch {
                try {
                    val windows =
                        findPassageWindows(
                            request = request,
                            routeGeometry = snapshot.routeGeometry,
                            distanceNm = metrics.distanceNm,
                            averageTrueCourse = metrics.averageTrueCourseDegrees ?: 0.0
                        )

                    val currentWindow = windows.find { it.contains(request.departure) }
                        ?: windows.filter { it.start.isAfter(request.departure) }.minByOrNull { it.start }
                        ?: windows.firstOrNull()

                    val finalAssessment = assessWithWindow(request, snapshot.routeGeometry, metrics, currentWindow)
                    val tidalEval = UnderKeelSafetyEvaluator.evaluate(
                        finalAssessment.clearanceSamples,
                        request.boatSettings.safetyMarginMeters,
                        finalAssessment.allLegsValid
                    )
                    val combinedStatus = RouteStatusEvaluator.combine(
                        tidalEval.status,
                        finalAssessment.weatherStatus
                    )

                    updateForGeneration(generation) {
                        it.copy(
                            passageWindows = windows,
                            isSearchingPassageWindow = false,
                            routeStatus = combinedStatus,
                            tidalStatus = tidalEval.status,
                            failureReason = tidalEval.reason,
                            routeMetrics = metrics.copy(
                                worstUnderKeelClearanceMeters = finalAssessment.worstClearanceMeters,
                                worstClearanceName = finalAssessment.worstClearanceSample?.waypointName
                            )
                        )
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    updateForGeneration(generation) {
                        it.copy(
                            isSearchingPassageWindow = false,
                            error = error.message ?: "Passagefenster konnte nicht berechnet werden.",
                        )
                    }
                }
            }
    }

    private suspend fun assessWithWindow(
        request: RoutePlanningRequest,
        geometry: List<GeoPoint>,
        metrics: RouteMetrics,
        window: PassageWindow?,
    ): RouteSafetyAssessment {
        val assessment = routeAssessmentProvider.assess(
            RouteAssessmentInput(request, geometry, metrics, currentProvider = currentVectorProvider)
        )

        // Falls wir ein berechnetes Fenster haben, geben wir es an den Evaluator weiter,
        // damit dieser prüfen kann, ob die Ankunft außerhalb des sicheren Fensters liegt.
        val enrichedSamples = assessment.clearanceSamples.mapIndexed { index, sample ->
            if (index == assessment.clearanceSamples.lastIndex) {
                sample.copy(safeWindowEnd = window?.end)
            } else sample
        }

        val safetyMargin = request.boatSettings.safetyMarginMeters
        val bottleneck = enrichedSamples.filter { it.clearanceMeters != null }
            .minByOrNull { it.clearanceMeters!! - safetyMargin }

        return assessment.copy(
            clearanceSamples = enrichedSamples,
            criticalSample = bottleneck
        )
    }

    private fun routeInputChanged() {
        if (_uiState.value.hasCompleteRouteInput) {
            calculateCurrentRoute()
        } else {
            clearCalculatedRoute()
        }
    }

    private fun clearCalculatedRoute() {
        calculationGeneration += 1
        calculationJob?.cancel()
        _uiState.update {
            it.copy(
                routeGeometry = emptyList(),
                routeStatus = RouteStatus.UNVOLLSTAENDIG,
                tidalStatus = RouteStatus.UNVOLLSTAENDIG,
                weatherStatus = WeatherStatus.UNVOLLSTAENDIG,
                routeMetrics = null,
                passageWindows = emptyList(),
                isCalculating = false,
                isSearchingPassageWindow = false,
                messages = emptyList(),
                error = null,
            )
        }
    }

    private fun calculateCurrentRoute() {
        val request = _uiState.value.toRequestOrNull() ?: return
        calculationGeneration += 1
        val generation = calculationGeneration
        calculationJob?.cancel()
        _uiState.update {
            it.copy(
                isCalculating = true,
                isSearchingPassageWindow = false,
                messages = emptyList(),
                error = null,
            )
        }

        calculationJob =
            viewModelScope.launch {
                try {
                    val harbours = request.harbourChain.map(HarbourCatalog::get)
                    val geometryResult = withContext(Dispatchers.Default) {
                        routeGeometryProvider.calculate(harbours)
                    }
                    when (geometryResult) {
                        is RouteGeometryResult.Incomplete -> {
                            updateForGeneration(generation) {
                                it.copy(
                                    routeGeometry = geometryResult.partialPoints,
                                    routeStatus = RouteStatus.UNVOLLSTAENDIG,
                                    tidalStatus = RouteStatus.UNVOLLSTAENDIG,
                                    weatherStatus = WeatherStatus.UNVOLLSTAENDIG,
                                    routeMetrics = null,
                                    passageWindows = emptyList(),
                                    isCalculating = false,
                                    isSearchingPassageWindow = false,
                                    messages = listOf(geometryResult.reason),
                                    error = geometryResult.reason,
                                )
                            }
                        }

                        is RouteGeometryResult.Success -> {
                            calculateSafetyAndPassage(
                                generation = generation,
                                request = request,
                                routeGeometry = geometryResult.points,
                            )
                        }
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Exception) {
                    updateForGeneration(generation) {
                        it.copy(
                            routeStatus = RouteStatus.UNVOLLSTAENDIG,
                            tidalStatus = RouteStatus.UNVOLLSTAENDIG,
                            weatherStatus = WeatherStatus.UNVOLLSTAENDIG,
                            isCalculating = false,
                            isSearchingPassageWindow = false,
                            messages = listOf(
                                error.message ?: "Die Route konnte nicht berechnet werden.",
                            ),
                            error = error.message ?: "Die Route konnte nicht berechnet werden.",
                        )
                    }
                }
            }
    }

    private suspend fun calculateSafetyAndPassage(
        generation: Long,
        request: RoutePlanningRequest,
        routeGeometry: List<GeoPoint>,
    ) {
        android.util.Log.d("RoutePlanning", "Starte Sicherheitsprüfung für Abfahrt ${request.departure}")
        val fairwayResult = withContext(Dispatchers.Default) {
            metricRouteResolver?.resolve(request)
        }

        val metricGeometry =
            when (fairwayResult) {
                null -> routeGeometry
                is FairwayRouteResult.Success ->
                    fairwayResult.waypoints.map(RouteSafetyWaypoint::coordinate)

                is FairwayRouteResult.Incomplete -> {
                    // FALLBACK: Wenn die Fahrwasser-Optimierung fehlschlägt, nutzen wir die Rohgeometrie.
                    // So wird für jede gewählte Route (z.B. Borkum, Spiekeroog) ein Ergebnis berechnet.
                    android.util.Log.w("RoutePlanning", "Fahrwasser-Optimierung unvollständig: ${fairwayResult.reason}")
                    routeGeometry
                }
            }
        val initialMetrics = withContext(Dispatchers.Default) {
            RouteMetricsCalculator.calculate(
                routeGeometry = metricGeometry,
                departure = request.departure,
                boatSettings = request.boatSettings,
                currentProvider = currentVectorProvider
            )
        }
        if (initialMetrics == null) {
            updateForGeneration(generation) {
                it.copy(
                    routeGeometry = routeGeometry,
                    routeStatus = RouteStatus.UNVOLLSTAENDIG,
                    isCalculating = false,
                    messages = listOf("Die Routengeometrie ist unvollständig."),
                    error = "Die Routengeometrie ist unvollständig.",
                )
            }
            return
        }

        val assessment = withContext(Dispatchers.Default) {
            routeAssessmentProvider.assess(
                RouteAssessmentInput(
                    request = request,
                    routeGeometry = routeGeometry,
                    routeMetrics = initialMetrics,
                    isScan = false,
                    currentProvider = currentVectorProvider
                ),
            )
        }
        android.util.Log.d("RoutePlanning", "Assessment abgeschlossen: ${assessment.clearanceSamples.size} Samples, allLegsValid=${assessment.allLegsValid}")

        val hasExpectedSamples =
            assessment.clearanceSamples.size == assessment.expectedWaypointCount
        val tidalEval =
            UnderKeelSafetyEvaluator.evaluate(
                samples = assessment.clearanceSamples,
                safetyMarginMeters = request.boatSettings.safetyMarginMeters,
                allLegsValid = assessment.allLegsValid && hasExpectedSamples,
            )
        val routeStatus = RouteStatusEvaluator.combine(tidalEval.status, assessment.weatherStatus)
        android.util.Log.d("RoutePlanning", "Status berechnet: tidal=${tidalEval.status}, route=$routeStatus, worstClearance=${assessment.worstClearanceMeters}")

        val metrics =
            initialMetrics.copy(
                worstUnderKeelClearanceMeters = assessment.worstClearanceMeters,
                worstClearanceName = assessment.worstClearanceSample?.waypointName,
            )

        updateForGeneration(generation) {
            it.copy(
                routeGeometry = routeGeometry,
                routeStatus = routeStatus,
                tidalStatus = tidalEval.status,
                weatherStatus = assessment.weatherStatus,
                failureReason = tidalEval.reason,
                routeMetrics = metrics,
                passageWindows = emptyList(),
                isCalculating = false,
                isSearchingPassageWindow = true,
                messages = assessment.messages,
                error = null,
            )
        }

        val passageWindows = withContext(Dispatchers.Default) {
            findPassageWindows(
                request = request,
                routeGeometry = routeGeometry,
                distanceNm = metrics.distanceNm,
                averageTrueCourse = metrics.averageTrueCourseDegrees ?: 0.0
            )
        }

        // Nach der Fensterberechnung evaluieren wir den Status erneut,
        // um das Zeitfenster (Ankunft nach Fenster-Ende) zu berücksichtigen.
        val currentWindow = passageWindows.find { it.contains(request.departure) }
            ?: passageWindows.filter { it.start.isAfter(request.departure) }.minByOrNull { it.start }
            ?: passageWindows.firstOrNull()

        val finalAssessment = assessWithWindow(request, routeGeometry, metrics, currentWindow)
        val finalTidalEval = UnderKeelSafetyEvaluator.evaluate(
            samples = finalAssessment.clearanceSamples,
            safetyMarginMeters = request.boatSettings.safetyMarginMeters,
            allLegsValid = assessment.allLegsValid && hasExpectedSamples,
        )
        val finalRouteStatus = RouteStatusEvaluator.combine(finalTidalEval.status, finalAssessment.weatherStatus)

        updateForGeneration(generation) {
            it.copy(
                passageWindows = passageWindows,
                isSearchingPassageWindow = false,
                routeStatus = finalRouteStatus,
                tidalStatus = finalTidalEval.status,
                failureReason = finalTidalEval.reason,
                routeMetrics = metrics.copy(
                    worstUnderKeelClearanceMeters = finalAssessment.worstClearanceMeters,
                    worstClearanceName = finalAssessment.bottleneckSample?.waypointName
                )
            )
        }
    }

    private suspend fun findPassageWindows(
        request: RoutePlanningRequest,
        routeGeometry: List<GeoPoint>,
        distanceNm: Double,
        averageTrueCourse: Double,
    ): List<PassageWindow> {
        return passageWindowScanner.findSafeWindows(
            center = request.departure,
            evaluator =
                PassageCandidateEvaluator { candidateDeparture ->
                    val shiftedRequest = request.copy(departure = candidateDeparture)
                    // WICHTIG: Wir nutzen eine rein distanzbasierte Metrik ohne dynamische Faktoren,
                    // um die Konsistenz des Fensters zu wahren.
                    val shiftedMetrics = RouteMetricsCalculator.fromDistance(
                        distanceNm = distanceNm,
                        departure = candidateDeparture,
                        boatSettings = request.boatSettings,
                        averageTrueCourse = averageTrueCourse
                    )

                    val assessment =
                        routeAssessmentProvider.assess(
                            RouteAssessmentInput(
                                request = shiftedRequest,
                                routeGeometry = routeGeometry,
                                routeMetrics = shiftedMetrics,
                                isScan = true,
                                currentProvider = currentVectorProvider
                            ),
                        )
                    PassageCandidateAssessment(
                        expectedWaypointCount = assessment.expectedWaypointCount,
                        waypointClearances = assessment.clearanceSamples,
                        allLegsValid = assessment.allLegsValid,
                        safetyMarginMeters = request.boatSettings.safetyMarginMeters,
                        weatherStatus = assessment.weatherStatus
                    )
                },
        )
    }

    private inline fun updateForGeneration(
        generation: Long,
        transform: (RoutePlanningUiState) -> RoutePlanningUiState,
    ) {
        if (generation == calculationGeneration) {
            _uiState.update(transform)
        }
    }
}

private fun RoutePlanningUiState.toRequestOrNull(): RoutePlanningRequest? {
    val start = startHarbourId ?: return null
    val destination = destinationHarbourId ?: return null
    if (start == destination) return null
    return RoutePlanningRequest(
        startHarbourId = start,
        destinationHarbourId = destination,
        intermediateStops = intermediateStops,
        departure = departure,
        boatSettings = boatSettings,
    )
}
