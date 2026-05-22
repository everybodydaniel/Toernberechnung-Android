package com.example.trnberechnung.routing.v2

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import com.example.trnberechnung.logic.RouterLog
import org.maplibre.android.geometry.LatLng
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.roundToInt

internal object SeaMaskBuilder {

    private const val TAG = "SeaMaskBuilder"
    private const val ASSET_PATH = "fairways/east_frisia_osm.geojson"
    private const val DEMO_ASSET = "fairways/east_frisia.geojson"
    private const val NORDSBEFV_ASSET = "fairways/nordsbefv.geojson"

    private const val NORDSBEFV_ROUTE_BUFFER_M = 250.0

    private const val BUOY_RADIUS_M = 250.0

    private const val DEFAULT_SEA_DEPTH_M = 5.0

    data class BuildResult(
        val cells: ByteArray,
        val chartDepth: ShortArray,
        val buoyPositions: FloatArray
    )

    private data class Polygon(val points: List<LatLng>, val seamarkType: String)

    private data class BuoyPoint(val lat: Double, val lon: Double)

    fun build(context: Context): BuildResult {
        val n = GridConfig.ROWS * GridConfig.COLS
        val cells = ByteArray(n) { CellType.OPEN_SEA.ordinal.toByte() }
        val depth = ShortArray(n) { (DEFAULT_SEA_DEPTH_M * SeaMask.DEPTH_SCALE).toInt().toShort() }

        val fairwayPolys = mutableListOf<Polygon>()
        val harbourPolys = mutableListOf<Polygon>()
        val restrictedPolys = mutableListOf<Polygon>()
        val buoys = mutableListOf<BuoyPoint>()

        try {
            context.assets.open(ASSET_PATH).use { inputStream ->
                JsonReader(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))).use { reader ->
                    parseFeatureCollection(reader, fairwayPolys, harbourPolys, restrictedPolys, buoys)
                }
            }
            RouterLog.i(
                TAG,
                "OSM parsed: ${fairwayPolys.size} fairway, ${harbourPolys.size} harbour, " +
                    "${restrictedPolys.size} restricted polygons, ${buoys.size} lateral buoys"
            )
        } catch (e: Exception) {
            RouterLog.w(TAG, "Failed to parse $ASSET_PATH — sea mask will be sparse", e)
        }

        try {
            context.assets.open(DEMO_ASSET).use { inputStream ->
                JsonReader(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))).use { reader ->
                    parseDemoLineStringFairways(reader, cells)
                }
            }
        } catch (_: Exception) {

        }

        for (p in fairwayPolys) rasterizePolygon(p.points, cells, CellType.FAIRWAY, overwriteLand = false)

        for (poly in IslandPolygons.ALL) rasterizePolygon(poly, cells, CellType.LAND, overwriteLand = true)

        for (poly in IslandPolygons.WATER_OVERRIDES) {
            rasterizePolygon(poly, cells, CellType.OPEN_SEA, overwriteLand = true)
        }

        for (p in harbourPolys) rasterizePolygon(p.points, cells, CellType.HARBOUR, overwriteLand = true)

        rasterizePolygon(
            IslandPolygons.FESTLAND_BACKSTOP_WANGERLAND, cells,
            CellType.LAND, overwriteLand = true
        )
        rasterizePolygon(
            IslandPolygons.FESTLAND_BACKSTOP_HARLESIEL, cells,
            CellType.LAND, overwriteLand = true
        )

        stampBuoyProximity(buoys, cells, BUOY_RADIUS_M)

        for (p in restrictedPolys) rasterizePolygon(p.points, cells, CellType.RESTRICTED, overwriteLand = false)

        val nordSchutzPolys = mutableListOf<List<LatLng>>()
        val nordRouten = mutableListOf<List<LatLng>>()
        try {
            context.assets.open(NORDSBEFV_ASSET).use { inputStream ->
                JsonReader(BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))).use { reader ->
                    parseNordSBefVCollection(reader, nordSchutzPolys, nordRouten)
                }
            }
            RouterLog.i(
                TAG,
                "NordSBefV parsed: ${nordSchutzPolys.size} Schutzgebiete, " +
                    "${nordRouten.size} erlaubte Routen"
            )
        } catch (e: Exception) {
            RouterLog.w(TAG, "Failed to parse $NORDSBEFV_ASSET — keine NordSBefV-Zonen", e)
        }

        for (ring in nordSchutzPolys) rasterizePolygon(ring, cells, CellType.RUHEZONE, overwriteLand = false)

        for (ln in nordRouten) {
            stampLineStringBuffer(
                ln, cells, CellType.FAIRWAY,
                NORDSBEFV_ROUTE_BUFFER_M, overwriteRestricted = true
            )
        }

        for (ln in IslandPolygons.MANUAL_FAIRWAY_ROUTES) {
            stampLineStringBuffer(
                ln, cells, CellType.FAIRWAY,
                NORDSBEFV_ROUTE_BUFFER_M,
                overwriteRestricted = true, overwriteLand = true
            )
        }

        val buoyFlat = FloatArray(buoys.size * 2)
        buoys.forEachIndexed { i, b ->
            buoyFlat[2 * i] = b.lat.toFloat()
            buoyFlat[2 * i + 1] = b.lon.toFloat()
        }

        val typeCounts = IntArray(CellType.values().size)
        for (b in cells) typeCounts[(b.toInt() and 0xFF).coerceAtMost(typeCounts.size - 1)]++
        RouterLog.i(
            TAG,
            "Mask distribution: " + CellType.values().joinToString(", ") { ct ->
                "${ct.name}=${typeCounts[ct.ordinal]}"
            }
        )

        return BuildResult(cells = cells, chartDepth = depth, buoyPositions = buoyFlat)
    }

    private fun parseFeatureCollection(
        reader: JsonReader,
        fairwayPolys: MutableList<Polygon>,
        harbourPolys: MutableList<Polygon>,
        restrictedPolys: MutableList<Polygon>,
        buoys: MutableList<BuoyPoint>
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "features") {
                reader.beginArray()
                while (reader.hasNext()) {
                    parseFeature(reader, fairwayPolys, harbourPolys, restrictedPolys, buoys)
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun parseFeature(
        reader: JsonReader,
        fairwayPolys: MutableList<Polygon>,
        harbourPolys: MutableList<Polygon>,
        restrictedPolys: MutableList<Polygon>,
        buoys: MutableList<BuoyPoint>
    ) {
        reader.beginObject()
        var seamarkType: String? = null
        var pendingGeometry: PendingGeometry? = null

        while (reader.hasNext()) {
            when (reader.nextName()) {
                "properties" -> {
                    seamarkType = parsePropertiesForSeamarkType(reader)
                }
                "geometry" -> {
                    pendingGeometry = parseGeometry(reader)
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val st = seamarkType ?: return
        val geom = pendingGeometry ?: return

        when (st) {
            "fairway" -> {
                for (ring in geom.polygons) fairwayPolys.add(Polygon(ring, st))
            }
            "harbour", "harbour_basin", "small_craft_facility" -> {
                for (ring in geom.polygons) harbourPolys.add(Polygon(ring, st))
            }
            "restricted_area" -> {
                for (ring in geom.polygons) restrictedPolys.add(Polygon(ring, st))
            }
            "buoy_lateral", "beacon_lateral" -> {
                geom.point?.let { p ->
                    if (GridConfig.inBounds(p.latitude, p.longitude)) {
                        buoys.add(BuoyPoint(p.latitude, p.longitude))
                    }
                }
            }
        }
    }

    private fun parseNordSBefVCollection(
        reader: JsonReader,
        schutzPolys: MutableList<List<LatLng>>,
        routen: MutableList<List<LatLng>>
    ) {
        reader.beginObject()
        while (reader.hasNext()) {
            if (reader.nextName() == "features") {
                reader.beginArray()
                while (reader.hasNext()) parseNordSBefVFeature(reader, schutzPolys, routen)
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun parseNordSBefVFeature(
        reader: JsonReader,
        schutzPolys: MutableList<List<LatLng>>,
        routen: MutableList<List<LatLng>>
    ) {
        reader.beginObject()
        var name: String? = null
        var geom: PendingGeometry? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "properties" -> name = parsePropertiesForName(reader)
                "geometry" -> geom = parseGeometry(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        val nm = name?.trim() ?: return
        val g = geom ?: return

        when {
            nm.startsWith("Besonderes Schutzgebiet") -> {
                for (ring in g.polygons) if (ring.size >= 3) schutzPolys.add(ring)
            }
            (nm.startsWith("Schutzgebiets-Route") || nm.startsWith("Schnellfahrkorridor")) &&
                !nm.contains("nicht-maschinenangetriebene") -> {
                if (g.line.size >= 2) routen.add(g.line)
            }
        }
    }

    private fun parsePropertiesForName(reader: JsonReader): String? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        reader.beginObject()
        var nm: String? = null
        while (reader.hasNext()) {
            if (reader.nextName() == "name") {
                if (reader.peek() == JsonToken.STRING) nm = reader.nextString() else reader.skipValue()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return nm
    }

    private fun parsePropertiesForSeamarkType(reader: JsonReader): String? {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return null
        }
        reader.beginObject()
        var st: String? = null
        while (reader.hasNext()) {
            val name = reader.nextName()
            if (name == "seamark:type") {
                if (reader.peek() == JsonToken.STRING) st = reader.nextString() else reader.skipValue()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
        return st
    }

    private data class PendingGeometry(
        val point: LatLng? = null,
        val polygons: List<List<LatLng>> = emptyList(),
        val line: List<LatLng> = emptyList()
    )

    private fun parseGeometry(reader: JsonReader): PendingGeometry {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull()
            return PendingGeometry()
        }
        reader.beginObject()
        var type: String? = null
        var coords: Any? = null
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "type" -> type = reader.nextString()
                "coordinates" -> coords = readCoordinates(reader)
                else -> reader.skipValue()
            }
        }
        reader.endObject()

        return when (type) {
            "Point" -> {
                val p = coords as? DoubleArray
                if (p != null && p.size >= 2) PendingGeometry(point = LatLng(p[1], p[0])) else PendingGeometry()
            }
            "Polygon" -> {

                val rings = coords as? List<*> ?: return PendingGeometry()
                val outer = (rings.firstOrNull() as? List<*>) ?: return PendingGeometry()
                val pts = outer.mapNotNull {
                    val xy = it as? DoubleArray ?: return@mapNotNull null
                    if (xy.size >= 2) LatLng(xy[1], xy[0]) else null
                }
                if (pts.size < 3) PendingGeometry() else PendingGeometry(polygons = listOf(pts))
            }
            "LineString" -> {

                val list = coords as? List<*> ?: return PendingGeometry()
                val pts = list.mapNotNull {
                    val xy = it as? DoubleArray ?: return@mapNotNull null
                    if (xy.size >= 2) LatLng(xy[1], xy[0]) else null
                }
                if (pts.size < 2) PendingGeometry() else PendingGeometry(line = pts)
            }
            "MultiLineString" -> {

                val lines = coords as? List<*> ?: return PendingGeometry()
                val pts = lines.flatMap { ln ->
                    (ln as? List<*>)?.mapNotNull {
                        val xy = it as? DoubleArray ?: return@mapNotNull null
                        if (xy.size >= 2) LatLng(xy[1], xy[0]) else null
                    } ?: emptyList()
                }
                if (pts.size < 2) PendingGeometry() else PendingGeometry(line = pts)
            }
            "MultiPolygon" -> {

                val polys = coords as? List<*> ?: return PendingGeometry()
                val rings = polys.mapNotNull { polyRings ->
                    val ringList = polyRings as? List<*> ?: return@mapNotNull null
                    val outer = (ringList.firstOrNull() as? List<*>) ?: return@mapNotNull null
                    val pts = outer.mapNotNull {
                        val xy = it as? DoubleArray ?: return@mapNotNull null
                        if (xy.size >= 2) LatLng(xy[1], xy[0]) else null
                    }
                    if (pts.size >= 3) pts else null
                }
                PendingGeometry(polygons = rings)
            }
            else -> PendingGeometry()
        }
    }

    private fun readCoordinates(reader: JsonReader): Any {
        return when (reader.peek()) {
            JsonToken.BEGIN_ARRAY -> {
                reader.beginArray()
                val items = mutableListOf<Any>()

                if (reader.hasNext() && reader.peek() == JsonToken.NUMBER) {
                    val nums = mutableListOf<Double>()
                    while (reader.hasNext()) nums.add(reader.nextDouble())
                    reader.endArray()
                    return nums.toDoubleArray()
                }
                while (reader.hasNext()) items.add(readCoordinates(reader))
                reader.endArray()
                items
            }
            else -> {
                reader.skipValue()
                emptyList<Any>()
            }
        }
    }

    private fun parseDemoLineStringFairways(reader: JsonReader, cells: ByteArray) {
        reader.beginObject()
        while (reader.hasNext()) {
            val n = reader.nextName()
            if (n == "features") {
                reader.beginArray()
                while (reader.hasNext()) {
                    val pendingLine = parseLineStringFeature(reader)
                    if (pendingLine.size >= 2) {
                        stampLineStringBuffer(pendingLine, cells, CellType.FAIRWAY, bufferM = 200.0)
                    }
                }
                reader.endArray()
            } else {
                reader.skipValue()
            }
        }
        reader.endObject()
    }

    private fun parseLineStringFeature(reader: JsonReader): List<LatLng> {
        reader.beginObject()
        var pts: List<LatLng> = emptyList()
        while (reader.hasNext()) {
            when (reader.nextName()) {
                "geometry" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                    } else {
                        reader.beginObject()
                        var type: String? = null
                        var coords: Any? = null
                        while (reader.hasNext()) {
                            when (reader.nextName()) {
                                "type" -> type = reader.nextString()
                                "coordinates" -> coords = readCoordinates(reader)
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                        if (type == "LineString") {
                            val list = coords as? List<*>
                            pts = list?.mapNotNull {
                                val xy = it as? DoubleArray ?: return@mapNotNull null
                                if (xy.size >= 2) LatLng(xy[1], xy[0]) else null
                            } ?: emptyList()
                        }
                    }
                }
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return pts
    }

    private fun rasterizePolygon(
        polygon: List<LatLng>,
        cells: ByteArray,
        value: CellType,
        overwriteLand: Boolean
    ) {
        if (polygon.size < 3) return

        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (p in polygon) {
            if (p.latitude < minLat) minLat = p.latitude
            if (p.latitude > maxLat) maxLat = p.latitude
            if (p.longitude < minLon) minLon = p.longitude
            if (p.longitude > maxLon) maxLon = p.longitude
        }
        if (maxLat < GridConfig.MIN_LAT || minLat > GridConfig.MAX_LAT) return
        if (maxLon < GridConfig.MIN_LON || minLon > GridConfig.MAX_LON) return

        val rowMin = GridConfig.latToRow(minLat)
        val rowMax = GridConfig.latToRow(maxLat)
        val valByte = value.ordinal.toByte()
        val landByte = CellType.LAND.ordinal.toByte()

        val n = polygon.size
        val xs = DoubleArray(n) 
        val ys = DoubleArray(n) 
        for (i in 0 until n) {
            ys[i] = (polygon[i].latitude - GridConfig.MIN_LAT) / GridConfig.LAT_STEP
            xs[i] = (polygon[i].longitude - GridConfig.MIN_LON) / GridConfig.LON_STEP
        }

        val xIntersections = DoubleArray(n)
        for (row in rowMin..rowMax) {
            val y = row + 0.5
            var ixCount = 0
            for (i in 0 until n) {
                val j = if (i == 0) n - 1 else i - 1
                val yi = ys[i]
                val yj = ys[j]
                if ((yi > y) != (yj > y)) {
                    val t = (y - yi) / (yj - yi)
                    val xx = xs[i] + t * (xs[j] - xs[i])
                    if (ixCount < xIntersections.size) xIntersections[ixCount++] = xx
                }
            }

            for (a in 0 until ixCount - 1) {
                for (b in a + 1 until ixCount) {
                    if (xIntersections[a] > xIntersections[b]) {
                        val tmp = xIntersections[a]
                        xIntersections[a] = xIntersections[b]
                        xIntersections[b] = tmp
                    }
                }
            }

            var k = 0
            while (k + 1 < ixCount) {
                val cStart = max(0.0, xIntersections[k]).toInt()
                val cEnd = min((GridConfig.COLS - 1).toDouble(), xIntersections[k + 1]).toInt()
                if (cStart <= cEnd) {
                    val baseIdx = row * GridConfig.COLS
                    for (c in cStart..cEnd) {
                        val idx = baseIdx + c
                        if (!overwriteLand && cells[idx] == landByte) continue
                        cells[idx] = valByte
                    }
                }
                k += 2
            }
        }
    }

    private fun stampBuoyProximity(buoys: List<BuoyPoint>, cells: ByteArray, radiusM: Double) {
        if (buoys.isEmpty()) return
        val openSeaByte = CellType.OPEN_SEA.ordinal.toByte()
        val wattByte = CellType.WATTFAHRWASSER.ordinal.toByte()

        val metersPerRow = GridConfig.LAT_STEP * 111_320.0
        val midLatRad = (GridConfig.MIN_LAT + GridConfig.MAX_LAT) / 2.0 * PI / 180.0
        val metersPerCol = GridConfig.LON_STEP * 111_320.0 * cos(midLatRad)
        val rowRadius = (radiusM / metersPerRow).roundToInt().coerceAtLeast(1)
        val colRadius = (radiusM / metersPerCol).roundToInt().coerceAtLeast(1)
        val radiusM2 = radiusM * radiusM

        for (b in buoys) {
            if (!GridConfig.inBounds(b.lat, b.lon)) continue
            val r0 = GridConfig.latToRow(b.lat)
            val c0 = GridConfig.lonToCol(b.lon)
            val rMin = max(0, r0 - rowRadius)
            val rMax = min(GridConfig.ROWS - 1, r0 + rowRadius)
            val cMin = max(0, c0 - colRadius)
            val cMax = min(GridConfig.COLS - 1, c0 + colRadius)
            for (r in rMin..rMax) {
                val rowLat = GridConfig.rowToLat(r)
                val baseIdx = r * GridConfig.COLS
                for (c in cMin..cMax) {
                    val idx = baseIdx + c
                    if (cells[idx] != openSeaByte) continue
                    val colLon = GridConfig.colToLon(c)
                    val d = GridConfig.approxMeters(b.lat, b.lon, rowLat, colLon)
                    if (d * d <= radiusM2) cells[idx] = wattByte
                }
            }
        }
    }

    private fun stampLineStringBuffer(
        line: List<LatLng>,
        cells: ByteArray,
        value: CellType,
        bufferM: Double,
        overwriteRestricted: Boolean = false,
        overwriteLand: Boolean = false
    ) {
        if (line.size < 2) return
        val openSeaByte = CellType.OPEN_SEA.ordinal.toByte()
        val landByte = CellType.LAND.ordinal.toByte()
        val restrictedByte = CellType.RESTRICTED.ordinal.toByte()
        val ruhezoneByte = CellType.RUHEZONE.ordinal.toByte()
        val wattByte = CellType.WATTFAHRWASSER.ordinal.toByte()
        val valByte = value.ordinal.toByte()

        val metersPerRow = GridConfig.LAT_STEP * 111_320.0
        val midLatRad = (GridConfig.MIN_LAT + GridConfig.MAX_LAT) / 2.0 * PI / 180.0
        val metersPerCol = GridConfig.LON_STEP * 111_320.0 * cos(midLatRad)
        val rowRadius = (bufferM / metersPerRow).roundToInt().coerceAtLeast(1)
        val colRadius = (bufferM / metersPerCol).roundToInt().coerceAtLeast(1)
        val bufferM2 = bufferM * bufferM

        for (i in 0 until line.size - 1) {
            val a = line[i]
            val b = line[i + 1]
            val segLen = GridConfig.approxMeters(a.latitude, a.longitude, b.latitude, b.longitude)
            val steps = max(2, (segLen / 80.0).toInt())
            for (s in 0..steps) {
                val t = s.toDouble() / steps
                val lat = a.latitude + (b.latitude - a.latitude) * t
                val lon = a.longitude + (b.longitude - a.longitude) * t
                if (!GridConfig.inBounds(lat, lon)) continue
                val r0 = GridConfig.latToRow(lat)
                val c0 = GridConfig.lonToCol(lon)
                val rMin = max(0, r0 - rowRadius)
                val rMax = min(GridConfig.ROWS - 1, r0 + rowRadius)
                val cMin = max(0, c0 - colRadius)
                val cMax = min(GridConfig.COLS - 1, c0 + colRadius)
                for (r in rMin..rMax) {
                    val rowLat = GridConfig.rowToLat(r)
                    val baseIdx = r * GridConfig.COLS
                    for (c in cMin..cMax) {
                        val idx = baseIdx + c
                        val cur = cells[idx]
                        if (cur == landByte && !overwriteLand) continue
                        val allowed = cur == openSeaByte || cur == wattByte ||
                            cur == ruhezoneByte ||
                            (overwriteLand && cur == landByte) ||
                            (overwriteRestricted && cur == restrictedByte)
                        if (!allowed) continue
                        val colLon = GridConfig.colToLon(c)
                        val d = GridConfig.approxMeters(lat, lon, rowLat, colLon)
                        if (d * d <= bufferM2) cells[idx] = valByte
                    }
                }
            }
        }
    }
}
