package com.example.trnberechnung.routing.v2

import com.example.trnberechnung.logic.RouterLog
import org.maplibre.android.geometry.LatLng

internal object BuoyValidator {

    private const val TAG = "BuoyValidator"

    private const val SAMPLE_M = 100.0

    private const val MAX_PLAUSIBLE_DISTANCE_M = 1500.0

    private const val OPEN_SEA_DEPTH_M = 10.0

    enum class Status { PASSED, SUSPECT }

    data class Result(
        val status: Status,
        val suspectCount: Int,
        val maxBuoyDistanceM: Double,
        val sampleCount: Int
    )

    fun validate(path: List<LatLng>): Result {
        if (path.size < 2) return Result(Status.PASSED, 0, 0.0, 0)
        if (SeaMask.buoyCount() == 0) return Result(Status.PASSED, 0, 0.0, 0)

        var suspectSamples = 0
        var maxDist = 0.0
        var totalSamples = 0

        for (i in 0 until path.size - 1) {
            val a = path[i]
            val b = path[i + 1]
            val len = GridConfig.approxMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val steps = kotlin.math.max(2, (len / SAMPLE_M).toInt())

            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val lat = a.latitude + (b.latitude - a.latitude) * t
                val lon = a.longitude + (b.longitude - a.longitude) * t

                val buoyDist = SeaMask.nearestBuoyDistanceMeters(lat, lon)
                if (buoyDist > maxDist) maxDist = buoyDist

                val chartDepth = SeaMask.depthAtLatLng(lat, lon)
                if (buoyDist > MAX_PLAUSIBLE_DISTANCE_M && chartDepth < OPEN_SEA_DEPTH_M) {
                    suspectSamples++
                }
                totalSamples++
            }
        }

        val status = if (suspectSamples > totalSamples / 10) Status.SUSPECT else Status.PASSED

        if (status == Status.SUSPECT) {
            RouterLog.w(
                TAG,
                "Path validation SUSPECT: $suspectSamples/$totalSamples samples > " +
                    "${MAX_PLAUSIBLE_DISTANCE_M.toInt()}m from any buoy in shallow water. " +
                    "Max buoy distance = ${maxDist.toInt()}m. " +
                    "Layer A (LandMask) should still have prevented land crossing — verify visually."
            )
        } else {
            RouterLog.d(
                TAG,
                "Path validation PASSED: $suspectSamples/$totalSamples suspect, max=${maxDist.toInt()}m"
            )
        }

        return Result(status, suspectSamples, maxDist, totalSamples)
    }
}
