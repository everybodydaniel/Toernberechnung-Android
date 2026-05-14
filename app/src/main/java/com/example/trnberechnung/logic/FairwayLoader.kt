package com.example.trnberechnung.logic

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng

/**
 * Lädt zusätzliche Fahrwasser- und Schutzzonen-Geometrien aus
 * GeoJSON-Dateien in `app/src/main/assets/fairways/` (Endung `.geojson`)
 * und stellt sie [NauticalRouter] und [com.example.trnberechnung.routing.SeaRouteCalculator]
 * zur Verfügung.
 *
 * # Erwartetes GeoJSON-Schema
 *
 * Eine `FeatureCollection` mit zwei Feature-Typen:
 *
 *  1. **Fahrwasser** — `geometry.type = "LineString"`, `properties`:
 *     - `id` (String) — eindeutiger Fahrwasser-Name, z.B. `"memmertbalje"`
 *     - `minDepth` (Number, Meter, Chart Datum LAT) — Mindesttiefe der Rinne
 *     - `bouyType` (String, optional) — z.B. `"beprickt"`, `"betonnt"`
 *
 *     Jeder Punkt der LineString wird zu einem [NauticalRouter.WP],
 *     IDs sind `"<id>_0"`, `"<id>_1"`, …
 *     Aufeinanderfolgende Punkte werden zu einer Kante verknüpft.
 *     Erster und letzter Punkt werden zusätzlich an den nächstgelegenen
 *     Basis-Waypoint angeschlossen (≤ 0,7 sm), damit das Fahrwasser
 *     in den Hauptgraphen integriert ist.
 *
 *  2. **Schutzzone** — `geometry.type = "Polygon"`, `properties`:
 *     - `zone` (String) — z.B. `"nationalpark_zone_1"`, `"vogelschutzgebiet"`
 *     - `name` (String, optional)
 *
 *     Wird als `List<LatLng>` in [protectedZones] aufgenommen.
 *     [com.example.trnberechnung.routing.SeaRouteCalculator] rastert
 *     diese Polygone wie Land (Cost = unendlich).
 *
 * # Lebenszyklus
 *
 * [load] wird einmalig in `MainActivity.onCreate` aufgerufen, BEVOR
 * der erste Routing-Lauf passiert. Solange [isLoaded] `false` ist, sind
 * [extraWaypoints]/[extraEdges]/[protectedZones] leer und der Router
 * fällt sauber auf den Basisgraphen zurück.
 *
 * Die UI kann [isLoaded] abfragen, um den Banner
 * „PEILDATEN & TIDE-BERECHNUNG AKTUALISIERT & VERWENDET" anzuzeigen.
 */
object FairwayLoader {

    private const val TAG = "FairwayLoader"
    private const val ASSETS_DIR = "fairways"

    private val _extraWaypoints = mutableListOf<NauticalRouter.WP>()
    private val _extraEdges = mutableListOf<Pair<String, String>>()
    private val _protectedZones = mutableListOf<List<LatLng>>()

    /** Aus GeoJSON erzeugte Fahrwasser-Waypoints. Leer, solange nicht geladen. */
    val extraWaypoints: List<NauticalRouter.WP> get() = _extraWaypoints

    /** Aus GeoJSON erzeugte Fahrwasser-Kanten + Anbindungen an Basis-Waypoints. */
    val extraEdges: List<Pair<String, String>> get() = _extraEdges

    /** Aus GeoJSON erzeugte Schutzzonen (Nationalpark-Zone-1 etc.). */
    val protectedZones: List<List<LatLng>> get() = _protectedZones

    /**
     * True, sobald [load] mindestens ein Feature erfolgreich geparst hat.
     * `StateFlow`, damit Compose-UI reaktiv darauf hören kann
     * (z.B. das Peildaten-Banner in der MapScreen).
     */
    private val _isLoaded = MutableStateFlow(false)
    val isLoadedFlow: StateFlow<Boolean> = _isLoaded

    /** Bequemer synchroner Snapshot. Für reaktive UIs [isLoadedFlow] bevorzugen. */
    val isLoaded: Boolean get() = _isLoaded.value

    /**
     * Lädt alle `*.geojson`-Dateien aus `assets/fairways/`. Idempotent:
     * mehrfache Aufrufe überschreiben den State sauber.
     */
    fun load(context: Context) {
        _extraWaypoints.clear()
        _extraEdges.clear()
        _protectedZones.clear()
        _isLoaded.value = false

        val assets = context.assets
        val files = try {
            assets.list(ASSETS_DIR).orEmpty().filter { it.endsWith(".geojson") }
        } catch (e: Exception) {
            RouterLog.w(TAG, "Cannot list assets/$ASSETS_DIR", e)
            return
        }

        if (files.isEmpty()) {
            RouterLog.i(TAG, "No GeoJSON files in assets/$ASSETS_DIR — using base graph only")
            return
        }

        var featuresParsed = 0
        for (file in files) {
            try {
                val json = assets.open("$ASSETS_DIR/$file").bufferedReader().use { it.readText() }
                featuresParsed += parseFeatureCollection(json)
            } catch (e: Exception) {
                RouterLog.w(TAG, "Failed to parse $file", e)
            }
        }

        // Anbindung der Fahrwasser-Endpunkte an den Basisgraphen
        connectEndpointsToBaseGraph()

        _isLoaded.value = featuresParsed > 0
        RouterLog.i(TAG, "Loaded $featuresParsed features → ${_extraWaypoints.size} WPs, " +
                "${_extraEdges.size} edges, ${_protectedZones.size} zones")
    }

    // ── Whitelists für typensensitives Parsing ──────────────────────────
    /**
     * Welche LineString-`seamark:type`-Werte werden als befahrbare Fahrwasser
     * akzeptiert. Andere Linien (Kabel, Pipelines, Brücken, Küstenlinien)
     * werden ignoriert.
     */
    private val FAIRWAY_SEAMARKS = setOf(
        "fairway",
        "navigation_line",
        "recommended_track",
        "recommended_traffic_lane",
        "ferry_route",
        "separation_lane"
    )

    /**
     * Welche Polygon-`seamark:type`-Werte werden als Sperrzone (Routing-No-Go)
     * behandelt. Schutzgebiete + Nationalpark-Zonen werden zusätzlich über
     * `protect_class` oder `boundary=protected_area` erkannt.
     */
    private val PROTECTED_SEAMARKS = setOf(
        "restricted_area",
        "dumping_ground",
        "military_area",
        "production_area"
    )

    // ── Routing-Bounding-Box (Ostfriesisches Wattenmeer) ─────────────────
    // Buoys/Beacons außerhalb werden nicht zu Pricken-Ketten verarbeitet.
    private val BBOX_LAT_MIN = 53.3
    private val BBOX_LAT_MAX = 54.0
    private val BBOX_LON_MIN = 6.3
    private val BBOX_LON_MAX = 8.3

    /** Parst eine GeoJSON FeatureCollection. Liefert die Anzahl gelesener Features. */
    private fun parseFeatureCollection(json: String): Int {
        val root = JSONObject(json)
        if (root.optString("type") != "FeatureCollection") return 0

        val features = root.optJSONArray("features") ?: return 0
        var count = 0
        // ── Pass 1: LineStrings + Polygone (Standard-Features) ──
        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i) ?: continue
            val geom = feat.optJSONObject("geometry") ?: continue
            val props = feat.optJSONObject("properties") ?: JSONObject()

            when (geom.optString("type")) {
                "LineString" -> if (parseFairway(geom, props)) count++
                "Polygon"    -> if (parseZone(geom, props)) count++
                "MultiPolygon" -> if (parseMultiPolygonZone(geom, props)) count++
            }
        }
        // ── Pass 2: laterale Tonnen → Pricken-Ketten ──
        // Erzeugt zusätzliche Waypoints + Edges aus rot/grün-Paaren.
        count += buildBuoyChains(features)
        return count
    }

    /** Kürzt OSM-Pfad-IDs (`way/1234567`) zu kompakten Kennungen. */
    private fun deriveFairwayId(props: JSONObject): String {
        val rawId = props.optString("@id").ifBlank { props.optString("id") }
        if (rawId.isNotBlank()) {
            return rawId.replace("/", "_")
        }
        return props.optString("seamark:type").ifBlank { "fw" } + "_${_extraWaypoints.size}"
    }

    /** Liest aus OSM-Tags eine plausible Mindesttiefe. Default: 1 m. */
    private fun deriveMinDepth(props: JSONObject): Double {
        // OSM-Tags `seamark:depth`, `seamark:fairway:depth`, `depth`
        return props.optDouble("seamark:fairway:depth", Double.NaN)
            .takeIf { !it.isNaN() }
            ?: props.optDouble("seamark:depth", Double.NaN).takeIf { !it.isNaN() }
            ?: props.optDouble("depth", Double.NaN).takeIf { !it.isNaN() }
            ?: 1.0
    }

    private fun parseFairway(geom: JSONObject, props: JSONObject): Boolean {
        // Nur seamark-Linien, die wir als Fahrwasser akzeptieren.
        // Schemas: entweder unser eigenes (`properties.id` + `minDepth`) oder OSM (`seamark:type`).
        val seamark = props.optString("seamark:type")
        val isOwnFormat = props.has("id") && props.has("minDepth")
        if (!isOwnFormat && seamark !in FAIRWAY_SEAMARKS) return false

        val coords = geom.optJSONArray("coordinates") ?: return false
        if (coords.length() < 2) return false

        val id = if (isOwnFormat) props.optString("id") else deriveFairwayId(props)
        val minDepth = if (isOwnFormat) props.optDouble("minDepth", 1.0) else deriveMinDepth(props)

        val newWpIds = mutableListOf<String>()
        for (j in 0 until coords.length()) {
            val pt = coords.optJSONArray(j) ?: continue
            // GeoJSON-Konvention: [lon, lat]
            val lon = pt.optDouble(0)
            val lat = pt.optDouble(1)
            val wpId = "${id}_$j"
            _extraWaypoints += NauticalRouter.WP(wpId, lat, lon, minDepth)
            newWpIds += wpId
        }
        // Kanten entlang der Linie
        for (k in 0 until newWpIds.size - 1) {
            _extraEdges += newWpIds[k] to newWpIds[k + 1]
        }
        return true
    }

    /** True, wenn ein Polygon-Feature als Sperrzone behandelt werden soll. */
    private fun isProtectedPolygon(props: JSONObject): Boolean {
        // Eigenes Format
        if (props.optString("zone").isNotBlank()) return true
        // OSM-Whitelist
        if (props.optString("seamark:type") in PROTECTED_SEAMARKS) return true
        // Allgemeine Schutzgebiet-Tags
        if (props.has("protect_class")) return true
        if (props.optString("boundary") == "protected_area") return true
        if (props.optString("leisure") == "nature_reserve") return true
        return false
    }

    private fun parseZone(geom: JSONObject, props: JSONObject): Boolean {
        if (!isProtectedPolygon(props)) return false

        // Nur äußerer Ring (erstes Element von coordinates)
        val rings = geom.optJSONArray("coordinates") ?: return false
        val outer = rings.optJSONArray(0) ?: return false
        if (outer.length() < 3) return false

        val polygon = mutableListOf<LatLng>()
        for (j in 0 until outer.length()) {
            val pt = outer.optJSONArray(j) ?: continue
            val lon = pt.optDouble(0)
            val lat = pt.optDouble(1)
            polygon += LatLng(lat, lon)
        }
        _protectedZones += polygon
        return true
    }

    /** MultiPolygon = mehrere Ringe; jeder äußere Ring wird als eigene Zone aufgenommen. */
    private fun parseMultiPolygonZone(geom: JSONObject, props: JSONObject): Boolean {
        if (!isProtectedPolygon(props)) return false

        val polygons = geom.optJSONArray("coordinates") ?: return false
        var added = false
        for (p in 0 until polygons.length()) {
            val rings = polygons.optJSONArray(p) ?: continue
            val outer = rings.optJSONArray(0) ?: continue
            if (outer.length() < 3) continue
            val polygon = mutableListOf<LatLng>()
            for (j in 0 until outer.length()) {
                val pt = outer.optJSONArray(j) ?: continue
                val lon = pt.optDouble(0)
                val lat = pt.optDouble(1)
                polygon += LatLng(lat, lon)
            }
            _protectedZones += polygon
            added = true
        }
        return added
    }

    // ─────────────────────────────────────────────────────────────────────
    //  PRICKEN-KETTEN aus lateralen Tonnen
    // ─────────────────────────────────────────────────────────────────────
    /**
     * Da OSM im offenen Watt fast keine Fahrwasser als LineStrings hat,
     * sondern nur einzelne Tonnen/Pricken als Points, rekonstruieren wir
     * Fahrwasser daraus:
     *
     *  1. Jede rote (port) laterale Tonne wird mit der nächsten grünen
     *     (starboard) Tonne in ≤ ~500 m gepaart → der Mittelpunkt liegt
     *     in der Fahrrinne.
     *  2. Diese Mittelpunkte werden zu Waypoints. Jeder bekommt zu seinen
     *     bis zu K (= 3) nächsten Nachbarn in ≤ ~900 m je eine Kante —
     *     das ergibt ein zusammenhängendes Pricken-Netz im Watt.
     *  3. Bbox auf Ostfriesland eingeschränkt, damit Nordfriesland nicht
     *     mitgeparst wird.
     *
     * Heuristik, nicht exakt. Aber sehr viel näher an der Realität als
     * Hartkodierungen, weil die Tonnen-Positionen ja vermessen sind.
     */
    private fun buildBuoyChains(features: org.json.JSONArray): Int {
        data class Buoy(val lat: Double, val lon: Double, val isRed: Boolean)
        val buoys = mutableListOf<Buoy>()

        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i) ?: continue
            val props = feat.optJSONObject("properties") ?: continue
            val type = props.optString("seamark:type")
            if (type != "buoy_lateral" && type != "beacon_lateral") continue

            val geom = feat.optJSONObject("geometry") ?: continue
            if (geom.optString("type") != "Point") continue
            val coords = geom.optJSONArray("coordinates") ?: continue
            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            if (lat !in BBOX_LAT_MIN..BBOX_LAT_MAX) continue
            if (lon !in BBOX_LON_MIN..BBOX_LON_MAX) continue

            val tag = if (type == "buoy_lateral") "buoy_lateral" else "beacon_lateral"
            val category = props.optString("seamark:$tag:category")
            val colour = props.optString("seamark:$tag:colour")
            val isRed = category == "port" || category == "preferred_channel_port" ||
                        colour.startsWith("red")
            val isGreen = category == "starboard" || category == "preferred_channel_starboard" ||
                          colour.startsWith("green")
            when {
                isRed -> buoys += Buoy(lat, lon, isRed = true)
                isGreen -> buoys += Buoy(lat, lon, isRed = false)
            }
        }

        if (buoys.size < 4) {
            RouterLog.i(TAG, "Buoy chains: only ${buoys.size} lateral buoys in bbox — skipping")
            return 0
        }

        val reds = buoys.filter { it.isRed }
        val greens = buoys.filter { !it.isRed }
        RouterLog.d(TAG, "Buoy chains: ${reds.size} red + ${greens.size} green in bbox")

        // ── (1) Paarung: jede rote Tonne mit nächster grüner in ≤ 500 m ──
        // 0.005° ≈ 555 m bei 53,7° N — ausreichend für schmale Pricken-Pfade.
        val maxPairDistDeg = 0.005
        val maxPairDistSq = maxPairDistDeg * maxPairDistDeg
        val usedGreen = BooleanArray(greens.size)
        data class Midpoint(val lat: Double, val lon: Double)
        val midpoints = mutableListOf<Midpoint>()

        for (red in reds) {
            var bestIdx = -1
            var bestSq = Double.MAX_VALUE
            for (gi in greens.indices) {
                if (usedGreen[gi]) continue
                val g = greens[gi]
                val dLat = red.lat - g.lat
                val dLon = red.lon - g.lon
                val sq = dLat * dLat + dLon * dLon
                if (sq < bestSq) { bestSq = sq; bestIdx = gi }
            }
            if (bestIdx >= 0 && bestSq <= maxPairDistSq) {
                usedGreen[bestIdx] = true
                val g = greens[bestIdx]
                midpoints += Midpoint(
                    (red.lat + g.lat) / 2.0,
                    (red.lon + g.lon) / 2.0
                )
            }
        }

        RouterLog.d(TAG, "Buoy chains: ${midpoints.size} pairs → channel midpoints")
        if (midpoints.size < 2) return 0

        // ── (2) Midpoints als Waypoints hinzufügen ──
        val mpIds = midpoints.mapIndexed { idx, mp ->
            val id = "pricken_$idx"
            // Tiefe als 0,5 m (konservativ — Watt-Pricken markieren oft
            // <1 m Fahrwasser; ohne echte Tiefenangabe konservativer Default)
            _extraWaypoints += NauticalRouter.WP(id, mp.lat, mp.lon, 0.5)
            id
        }

        // ── (3) Verknüpfung: jeder Midpoint zu seinen K nächsten Nachbarn ──
        // 0.008° ≈ 890 m. K=3, damit das Netz auch bei Verzweigungen zusammenhängt.
        val maxLinkDistDeg = 0.008
        val maxLinkDistSq = maxLinkDistDeg * maxLinkDistDeg
        val K = 3
        var edgeCount = 0
        for (i in midpoints.indices) {
            val a = midpoints[i]
            val neighbors = mutableListOf<Pair<Int, Double>>()
            for (j in midpoints.indices) {
                if (j == i) continue
                val b = midpoints[j]
                val dLat = a.lat - b.lat
                val dLon = a.lon - b.lon
                val sq = dLat * dLat + dLon * dLon
                if (sq <= maxLinkDistSq) neighbors += j to sq
            }
            neighbors.sortBy { it.second }
            for ((j, _) in neighbors.take(K)) {
                if (i < j) {  // Dedup ungerichtete Kanten
                    _extraEdges += mpIds[i] to mpIds[j]
                    edgeCount++
                }
            }
        }

        RouterLog.i(TAG, "Buoy chains: ${midpoints.size} WPs, $edgeCount edges added to graph")
        // Wir zählen die Pricken-Kette einmal als ein "Feature", damit isLoaded triggert.
        return if (midpoints.isNotEmpty()) 1 else 0
    }

    /**
     * Verknüpft die extra-Waypoints mit dem Basisgraph.
     *
     *  • Fahrwasser-LineStrings (id-Prefix `way_*` oder ähnliches): nur erster
     *    und letzter Punkt jeder Linie an den nächsten Basis-WP innerhalb
     *    ~0,7 sm anschließen. Das hält das ursprüngliche Linien-Tracing intakt.
     *  • Pricken-Mittelpunkte (id-Prefix `pricken_`): JEDEN einzeln testen,
     *    weil die Pricken-Ketten lange Verläufe haben und an mehreren Stellen
     *    nahe an Häfen vorbeiziehen. So bekommt jeder erreichbare Hafen einen
     *    Einstieg ins Pricken-Netz.
     */
    private fun connectEndpointsToBaseGraph() {
        val maxDistDeg = 0.012  // ~0,7 sm
        val maxDistSq = maxDistDeg * maxDistDeg
        val baseList = NauticalRouter.baseWaypointsForLoader()
        if (baseList.isEmpty()) return

        val (pricken, fairwayWps) = _extraWaypoints.partition { it.id.startsWith("pricken_") }

        // Fahrwasser-LineStrings: nur Endpunkte pro Gruppe (Linie)
        val groups = fairwayWps.groupBy { it.id.substringBeforeLast('_') }
        for ((_, members) in groups) {
            val endpoints = listOfNotNull(members.firstOrNull(), members.lastOrNull()).distinct()
            for (endpoint in endpoints) {
                connectIfClose(endpoint, baseList, maxDistSq)
            }
        }

        // Pricken-Netz: jeder Mittelpunkt einzeln. Bei vielen Pricken (1000+)
        // ist das O(N×M), aber M (≈100 base WPs) hält's überschaubar.
        for (wp in pricken) {
            connectIfClose(wp, baseList, maxDistSq)
        }
    }

    private fun connectIfClose(
        wp: NauticalRouter.WP,
        baseList: List<NauticalRouter.WP>,
        maxDistSq: Double
    ) {
        val nearest = baseList.minByOrNull { b ->
            val dLat = b.lat - wp.lat
            val dLon = b.lon - wp.lon
            dLat * dLat + dLon * dLon
        } ?: return
        val dLat = nearest.lat - wp.lat
        val dLon = nearest.lon - wp.lon
        if (dLat * dLat + dLon * dLon <= maxDistSq) {
            _extraEdges += wp.id to nearest.id
        }
    }
}
