package com.example.trnberechnung.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trnberechnung.network.IslandCoordinatesProvider
import com.example.trnberechnung.network.IslandCoordinatesProvider.IslandData
import com.example.trnberechnung.model.*
import com.example.trnberechnung.routing.v2.NauticalRouterV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng
import java.time.LocalDateTime

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

                val draft = boatRepo.draft.toDouble()
                val margin = boatRepo.safetyMargin.toDouble()

                val islands = withContext(Dispatchers.IO) {
                    IslandCoordinatesProvider.getIslandCoordinates(getApplication())
                }

                _uiState.value = _uiState.value.copy(
                    islands = islands,
                    statusMessage = "Berechne Seeroute…",
                    boatDraft = draft,
                    safetyMargin = margin
                )

                val segments = withContext(Dispatchers.Default) {
                    if (customStart != null && customEnd != null) {
                        NauticalRouterV2.calculateSegmentedRoute(
                            customStart, customEnd,
                            draft = draft,
                            margin = margin,
                            currentTime = finalTime,
                            tideEvents = tideEvents
                        )
                    } else {
                        emptyList()
                    }
                }

                val route = segments.flatMap { it.points }

                val tideOffset = NauticalRouterV2.calculateTideOffset(finalTime, tideEvents)
                val depthPoints = if (route.isEmpty()) {
                    emptyList()
                } else {
                    NauticalRouterV2.depthSamplesAlongRoute(
                        route = route,
                        tideOffset = tideOffset,
                        draft = draft,
                        margin = margin,
                        spacingM = 1000.0
                    )
                }

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

    fun updateDepartureTime(time: LocalDateTime, tideEvents: List<TideEvent>) {
        loadAndCalculate(tideEvents, time)
    }

    fun refresh() {
        loadAndCalculate()
    }

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
        val R = 3440.065 
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * R * kotlin.math.asin(kotlin.math.sqrt(a))
    }
}
