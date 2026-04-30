package com.example.trnberechnung.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trnberechnung.network.IslandCoordinatesProvider
import com.example.trnberechnung.network.IslandCoordinatesProvider.IslandData
import com.example.trnberechnung.routing.SeaRouteCalculator
import com.example.trnberechnung.model.*
import com.example.trnberechnung.logic.NauticalRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.time.LocalDateTime

/**
 * ViewModel for the Route Planning screen.
 * Manages island data loading and sea route calculation.
 */
class RoutePlanningViewModel(application: Application) : AndroidViewModel(application) {

    private val boatRepo = BoatProfileRepository(application)

    data class UiState(
        val islands: List<IslandData> = emptyList(),
        val route: List<LatLng> = emptyList(),
        val routeSegments: List<RouteSegment> = emptyList(),
        val depthPoints: List<DepthPoint> = emptyList(),
        val isLoading: Boolean = true,
        val error: String? = null,
        val routeDistanceNm: Double = 0.0,
        val statusMessage: String = "Lade Inseldaten…",
        val boatDraft: Double = 1.2,
        val safetyMargin: Double = 0.5,
        val departureTime: LocalDateTime = LocalDateTime.now()
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState

    init {
        loadAndCalculate()
    }

    /**
     * Load island coordinates and calculate the sea route.
     */
    fun loadAndCalculate(
        tideEvents: List<TideEvent> = emptyList(),
        departureTime: LocalDateTime? = null,
        customStart: LatLng? = null,
        customEnd: LatLng? = null
    ) {
        val finalTime = departureTime ?: _uiState.value.departureTime
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true, 
                statusMessage = "Lade Inseldaten…",
                departureTime = finalTime
            )

            try {
                // Step 0: Get boat profile data
                val draft = boatRepo.draft.toDouble()
                val margin = boatRepo.safetyMargin.toDouble()

                // Step 1: Load island coordinates (offline-first)
                val islands = withContext(Dispatchers.IO) {
                    IslandCoordinatesProvider.getIslandCoordinates(getApplication())
                }

                _uiState.value = _uiState.value.copy(
                    islands = islands,
                    statusMessage = "Berechne Seeroute…",
                    boatDraft = draft,
                    safetyMargin = margin
                )

                // Step 3: Calculate sea route
                val segments = withContext(Dispatchers.Default) {
                    if (customStart != null && customEnd != null) {
                        // Point-to-Point route
                        NauticalRouter.calculateSegmentedRoute(
                            customStart, customEnd,
                            draft = draft,
                            margin = margin,
                            currentTime = finalTime,
                            tideEvents = tideEvents
                        )
                    } else {
                        // Do NOT calculate multi-stop by default anymore to avoid clutter
                        emptyList()
                    }
                }

                // Step 3.5: Calculate depth points for the area around the route
                val tideOffset = NauticalRouter.calculateTideOffset(finalTime, tideEvents)
                val allWaypoints = NauticalRouter.waypoints
                
                // Flatten segments for distance calculation and backward compatibility
                val route = segments.flatMap { it.points }

                // Filter depth points to only show those relevant to the current active route
                // If no route is active, show no depth points (or maybe only port points?)
                val relevantWaypoints = if (route.isEmpty()) {
                    // Only show harbor waypoints if no route is active
                    allWaypoints.filter { it.id.contains("hbr") }
                } else {
                    // Show waypoints within ~2nm of the route
                    allWaypoints.filter { wp ->
                        route.any { pt -> haversineNm(wp.lat, wp.lon, pt.latitude, pt.longitude) < 2.0 }
                    }
                }

                val depthPoints = relevantWaypoints.map { wp ->
                    val currentDepth = wp.chartDepth + tideOffset
                    DepthPoint(
                        position = LatLng(wp.lat, wp.lon),
                        depth = currentDepth,
                        type = NauticalRouter.classifyDepth(currentDepth, draft, margin)
                    )
                }

                // Step 4: Calculate total distance
                val totalDistNm = calculateTotalDistanceNm(route)

                _uiState.value = _uiState.value.copy(
                    islands = islands,
                    route = route,
                    routeSegments = segments,
                    depthPoints = depthPoints,
                    isLoading = false,
                    error = null,
                    routeDistanceNm = totalDistNm,
                    statusMessage = "Seeroute berechnet: %.1f nm (Tiefgang: %.1fm)".format(totalDistNm, draft)
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Fehler: ${e.message}",
                    statusMessage = "Fehler bei der Routenberechnung"
                )
            }
        }
    }

    /**
     * Update departure time and recalculate route.
     */
    fun updateDepartureTime(time: LocalDateTime, tideEvents: List<TideEvent>) {
        loadAndCalculate(tideEvents, time)
    }

    /**
     * Refresh island data and recalculate route.
     */
    fun refresh() {
        loadAndCalculate()
    }

    /**
     * Calculate total route distance in nautical miles.
     */
    private fun calculateTotalDistanceNm(route: List<LatLng>): Double {
        if (route.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until route.size - 1) {
            total += haversineNm(
                route[i].latitude, route[i].longitude,
                route[i + 1].latitude, route[i + 1].longitude
            )
        }
        return total
    }

    private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 3440.065 // Earth radius in nautical miles
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * R * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}
