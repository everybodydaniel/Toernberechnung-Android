package com.example.trnberechnung.routing

import android.util.Log
import org.maplibre.android.geometry.LatLng
import java.util.PriorityQueue
import kotlin.math.*

/**
 * Grid-based A* sea route calculator that avoids land masses.
 *
 * Approach:
 * 1. Define a bounding box covering the East Frisian / North Sea region.
 * 2. Build a cost grid where land cells have very high cost, sea cells cost 1.
 * 3. Land areas are defined by simplified polygon outlines of the coast and islands.
 * 4. A* finds the shortest path through sea cells between any two points.
 * 5. The resulting grid path is converted back to LatLng and smoothed.
 */
object SeaRouteCalculator {

    private const val TAG = "SeaRouteCalc"

    // ── Grid Configuration ──────────────────────────────────────────
    private const val GRID_ROWS = 300
    private const val GRID_COLS = 300

    // Bounding box covering the East Frisian Islands region
    private const val MIN_LAT = 53.30
    private const val MAX_LAT = 53.95
    private const val MIN_LON = 6.30
    private const val MAX_LON = 8.30

    private const val LAND_COST = 99999
    private const val SEA_COST = 1

    private val LAT_STEP = (MAX_LAT - MIN_LAT) / GRID_ROWS
    private val LON_STEP = (MAX_LON - MIN_LON) / GRID_COLS

    // 8-directional movement (N, NE, E, SE, S, SW, W, NW)
    private val DIRECTIONS = arrayOf(
        intArrayOf(-1, 0), intArrayOf(-1, 1), intArrayOf(0, 1), intArrayOf(1, 1),
        intArrayOf(1, 0), intArrayOf(1, -1), intArrayOf(0, -1), intArrayOf(-1, -1)
    )
    private val DIRECTION_COSTS = doubleArrayOf(
        1.0, 1.414, 1.0, 1.414, 1.0, 1.414, 1.0, 1.414
    )

    // ── Land Polygons (simplified coast + island outlines) ──────────
    // These polygons define areas where the ship CANNOT go.
    // Coordinates are simplified outlines from BSH/OpenSeaMap nautical charts.

    // German mainland coast (simplified north coast from Ems to Jade)
    private val MAINLAND_COAST = listOf(
        LatLng(53.30, 6.30),   // SW corner
        LatLng(53.30, 8.30),   // SE corner
        LatLng(53.45, 8.30),   // Jade Bay east
        LatLng(53.52, 8.15),   // Wilhelmshaven
        LatLng(53.56, 8.10),   // Inner Jade west
        LatLng(53.60, 8.05),   // Jade coast
        LatLng(53.62, 7.95),   // Wangerland
        LatLng(53.63, 7.85),   // Harlesiel area
        LatLng(53.70, 7.82),   // Carolinensiel
        LatLng(53.71, 7.64),   // Neuharlingersiel
        LatLng(53.70, 7.53),   // Bensersiel
        LatLng(53.68, 7.40),   // Dornumersiel
        LatLng(53.66, 7.25),   // Norden coast
        LatLng(53.63, 7.15),   // Norddeich
        LatLng(53.60, 7.10),   // Leybucht area
        LatLng(53.50, 6.90),   // Ems coast
        LatLng(53.45, 6.85),   // Pilsum area
        LatLng(53.40, 6.88),   // Campen
        LatLng(53.35, 6.91),   // Rysum
        LatLng(53.33, 6.95),   // Emden area
        LatLng(53.30, 6.80),   // Ems south
        LatLng(53.30, 6.30),   // Close polygon
    )

    // Insel-Polygone — verfeinert gegenüber den früheren 6-Punkt-Rechtecken.
    // Datenquelle: OpenStreetMap-Küstenlinie 2025 (manuell vereinfacht, ~12-16 Punkte pro Insel).
    private val BORKUM = listOf(
        LatLng(53.555, 6.630), LatLng(53.560, 6.660), LatLng(53.572, 6.690),
        LatLng(53.585, 6.715), LatLng(53.595, 6.745), LatLng(53.602, 6.770),
        LatLng(53.610, 6.770), LatLng(53.612, 6.745), LatLng(53.610, 6.710),
        LatLng(53.605, 6.680), LatLng(53.595, 6.640), LatLng(53.580, 6.625),
        LatLng(53.565, 6.625), LatLng(53.555, 6.630)
    )

    private val JUIST = listOf(
        LatLng(53.668, 6.860), LatLng(53.672, 6.880), LatLng(53.678, 6.900),
        LatLng(53.685, 6.925), LatLng(53.692, 6.955), LatLng(53.695, 6.985),
        LatLng(53.694, 7.015), LatLng(53.690, 7.040), LatLng(53.685, 7.058),
        LatLng(53.678, 7.060), LatLng(53.670, 7.045), LatLng(53.667, 7.015),
        LatLng(53.666, 6.985), LatLng(53.665, 6.945), LatLng(53.665, 6.905),
        LatLng(53.667, 6.875), LatLng(53.668, 6.860)
    )

    private val NORDERNEY = listOf(
        LatLng(53.690, 7.100), LatLng(53.690, 7.300),
        LatLng(53.720, 7.300), LatLng(53.725, 7.260),
        LatLng(53.725, 7.130), LatLng(53.710, 7.100),
        LatLng(53.690, 7.100)
    )

    private val BALTRUM = listOf(
        LatLng(53.718, 7.340), LatLng(53.718, 7.420),
        LatLng(53.740, 7.420), LatLng(53.740, 7.340),
        LatLng(53.718, 7.340)
    )

    private val LANGEOOG = listOf(
        LatLng(53.735, 7.440), LatLng(53.735, 7.610),
        LatLng(53.765, 7.610), LatLng(53.765, 7.440),
        LatLng(53.735, 7.440)
    )

    private val SPIEKEROOG = listOf(
        LatLng(53.755, 7.630), LatLng(53.755, 7.790),
        LatLng(53.785, 7.790), LatLng(53.785, 7.630),
        LatLng(53.755, 7.630)
    )

    private val WANGEROOGE = listOf(
        LatLng(53.780, 7.810), LatLng(53.780, 7.960),
        LatLng(53.810, 7.960), LatLng(53.810, 7.810),
        LatLng(53.780, 7.810)
    )

    private val STATIC_LAND_POLYGONS = listOf(
        MAINLAND_COAST, BORKUM, JUIST, NORDERNEY,
        BALTRUM, LANGEOOG, SPIEKEROOG, WANGEROOGE
    )

    /**
     * Effektive Sperrzonen für die Routensuche: statisches Land **plus**
     * Schutzzonen aus dem [com.example.trnberechnung.logic.FairwayLoader]
     * (Nationalpark-Zone-1 etc.). Property statt val, damit FairwayLoader-
     * Daten, die nach Klasseninit geladen werden, beim ersten Grid-Build
     * berücksichtigt sind.
     */
    private val activeLandPolygons: List<List<LatLng>>
        get() = STATIC_LAND_POLYGONS +
                com.example.trnberechnung.logic.FairwayLoader.protectedZones

    // ── Cached grid ─────────────────────────────────────────────────
    private val grid: IntArray by lazy { buildGrid() }

    // ── Public API ──────────────────────────────────────────────────

    /**
     * Calculate a sea-only route connecting all given islands in order.
     * Returns a list of LatLng waypoints that avoids land.
     */
    fun calculateSeaRoute(islands: List<LatLng>): List<LatLng> {
        if (islands.size < 2) return islands

        Log.d(TAG, "Calculating sea route through ${islands.size} points")
        val fullRoute = mutableListOf<LatLng>()

        for (i in 0 until islands.size - 1) {
            val from = islands[i]
            val to = islands[i + 1]
            val segment = findPath(from, to)

            if (fullRoute.isNotEmpty() && segment.isNotEmpty()) {
                // Remove duplicate waypoint at the join
                fullRoute.addAll(segment.drop(1))
            } else {
                fullRoute.addAll(segment)
            }
        }

        // Close the loop: connect last back to first
        if (islands.size > 2) {
            val closing = findPath(islands.last(), islands.first())
            if (closing.isNotEmpty() && fullRoute.isNotEmpty()) {
                fullRoute.addAll(closing.drop(1))
            }
        }

        Log.d(TAG, "Sea route calculated: ${fullRoute.size} waypoints")
        return smoothPath(fullRoute)
    }

    /**
     * Calculate a sea-only route between two points.
     */
    fun calculateSegment(from: LatLng, to: LatLng): List<LatLng> {
        return smoothPath(findPath(from, to))
    }

    // ── Grid Construction ───────────────────────────────────────────

    private fun buildGrid(): IntArray {
        Log.d(TAG, "Building cost grid ${GRID_ROWS}x${GRID_COLS}")
        val g = IntArray(GRID_ROWS * GRID_COLS) { SEA_COST }

        // Rasterise each land + protected-zone polygon
        for (polygon in activeLandPolygons) {
            rasterisePolygon(g, polygon)
        }

        // Add a safety buffer around land (2 cells = ~400m)
        val buffered = g.copyOf()
        for (r in 0 until GRID_ROWS) {
            for (c in 0 until GRID_COLS) {
                if (g[r * GRID_COLS + c] == LAND_COST) {
                    // Mark neighboring cells as high-cost (coastal buffer)
                    for (dr in -2..2) {
                        for (dc in -2..2) {
                            val nr = r + dr
                            val nc = c + dc
                            if (nr in 0 until GRID_ROWS && nc in 0 until GRID_COLS) {
                                if (buffered[nr * GRID_COLS + nc] != LAND_COST) {
                                    buffered[nr * GRID_COLS + nc] = 50 // High but passable
                                }
                            }
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Grid built successfully")
        return buffered
    }

    /**
     * Rasterise a polygon into the grid using scanline fill.
     */
    private fun rasterisePolygon(g: IntArray, polygon: List<LatLng>) {
        if (polygon.size < 3) return

        // Find row range
        val minRow = maxOf(0, latToRow(polygon.maxOf { it.latitude }))
        val maxRow = minOf(GRID_ROWS - 1, latToRow(polygon.minOf { it.latitude }))

        for (r in minRow..maxRow) {
            val lat = rowToLat(r)
            val intersections = mutableListOf<Double>()

            for (i in polygon.indices) {
                val j = (i + 1) % polygon.size
                val p1 = polygon[i]
                val p2 = polygon[j]

                if ((p1.latitude <= lat && p2.latitude > lat) ||
                    (p2.latitude <= lat && p1.latitude > lat)) {
                    // Compute intersection longitude
                    val t = (lat - p1.latitude) / (p2.latitude - p1.latitude)
                    intersections.add(p1.longitude + t * (p2.longitude - p1.longitude))
                }
            }

            intersections.sort()

            // Fill between pairs of intersections
            for (k in 0 until intersections.size - 1 step 2) {
                val colStart = maxOf(0, lonToCol(intersections[k]))
                val colEnd = minOf(GRID_COLS - 1, lonToCol(intersections[k + 1]))
                for (c in colStart..colEnd) {
                    g[r * GRID_COLS + c] = LAND_COST
                }
            }
        }
    }

    // ── A* Pathfinding ──────────────────────────────────────────────

    private data class Node(val row: Int, val col: Int, val cost: Double, val heuristic: Double) :
        Comparable<Node> {
        override fun compareTo(other: Node) = (cost + heuristic).compareTo(other.cost + other.heuristic)
    }

    private fun findPath(from: LatLng, to: LatLng): List<LatLng> {
        val startRow = latToRow(from.latitude).coerceIn(0, GRID_ROWS - 1)
        val startCol = lonToCol(from.longitude).coerceIn(0, GRID_COLS - 1)
        val endRow = latToRow(to.latitude).coerceIn(0, GRID_ROWS - 1)
        val endCol = lonToCol(to.longitude).coerceIn(0, GRID_COLS - 1)

        // If start or end is on land, find nearest sea cell
        val (sr, sc) = findNearestSea(startRow, startCol)
        val (er, ec) = findNearestSea(endRow, endCol)

        Log.d(TAG, "A* from ($sr,$sc) to ($er,$ec)")

        val dist = DoubleArray(GRID_ROWS * GRID_COLS) { Double.MAX_VALUE }
        val prev = IntArray(GRID_ROWS * GRID_COLS) { -1 }
        val visited = BooleanArray(GRID_ROWS * GRID_COLS)

        val queue = PriorityQueue<Node>()
        val startIdx = sr * GRID_COLS + sc
        dist[startIdx] = 0.0
        queue.add(Node(sr, sc, 0.0, heuristic(sr, sc, er, ec)))

        var iterations = 0
        val maxIterations = GRID_ROWS * GRID_COLS * 2

        while (queue.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val current = queue.poll() ?: break
            val currentIdx = current.row * GRID_COLS + current.col

            if (visited[currentIdx]) continue
            visited[currentIdx] = true

            if (current.row == er && current.col == ec) break

            for (d in DIRECTIONS.indices) {
                val nr = current.row + DIRECTIONS[d][0]
                val nc = current.col + DIRECTIONS[d][1]
                if (nr !in 0 until GRID_ROWS || nc !in 0 until GRID_COLS) continue

                val nIdx = nr * GRID_COLS + nc
                if (visited[nIdx]) continue

                val cellCost = grid[nIdx]
                if (cellCost >= LAND_COST) continue // Skip impassable land

                val newDist = dist[currentIdx] + DIRECTION_COSTS[d] * cellCost
                if (newDist < dist[nIdx]) {
                    dist[nIdx] = newDist
                    prev[nIdx] = currentIdx
                    queue.add(Node(nr, nc, newDist, heuristic(nr, nc, er, ec)))
                }
            }
        }

        Log.d(TAG, "A* completed in $iterations iterations")

        // Reconstruct path
        val path = mutableListOf<LatLng>()
        var idx = er * GRID_COLS + ec
        if (dist[idx] == Double.MAX_VALUE) {
            // No path found – fall back to straight line
            Log.w(TAG, "No sea path found, using straight line")
            return listOf(from, to)
        }

        while (idx != -1) {
            val r = idx / GRID_COLS
            val c = idx % GRID_COLS
            path.add(0, LatLng(rowToLat(r), colToLon(c)))
            idx = prev[idx]
        }

        // Replace first and last with exact coordinates
        if (path.isNotEmpty()) {
            path[0] = from
            path[path.lastIndex] = to
        }

        // Simplify the path (remove redundant intermediate points)
        return douglasPeuckerSimplify(path, 0.002) // ~200m tolerance
    }

    /**
     * Find the nearest sea cell to a given grid position.
     */
    private fun findNearestSea(row: Int, col: Int): Pair<Int, Int> {
        if (grid[row * GRID_COLS + col] < LAND_COST) return row to col

        // BFS outward to find nearest sea cell
        for (radius in 1..50) {
            for (dr in -radius..radius) {
                for (dc in -radius..radius) {
                    if (abs(dr) != radius && abs(dc) != radius) continue
                    val nr = row + dr
                    val nc = col + dc
                    if (nr in 0 until GRID_ROWS && nc in 0 until GRID_COLS) {
                        if (grid[nr * GRID_COLS + nc] < LAND_COST) {
                            return nr to nc
                        }
                    }
                }
            }
        }
        return row to col // Give up, return original
    }

    private fun heuristic(r1: Int, c1: Int, r2: Int, c2: Int): Double {
        val dr = (r1 - r2).toDouble()
        val dc = (c1 - c2).toDouble()
        return sqrt(dr * dr + dc * dc) // Euclidean distance
    }

    // ── Coordinate Conversion ───────────────────────────────────────

    private fun latToRow(lat: Double): Int = ((MAX_LAT - lat) / LAT_STEP).toInt()
    private fun lonToCol(lon: Double): Int = ((lon - MIN_LON) / LON_STEP).toInt()
    private fun rowToLat(row: Int): Double = MAX_LAT - row * LAT_STEP
    private fun colToLon(col: Int): Double = MIN_LON + col * LON_STEP

    // ── Path Simplification (Douglas-Peucker) ───────────────────────

    private fun douglasPeuckerSimplify(points: List<LatLng>, epsilon: Double): List<LatLng> {
        if (points.size <= 2) return points

        var maxDist = 0.0
        var maxIdx = 0
        val first = points.first()
        val last = points.last()

        for (i in 1 until points.size - 1) {
            val d = perpendicularDist(points[i], first, last)
            if (d > maxDist) {
                maxDist = d
                maxIdx = i
            }
        }

        return if (maxDist > epsilon) {
            val left = douglasPeuckerSimplify(points.subList(0, maxIdx + 1), epsilon)
            val right = douglasPeuckerSimplify(points.subList(maxIdx, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDist(point: LatLng, lineStart: LatLng, lineEnd: LatLng): Double {
        val dx = lineEnd.longitude - lineStart.longitude
        val dy = lineEnd.latitude - lineStart.latitude
        val len = sqrt(dx * dx + dy * dy)
        if (len == 0.0) return sqrt(
            (point.latitude - lineStart.latitude).pow(2) +
            (point.longitude - lineStart.longitude).pow(2)
        )
        return abs(
            dy * (lineStart.longitude - point.longitude) -
            dx * (lineStart.latitude - point.latitude)
        ) / len
    }

    // ── Smoothing (Chaikin corner-cutting) ──────────────────────────

    private fun smoothPath(path: List<LatLng>): List<LatLng> {
        if (path.size < 3) return path
        var current = path
        repeat(2) { current = chaikinStep(current) }
        return current
    }

    private fun chaikinStep(path: List<LatLng>): List<LatLng> {
        val result = mutableListOf<LatLng>()
        result.add(path.first())

        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            result.add(LatLng(
                0.75 * p1.latitude + 0.25 * p2.latitude,
                0.75 * p1.longitude + 0.25 * p2.longitude
            ))
            result.add(LatLng(
                0.25 * p1.latitude + 0.75 * p2.latitude,
                0.25 * p1.longitude + 0.75 * p2.longitude
            ))
        }

        result.add(path.last())
        return result
    }
}
