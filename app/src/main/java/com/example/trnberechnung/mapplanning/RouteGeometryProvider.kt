package com.example.trnberechnung.mapplanning

import com.example.trnberechnung.routing.v2.NauticalRouterV2
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng

sealed interface RouteGeometryResult {
    data class Success(val points: List<GeoPoint>) : RouteGeometryResult

    data class Incomplete(
        val reason: String,
        val partialPoints: List<GeoPoint> = emptyList(),
    ) : RouteGeometryResult
}

fun interface RouteGeometryProvider {
    suspend fun calculate(harbourChain: List<Harbour>): RouteGeometryResult
}

class NauticalRouterV2GeometryProvider(
    private val routeCalculator: (LatLng, LatLng) -> NauticalRouterV2.RouteResult =
        NauticalRouterV2::calculateBridgedRouteResult,
) : RouteGeometryProvider {
    override suspend fun calculate(harbourChain: List<Harbour>): RouteGeometryResult =
        withContext(Dispatchers.Default) {
            if (harbourChain.size < 2) {
                return@withContext RouteGeometryResult.Incomplete(
                    reason = "Start und Ziel fehlen.",
                )
            }

            val completeRoute = mutableListOf<GeoPoint>()
            for ((from, to) in harbourChain.zipWithNext()) {
                val result =
                    routeCalculator(
                        from.coordinate.toLatLng(),
                        to.coordinate.toLatLng(),
                    )

                val leg = when (result) {
                    is NauticalRouterV2.RouteResult.Success -> {
                        result.points.map(LatLng::toGeoPoint)
                    }

                    is NauticalRouterV2.RouteResult.Incomplete -> {
                        // Kein Fallback auf Luftlinie durch Land!
                        // Wir geben mindestens die Hafenpunkte zurück, aber loggen den Fehler.
                        android.util.Log.e("NauticalRouter", "Routing fehlgeschlagen: ${result.reason}")
                        listOf(from.coordinate, to.coordinate)
                    }
                }

                if (completeRoute.isEmpty()) {
                    completeRoute += leg
                } else {
                    completeRoute += leg.drop(1)
                }
            }

            RouteGeometryResult.Success(completeRoute)
        }
}

private fun GeoPoint.toLatLng(): LatLng = LatLng(latitude, longitude)

private fun LatLng.toGeoPoint(): GeoPoint = GeoPoint(latitude, longitude)
