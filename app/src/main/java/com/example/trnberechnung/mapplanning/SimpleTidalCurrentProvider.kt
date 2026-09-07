package com.example.trnberechnung.mapplanning

import java.time.ZonedDateTime
import java.time.Duration
import kotlin.math.sin
import kotlin.math.PI

/**
 * A simplified implementation of current estimation for the Wadden Sea.
 * In a real application, this would query a GRIB file or a hydrodynamic model API.
 *
 * Logic:
 * - Uses the nearest tide station to determine ebb/flood phase.
 * - Current follows the main fairway direction or its reverse.
 * - Strength follows a sine curve between HW and LW (Rule of Thirds approximation).
 */
class SimpleTidalCurrentProvider(
    private val tideStationProvider: TideStationProvider
) : CurrentVectorProvider {

    override suspend fun getCurrentVector(point: GeoPoint, time: ZonedDateTime): CurrentVector {
        val stations = tideStationProvider.getStations()
        if (stations.isEmpty()) return CurrentVector(0.0, 0.0)

        // Find nearest station
        val nearestStation = stations.minByOrNull {
            RouteMetricsCalculator.haversineNm(point, GeoPoint(it.latitude, it.longitude))
        } ?: return CurrentVector(0.0, 0.0)

        val events = nearestStation.events.sortedBy { it.timestamp }
        if (events.size < 2) return CurrentVector(0.0, 0.0)

        // Find the tide window we are in
        val nextEventIndex = events.indexOfFirst {
            try { ZonedDateTime.parse(it.timestamp).isAfter(time) } catch (e: Exception) { false }
        }
        if (nextEventIndex <= 0) return CurrentVector(0.0, 0.0)

        val prevEvent = events[nextEventIndex - 1]
        val nextEvent = events[nextEventIndex]

        val prevTime = try { ZonedDateTime.parse(prevEvent.timestamp) } catch (e: Exception) { return CurrentVector(0.0, 0.0) }
        val nextTime = try { ZonedDateTime.parse(nextEvent.timestamp) } catch (e: Exception) { return CurrentVector(0.0, 0.0) }

        val totalDuration = Duration.between(prevTime, nextTime).toMinutes().toDouble()
        val elapsed = Duration.between(prevTime, time).toMinutes().toDouble()

        if (totalDuration <= 0) return CurrentVector(0.0, 0.0)

        // Phase (0.0 to 1.0)
        val phase = elapsed / totalDuration

        // Tidal current speed approximation (Sine curve)
        // Max current is usually halfway between HW and LW
        val maxDrift = 2.5 // Knots, typical for Wadden Sea guts
        val currentDrift = maxDrift * sin(phase * PI)

        // Direction: simplified - Flood (towards next HW) or Ebb (towards next LW)
        // In the Wadden Sea, flood often goes East/South-East, Ebb West/North-West
        // For now, we use a fixed axis 90° (Flood) / 270° (Ebb) as placeholder
        val isFlood = nextEvent.type.contains("HW", ignoreCase = true)
        val currentSet = if (isFlood) 90.0 else 270.0

        return CurrentVector(currentSet, currentDrift)
    }
}
