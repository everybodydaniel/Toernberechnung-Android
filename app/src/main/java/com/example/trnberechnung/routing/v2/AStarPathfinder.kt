package com.example.trnberechnung.routing.v2

import com.example.trnberechnung.logic.RouterLog
import org.maplibre.android.geometry.LatLng
import java.util.PriorityQueue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AStarPathfinder {

    companion object {
        private const val TAG = "AStarPathfinder"

        private const val MAX_EXPANSIONS = 600_000

        private const val STRAIGHT_COST = 1.0

        private val DIAGONAL_COST = sqrt(2.0)

        private const val MIN_COST_MULTIPLIER = 0.85

        private val DR = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
        private val DC = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
        private val IS_DIAG = booleanArrayOf(true, false, true, false, false, true, false, true)
    }

    fun findPath(start: LatLng, end: LatLng): List<LatLng>? {
        if (!SeaMask.ready) {
            RouterLog.w(TAG, "SeaMask not ready — cannot route")
            return null
        }

        val startCell = snapToNavigable(start)
        val endCell = snapToNavigable(end)
        if (startCell == null || endCell == null) {
            RouterLog.w(TAG, "Start or end snap-to-water failed (start=$start, end=$end)")
            return null
        }

        val (sr, sc) = startCell
        val (er, ec) = endCell

        if (sr == er && sc == ec) {
            return listOf(start, end)
        }

        val gScore = HashMap<Int, Double>()
        val parent = HashMap<Int, Int>()
        val closed = HashSet<Int>()
        val open = PriorityQueue<DoubleArray>(compareBy { it[2] })

        val startIdx = GridConfig.index(sr, sc)
        val endIdx = GridConfig.index(er, ec)
        gScore[startIdx] = 0.0
        open.add(doubleArrayOf(sr.toDouble(), sc.toDouble(), heuristic(sr, sc, er, ec)))

        var expansions = 0
        var found = false

        while (open.isNotEmpty()) {
            val node = open.poll()!!
            val r = node[0].toInt()
            val c = node[1].toInt()
            val curIdx = GridConfig.index(r, c)

            if (curIdx in closed) continue
            closed.add(curIdx)
            expansions++

            if (curIdx == endIdx) { found = true; break }
            if (expansions >= MAX_EXPANSIONS) {
                RouterLog.w(TAG, "A* expansion limit reached ($MAX_EXPANSIONS) — giving up")
                break
            }

            val curG = gScore[curIdx] ?: continue

            for (d in 0..7) {
                val nr = r + DR[d]
                val nc = c + DC[d]
                if (!GridConfig.inBounds(nr, nc)) continue

                val neighborCell = SeaMask.cellAt(nr, nc)
                if (neighborCell.isBlocked) continue

                if (IS_DIAG[d]) {
                    if (SeaMask.cellAt(r, nc).isBlocked && SeaMask.cellAt(nr, c).isBlocked) continue

                    if (SeaMask.cellAt(r, nc).isBlocked || SeaMask.cellAt(nr, c).isBlocked) continue
                }

                val stepCost = if (IS_DIAG[d]) DIAGONAL_COST else STRAIGHT_COST
                val tentativeG = curG + stepCost * neighborCell.cost

                val nIdx = GridConfig.index(nr, nc)
                val knownG = gScore[nIdx]
                if (knownG != null && tentativeG >= knownG) continue

                gScore[nIdx] = tentativeG
                parent[nIdx] = curIdx
                val h = heuristic(nr, nc, er, ec)
                open.add(doubleArrayOf(nr.toDouble(), nc.toDouble(), tentativeG + h))
            }
        }

        if (!found) {
            RouterLog.w(TAG, "A* exhausted without reaching goal (expansions=$expansions)")
            return null
        }

        val gridPath = mutableListOf<Int>()
        var cur: Int? = endIdx
        while (cur != null) {
            gridPath.add(cur)
            cur = parent[cur]
        }
        gridPath.reverse()

        RouterLog.d(TAG, "A* found path: ${gridPath.size} cells, expansions=$expansions")

        val latLngPath = mutableListOf<LatLng>()
        latLngPath.add(start)
        for (idx in gridPath) {
            val row = idx / GridConfig.COLS
            val col = idx % GridConfig.COLS
            latLngPath.add(LatLng(GridConfig.rowToLat(row), GridConfig.colToLon(col)))
        }
        latLngPath.add(end)
        return latLngPath
    }

    private fun heuristic(r1: Int, c1: Int, r2: Int, c2: Int): Double {
        val dr = kotlin.math.abs(r1 - r2)
        val dc = kotlin.math.abs(c1 - c2)
        val straight = STRAIGHT_COST * MIN_COST_MULTIPLIER
        val diagonal = DIAGONAL_COST * MIN_COST_MULTIPLIER
        return straight * (dr + dc) + (diagonal - 2 * straight) * min(dr, dc)
    }

    private fun snapToNavigable(p: LatLng): Pair<Int, Int>? {
        if (!GridConfig.inBounds(p.latitude, p.longitude)) return null
        val r0 = GridConfig.latToRow(p.latitude)
        val c0 = GridConfig.lonToCol(p.longitude)
        if (SeaMask.isNavigable(r0, c0)) return r0 to c0

        val maxRadius = 80
        for (radius in 1..maxRadius) {
            val rMin = max(0, r0 - radius)
            val rMax = min(GridConfig.ROWS - 1, r0 + radius)
            val cMin = max(0, c0 - radius)
            val cMax = min(GridConfig.COLS - 1, c0 + radius)

            for (r in rMin..rMax) {
                for (c in cMin..cMax) {
                    val ringDist = max(kotlin.math.abs(r - r0), kotlin.math.abs(c - c0))
                    if (ringDist != radius) continue
                    if (SeaMask.isNavigable(r, c)) {
                        RouterLog.d(TAG, "Snapped $p to nearest water cell at radius $radius")
                        return r to c
                    }
                }
            }
        }
        return null
    }
}
