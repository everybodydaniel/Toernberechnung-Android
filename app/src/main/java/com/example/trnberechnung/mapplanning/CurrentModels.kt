package com.example.trnberechnung.mapplanning

import java.time.ZonedDateTime
import kotlin.math.*

/**
 * Represents a tidal current vector (Set and Drift).
 * @param setDegrees The direction the current is flowing TOWARDS (0-360 degrees).
 * @param driftKnots The speed of the current in knots.
 */
data class CurrentVector(
    val setDegrees: Double,
    val driftKnots: Double
)

interface CurrentVectorProvider {
    /**
     * Returns the estimated current vector for a given position and time.
     */
    suspend fun getCurrentVector(point: GeoPoint, time: ZonedDateTime): CurrentVector
}

/**
 * Result of the navigation triangle calculation (STW + Current = SOG).
 */
data class NavigationVectorResult(
    val sogKnots: Double,
    val headingDegrees: Double,
    val driftAngleDegrees: Double
)

object VectorMath {
    /**
     * Calculates SOG and Heading (rwP) given STW, Course (rwK) and Current.
     * Uses the wind triangle (here current triangle) logic.
     *
     * @param stwKnots Speed Through Water (Log speed)
     * @param courseOverGroundDegrees The desired track (rwK)
     * @param current Current vector (Set and Drift)
     */
    fun calculateSogAndHeading(
        stwKnots: Double,
        courseOverGroundDegrees: Double,
        current: CurrentVector
    ): NavigationVectorResult {
        val cogRad = Math.toRadians(courseOverGroundDegrees)
        val setRad = Math.toRadians(current.setDegrees)

        // Current components
        val curX = current.driftKnots * cos(setRad)
        val curY = current.driftKnots * sin(setRad)

        // Desired track components (unit vector)
        val trackX = cos(cogRad)
        val trackY = sin(cogRad)

        // Solve for SOG: (SOG*trackX - curX)^2 + (SOG*trackY - curY)^2 = STW^2
        // SOG^2 - 2*SOG*(trackX*curX + trackY*curY) + curX^2 + curY^2 - STW^2 = 0

        val a = 1.0
        val b = -2.0 * (trackX * curX + trackY * curY)
        val c = current.driftKnots * current.driftKnots - stwKnots * stwKnots

        val discriminant = b * b - 4 * a * c
        if (discriminant < 0) {
            // Current is stronger than boat speed against us
            return NavigationVectorResult(0.0, courseOverGroundDegrees, 0.0)
        }

        val sog = (-b + sqrt(discriminant)) / (2 * a)

        // Heading components
        val hdgX = sog * trackX - curX
        val hdgY = sog * trackY - curY

        val hdgRad = atan2(hdgY, hdgX)
        val heading = (Math.toDegrees(hdgRad) + 360.0) % 360.0

        val driftAngle = (heading - courseOverGroundDegrees + 540.0) % 360.0 - 180.0

        return NavigationVectorResult(sog.coerceAtLeast(0.0), heading, driftAngle)
    }
}
