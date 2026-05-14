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
            Log.w(TAG, "Cannot list assets/$ASSETS_DIR", e)
            return
        }

        if (files.isEmpty()) {
            Log.i(TAG, "No GeoJSON files in assets/$ASSETS_DIR — using base graph only")
            return
        }

        var featuresParsed = 0
        for (file in files) {
            try {
                val json = assets.open("$ASSETS_DIR/$file").bufferedReader().use { it.readText() }
                featuresParsed += parseFeatureCollection(json)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse $file", e)
            }
        }

        // Anbindung der Fahrwasser-Endpunkte an den Basisgraphen
        connectEndpointsToBaseGraph()

        _isLoaded.value = featuresParsed > 0
        Log.i(TAG, "Loaded $featuresParsed features → ${_extraWaypoints.size} WPs, " +
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

    /** Parst eine GeoJSON FeatureCollection. Liefert die Anzahl gelesener Features. */
    private fun parseFeatureCollection(json: String): Int {
        val root = JSONObject(json)
        if (root.optString("type") != "FeatureCollection") return 0

        val features = root.optJSONArray("features") ?: return 0
        var count = 0
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

    /**
     * Verknüpft jeden Fahrwasser-Endpunkt mit dem nächsten Basis-Waypoint,
     * sofern der ≤ ~0,7 sm (≈ 0,012°) entfernt ist. Sonst hängt das
     * Fahrwasser isoliert im Graphen.
     */
    private fun connectEndpointsToBaseGraph() {
        val maxDistDeg = 0.012
        val baseList = NauticalRouter.baseWaypointsForLoader()
        if (baseList.isEmpty()) return

        val groups = _extraWaypoints.groupBy { it.id.substringBeforeLast('_') }
        for ((_, members) in groups) {
            val endpoints = listOfNotNull(members.firstOrNull(), members.lastOrNull()).distinct()
            for (endpoint in endpoints) {
                val nearest = baseList.minByOrNull { wp ->
                    val dLat = wp.lat - endpoint.lat
                    val dLon = wp.lon - endpoint.lon
                    dLat * dLat + dLon * dLon
                } ?: continue
                val dLat = nearest.lat - endpoint.lat
                val dLon = nearest.lon - endpoint.lon
                if (dLat * dLat + dLon * dLon <= maxDistDeg * maxDistDeg) {
                    _extraEdges += endpoint.id to nearest.id
                }
            }
        }
    }
}
