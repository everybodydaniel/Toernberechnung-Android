package com.example.trnberechnung.routing.v2

import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI

object GridConfig {

    const val ROWS = 800

    const val COLS = 1500

    const val MIN_LAT = 53.30
    const val MAX_LAT = 54.00
    const val MIN_LON = 6.30
    const val MAX_LON = 8.30

    const val LAT_STEP = (MAX_LAT - MIN_LAT) / ROWS

    const val LON_STEP = (MAX_LON - MIN_LON) / COLS

    private const val MID_LAT_RAD = (MIN_LAT + MAX_LAT) / 2.0 * PI / 180.0

    private const val METERS_PER_LAT_DEG = 111320.0

    private val METERS_PER_LON_DEG = METERS_PER_LAT_DEG * cos(MID_LAT_RAD)

    fun latToRow(lat: Double): Int {
        val r = ((lat - MIN_LAT) / LAT_STEP).toInt()
        return max(0, min(ROWS - 1, r))
    }

    fun lonToCol(lon: Double): Int {
        val c = ((lon - MIN_LON) / LON_STEP).toInt()
        return max(0, min(COLS - 1, c))
    }

    fun rowToLat(row: Int): Double = MIN_LAT + (row + 0.5) * LAT_STEP

    fun colToLon(col: Int): Double = MIN_LON + (col + 0.5) * LON_STEP

    fun inBounds(row: Int, col: Int): Boolean =
        row in 0 until ROWS && col in 0 until COLS

    fun inBounds(lat: Double, lon: Double): Boolean =
        lat in MIN_LAT..MAX_LAT && lon in MIN_LON..MAX_LON

    fun index(row: Int, col: Int): Int = row * COLS + col

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = (lat2 - lat1) * PI / 180.0
        val dLon = (lon2 - lon1) * PI / 180.0
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
            cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) *
            kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    fun approxMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dx = (lon2 - lon1) * METERS_PER_LON_DEG
        val dy = (lat2 - lat1) * METERS_PER_LAT_DEG
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}
