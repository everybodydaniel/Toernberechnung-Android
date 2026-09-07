package com.example.trnberechnung.mapplanning

import com.example.trnberechnung.logic.NauticalRouter
import org.maplibre.android.geometry.LatLng

data class RouteSafetyWaypoint(
    val id: String,
    val name: String,
    val coordinate: GeoPoint,
    val chartDepthMeters: Double?,
    val harbourId: HarbourId? = null,
    val isSeegat: Boolean = false,
) {
    val isUserWaypoint: Boolean
        get() = harbourId != null
}

sealed interface FairwayRouteResult {
    data class Success(
        val waypoints: List<RouteSafetyWaypoint>,
    ) : FairwayRouteResult

    data class Incomplete(
        val reason: String,
        val partialWaypoints: List<RouteSafetyWaypoint> = emptyList(),
    ) : FairwayRouteResult
}

fun interface FairwayRouteResolver {
    fun resolve(request: RoutePlanningRequest): FairwayRouteResult
}

/**
 * Expands start, ordered stops and destination with the strict Dijkstra
 * fairway graph. User-selected harbours retain stable IDs; injected graph
 * nodes carry their catalog chart depths.
 */
object CatalogFairwayRouteResolver : FairwayRouteResolver {
    override fun resolve(request: RoutePlanningRequest): FairwayRouteResult {
        val chain = request.harbourChain.map(HarbourCatalog::get)
        if (chain.size < 2) {
            return FairwayRouteResult.Incomplete("Start und Ziel fehlen.")
        }

        val expanded = mutableListOf<RouteSafetyWaypoint>()
        chain.zipWithNext().forEachIndexed { legIndex, (from, to) ->
            if (expanded.isEmpty()) expanded += from.toSafetyWaypoint()
            when (
                val path =
                    NauticalRouter.calculateFairwayPathResult(
                        start = from.coordinate.toLatLng(),
                        end = to.coordinate.toLatLng(),
                    )
            ) {
                is NauticalRouter.FairwayPathResult.Incomplete ->
                    return FairwayRouteResult.Incomplete(
                        reason = "${from.name} → ${to.name}: ${path.reason}",
                        partialWaypoints = expanded,
                    )

                is NauticalRouter.FairwayPathResult.Success -> {
                    path.waypoints
                        .filterNot { waypoint ->
                            waypoint.isCloseTo(from.coordinate) ||
                                waypoint.isCloseTo(to.coordinate)
                        }.forEachIndexed { waypointIndex, waypoint ->
                            expanded +=
                                RouteSafetyWaypoint(
                                    id = "fairway-$legIndex-$waypointIndex-${waypoint.id}",
                                    name = waypoint.id,
                                    coordinate = GeoPoint(waypoint.lat, waypoint.lon),
                                    chartDepthMeters = waypoint.chartDepth,
                                    isSeegat = waypoint.isSeegat
                                )
                        }
                    expanded += to.toSafetyWaypoint()
                }
            }
        }

        return if (expanded.size >= chain.size) {
            FairwayRouteResult.Success(expanded)
        } else {
            FairwayRouteResult.Incomplete(
                reason = "Der Fahrwasserweg ist unvollständig.",
                partialWaypoints = expanded,
            )
        }
    }
}

private fun Harbour.toSafetyWaypoint(): RouteSafetyWaypoint =
    RouteSafetyWaypoint(
        id = id.rawValue,
        name = name,
        coordinate = coordinate,
        chartDepthMeters = chartDepthMeters,
        harbourId = id,
    )

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun NauticalRouter.WP.isCloseTo(point: GeoPoint): Boolean =
    kotlin.math.abs(lat - point.latitude) < 0.005 &&
        kotlin.math.abs(lon - point.longitude) < 0.008
