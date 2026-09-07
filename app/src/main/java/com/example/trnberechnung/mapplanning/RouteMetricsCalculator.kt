package com.example.trnberechnung.mapplanning

import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt

object RouteMetricsCalculator {
    private const val EARTH_RADIUS_NM = 3_440.065
    private const val DIESEL_SAFETY_RESERVE_FACTOR = 1.20 // 20% reserve

    suspend fun calculate(
        routeGeometry: List<GeoPoint>,
        departure: ZonedDateTime,
        boatSettings: BoatSettings,
        worstClearanceMeters: Double? = null,
        worstClearanceName: String? = null,
        currentProvider: CurrentVectorProvider? = null
    ): RouteMetrics? {
        if (routeGeometry.size < 2 || boatSettings.speedKnots <= 0) return null

        var totalSeconds = 0.0
        var currentTime = departure

        val legs = routeGeometry.zipWithNext()
        legs.forEach { (start, end) ->
            val dist = haversineNm(start, end)
            val course = initialBearingDegrees(start, end)

            val sog = if (currentProvider != null) {
                val current = currentProvider.getCurrentVector(start, currentTime)
                VectorMath.calculateSogAndHeading(boatSettings.speedKnots, course, current).sogKnots
            } else {
                boatSettings.speedKnots
            }

            val legSeconds = (dist / sog.coerceAtLeast(0.1) * 3_600)
            totalSeconds += legSeconds
            currentTime = currentTime.plusSeconds(legSeconds.toLong())
        }

        val distanceNm = legs.sumOf { (start, end) -> haversineNm(start, end) }

        val avgCourse = calculateAverageTrueCourse(routeGeometry)

        // Calculate average current for display
        val currentVectors = routeGeometry.mapNotNull { point ->
            currentProvider?.getCurrentVector(point, departure)
        }
        val avgSet = if (currentVectors.isNotEmpty()) currentVectors.map { it.setDegrees }.average() else null
        val avgDrift = if (currentVectors.isNotEmpty()) currentVectors.map { it.driftKnots }.average() else null

        val travelTime = Duration.ofSeconds(totalSeconds.roundToLong())
        val berlinDeparture = departure.withZoneSameInstant(MAP_PLANNING_ZONE_ID)
        val consumptionBase = distanceNm * boatSettings.dieselLitersPerNm

        return RouteMetrics(
            distanceNm = distanceNm,
            travelTime = travelTime,
            arrival = berlinDeparture.plus(travelTime),
            worstUnderKeelClearanceMeters = worstClearanceMeters,
            draftMeters = boatSettings.draftMeters,
            safetyMarginMeters = boatSettings.safetyMarginMeters,
            worstClearanceName = worstClearanceName,
            dieselLiters = consumptionBase,
            dieselReserveLiters = consumptionBase * (DIESEL_SAFETY_RESERVE_FACTOR - 1.0),
            averageTrueCourseDegrees = avgCourse,
            averageCurrentSetDegrees = avgSet,
            averageCurrentDriftKnots = avgDrift
        )
    }

    fun fromDistance(
        distanceNm: Double,
        departure: ZonedDateTime,
        boatSettings: BoatSettings,
        worstClearanceMeters: Double? = null,
        worstClearanceName: String? = null,
        averageTrueCourse: Double? = null,
    ): RouteMetrics {
        require(distanceNm >= 0) { "Die Distanz darf nicht negativ sein." }
        val speedKnots = boatSettings.speedKnots
        require(speedKnots > 0) { "Die Fahrtgeschwindigkeit muss größer als 0 sein." }

        val travelSeconds = (distanceNm / speedKnots * 3_600).roundToLong()
        val travelTime = Duration.ofSeconds(travelSeconds)
        val berlinDeparture = departure.withZoneSameInstant(MAP_PLANNING_ZONE_ID)

        val consumptionBase = distanceNm * boatSettings.dieselLitersPerNm

        return RouteMetrics(
            distanceNm = distanceNm,
            travelTime = travelTime,
            arrival = berlinDeparture.plus(travelTime),
            worstUnderKeelClearanceMeters = worstClearanceMeters,
            draftMeters = boatSettings.draftMeters,
            safetyMarginMeters = boatSettings.safetyMarginMeters,
            worstClearanceName = worstClearanceName,
            dieselLiters = consumptionBase,
            dieselReserveLiters = consumptionBase * (DIESEL_SAFETY_RESERVE_FACTOR - 1.0),
            averageTrueCourseDegrees = averageTrueCourse
        )
    }

    fun calculateAverageTrueCourse(routeGeometry: List<GeoPoint>): Double {
        if (routeGeometry.size < 2) return 0.0
        var totalX = 0.0
        var totalY = 0.0
        var totalDist = 0.0

        routeGeometry.zipWithNext().forEach { (s, e) ->
            val dist = haversineNm(s, e)
            if (dist > 0.0001) {
                val brng = Math.toRadians(initialBearingDegrees(s, e))
                totalX += dist * cos(brng)
                totalY += dist * sin(brng)
                totalDist += dist
            }
        }

        if (totalDist == 0.0) return 0.0
        val avgRad = atan2(totalY, totalX)
        return (Math.toDegrees(avgRad) + 360.0) % 360.0
    }

    fun initialBearingDegrees(start: GeoPoint, end: GeoPoint): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)

        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val brng = atan2(y, x)
        return (Math.toDegrees(brng) + 360.0) % 360.0
    }

    fun haversineNm(start: GeoPoint, end: GeoPoint): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val deltaLat = Math.toRadians(end.latitude - start.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)
        val haversine =
            sin(deltaLat / 2).let { it * it } +
                cos(lat1) * cos(lat2) * sin(deltaLon / 2).let { it * it }
        return EARTH_RADIUS_NM * 2 * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}
