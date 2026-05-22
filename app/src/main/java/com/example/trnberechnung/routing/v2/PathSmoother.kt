package com.example.trnberechnung.routing.v2

import org.maplibre.android.geometry.LatLng
import kotlin.math.abs
import kotlin.math.sqrt

internal object PathSmoother {

    private const val DP_EPSILON_M = 80.0

    private const val CHAIKIN_ITERATIONS = 2

    private const val VALIDATION_SAMPLE_M = 50.0

    fun smooth(path: List<LatLng>): List<LatLng> {
        if (path.size < 3) return path

        val simplified = douglasPeucker(path, DP_EPSILON_M)
        var smoothed = simplified
        repeat(CHAIKIN_ITERATIONS) { smoothed = chaikinIter(smoothed) }

        return validateAndFix(smoothed, simplified)
    }

    private fun douglasPeucker(points: List<LatLng>, epsilonM: Double): List<LatLng> {
        if (points.size < 3) return points
        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true
        dpRecurse(points, 0, points.size - 1, epsilonM, keep)
        return points.filterIndexed { i, _ -> keep[i] }
    }

    private fun dpRecurse(
        points: List<LatLng>,
        start: Int,
        end: Int,
        eps: Double,
        keep: BooleanArray
    ) {
        if (end <= start + 1) return
        var maxD = 0.0
        var maxIdx = -1
        val a = points[start]
        val b = points[end]
        for (i in start + 1 until end) {
            val d = perpendicularDistanceMeters(points[i], a, b)
            if (d > maxD) { maxD = d; maxIdx = i }
        }
        if (maxD > eps && maxIdx > 0) {
            keep[maxIdx] = true
            dpRecurse(points, start, maxIdx, eps, keep)
            dpRecurse(points, maxIdx, end, eps, keep)
        }
    }

    private fun perpendicularDistanceMeters(p: LatLng, a: LatLng, b: LatLng): Double {

        val midLat = (a.latitude + b.latitude) / 2.0
        val cosMid = kotlin.math.cos(midLat * kotlin.math.PI / 180.0)
        val ax = a.longitude * cosMid; val ay = a.latitude
        val bx = b.longitude * cosMid; val by = b.latitude
        val px = p.longitude * cosMid; val py = p.latitude
        val dx = bx - ax; val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq < 1e-12) return GridConfig.approxMeters(p.latitude, p.longitude, a.latitude, a.longitude)
        val t = ((px - ax) * dx + (py - ay) * dy) / lenSq
        val tt = t.coerceIn(0.0, 1.0)
        val fx = ax + tt * dx; val fy = ay + tt * dy

        val ddx = (px - fx) / cosMid * 111_320.0 * cosMid 
        val ddy = (py - fy) * 111_320.0
        return sqrt(ddx * ddx + ddy * ddy)
    }

    private fun chaikinIter(points: List<LatLng>): List<LatLng> {
        if (points.size < 3) return points
        val out = ArrayList<LatLng>(points.size * 2)
        out.add(points.first()) 
        for (i in 0 until points.size - 1) {
            val p = points[i]
            val q = points[i + 1]

            out.add(LatLng(p.latitude * 0.75 + q.latitude * 0.25, p.longitude * 0.75 + q.longitude * 0.25))
            out.add(LatLng(p.latitude * 0.25 + q.latitude * 0.75, p.longitude * 0.25 + q.longitude * 0.75))
        }
        out.add(points.last()) 
        return out
    }

    private fun validateAndFix(smoothed: List<LatLng>, fallback: List<LatLng>): List<LatLng> {
        if (smoothed.size < 2) return smoothed

        val result = ArrayList<LatLng>(smoothed.size)
        result.add(smoothed.first())

        for (i in 0 until smoothed.size - 1) {
            val a = smoothed[i]
            val b = smoothed[i + 1]
            val len = GridConfig.approxMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val steps = kotlin.math.max(2, (len / VALIDATION_SAMPLE_M).toInt())
            var segmentCrossesLand = false
            for (s in 1..steps) {
                val t = s.toDouble() / steps
                val lat = a.latitude + (b.latitude - a.latitude) * t
                val lon = a.longitude + (b.longitude - a.longitude) * t
                if (SeaMask.cellAtLatLng(lat, lon).isBlocked) {
                    segmentCrossesLand = true
                    break
                }
            }

            if (segmentCrossesLand) {

                val midPoints = nearestFallbackSegment(a, b, fallback)
                result.addAll(midPoints)
            }
            result.add(b)
        }
        return result
    }

    private fun nearestFallbackSegment(a: LatLng, b: LatLng, fallback: List<LatLng>): List<LatLng> {
        if (fallback.size < 2) return emptyList()
        val iA = fallback.indices.minBy {
            val d = GridConfig.approxMeters(a.latitude, a.longitude, fallback[it].latitude, fallback[it].longitude)
            d
        }
        val iB = fallback.indices.minBy {
            val d = GridConfig.approxMeters(b.latitude, b.longitude, fallback[it].latitude, fallback[it].longitude)
            d
        }
        val lo = min(iA, iB)
        val hi = max(iA, iB)
        if (hi - lo <= 1) return emptyList()
        return fallback.subList(lo + 1, hi)
    }

    private fun min(a: Int, b: Int) = if (a < b) a else b
    private fun max(a: Int, b: Int) = if (a > b) a else b
}
