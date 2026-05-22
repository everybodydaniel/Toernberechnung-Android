package com.example.trnberechnung.routing.v2

import android.content.Context
import com.example.trnberechnung.logic.RouterLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object SeaMask {

    private const val TAG = "SeaMask"

    const val DEPTH_SCALE = 10

    private lateinit var cells: ByteArray
    private lateinit var chartDepth: ShortArray

    private lateinit var buoyPositions: FloatArray

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady

    @Volatile
    private var isBuilding = false

    fun build(context: Context) {
        if (isBuilding) {
            RouterLog.i(TAG, "build() already in progress — skipping")
            return
        }
        isBuilding = true
        _isReady.value = false
        try {
            val t0 = System.currentTimeMillis()
            val result = SeaMaskBuilder.build(context.applicationContext)
            cells = result.cells
            chartDepth = result.chartDepth
            buoyPositions = result.buoyPositions
            val dt = System.currentTimeMillis() - t0
            RouterLog.i(
                TAG,
                "SeaMask built in ${dt}ms — ${GridConfig.ROWS}×${GridConfig.COLS} grid, " +
                    "${buoyPositions.size / 2} buoys indexed"
            )
            _isReady.value = true
        } catch (e: Exception) {
            RouterLog.w(TAG, "SeaMask build failed", e)
        } finally {
            isBuilding = false
        }
    }

    fun cellAt(row: Int, col: Int): CellType {
        if (!::cells.isInitialized) return CellType.OPEN_SEA
        if (!GridConfig.inBounds(row, col)) return CellType.OPEN_SEA
        return CellType.fromByte(cells[GridConfig.index(row, col)])
    }

    fun cellAtLatLng(lat: Double, lon: Double): CellType {
        if (!GridConfig.inBounds(lat, lon)) return CellType.OPEN_SEA
        return cellAt(GridConfig.latToRow(lat), GridConfig.lonToCol(lon))
    }

    fun depthAt(row: Int, col: Int): Double {
        if (!::chartDepth.isInitialized) return 5.0
        if (!GridConfig.inBounds(row, col)) return 10.0
        return chartDepth[GridConfig.index(row, col)].toDouble() / DEPTH_SCALE
    }

    fun depthAtLatLng(lat: Double, lon: Double): Double {
        if (!GridConfig.inBounds(lat, lon)) return 10.0
        return depthAt(GridConfig.latToRow(lat), GridConfig.lonToCol(lon))
    }

    fun isNavigable(row: Int, col: Int): Boolean = !cellAt(row, col).isBlocked

    fun isNavigable(lat: Double, lon: Double): Boolean = !cellAtLatLng(lat, lon).isBlocked

    internal fun setCell(row: Int, col: Int, type: CellType) {
        if (!::cells.isInitialized) return
        if (!GridConfig.inBounds(row, col)) return
        cells[GridConfig.index(row, col)] = type.ordinal.toByte()
    }

    internal fun setDepth(row: Int, col: Int, depthMeters: Double) {
        if (!::chartDepth.isInitialized) return
        if (!GridConfig.inBounds(row, col)) return
        val scaled = (depthMeters * DEPTH_SCALE).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
        chartDepth[GridConfig.index(row, col)] = scaled.toShort()
    }

    fun nearestBuoyDistanceMeters(lat: Double, lon: Double): Double {
        if (!::buoyPositions.isInitialized || buoyPositions.isEmpty()) return Double.MAX_VALUE
        var best = Double.MAX_VALUE
        var i = 0
        while (i < buoyPositions.size) {
            val bLat = buoyPositions[i].toDouble()
            val bLon = buoyPositions[i + 1].toDouble()
            val d = GridConfig.approxMeters(lat, lon, bLat, bLon)
            if (d < best) best = d
            i += 2
        }
        return best
    }

    fun buoyCount(): Int =
        if (::buoyPositions.isInitialized) buoyPositions.size / 2 else 0

    val ready: Boolean get() = _isReady.value
}
