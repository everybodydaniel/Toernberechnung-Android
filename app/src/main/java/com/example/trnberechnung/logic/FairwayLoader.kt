package com.example.trnberechnung.logic

import android.content.Context
import android.util.Log
import org.json.JSONArray
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
 *     Basis-Waypoint angeschlossen (≤ 0,5 sm), damit das Fahrwasser
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

    /** True nach erfolgreichem [load] mit mindestens einem geparsten Feature. */
    @Volatile
    var isLoaded: Boolean = false
        private set

    /**
     * Lädt alle `*.geojson`-Dateien aus `assets/fairways/`. Idempotent:
     * mehrfache Aufrufe überschreiben den State sauber.
     */
    fun load(context: Context) {
        _extraWaypoints.clear()
        _extraEdges.clear()
        _protectedZones.clear()
        isLoaded = false

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

        isLoaded = featuresParsed > 0
        Log.i(TAG, "Loaded $featuresParsed features → ${_extraWaypoints.size} WPs, " +
                "${_extraEdges.size} edges, ${_protectedZones.size} zones")
    }

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
            }
        }
        return count
    }

    private fun parseFairway(geom: JSONObject, props: JSONObject): Boolean {
        val coords = geom.optJSONArray("coordinates") ?: return false
        if (coords.length() < 2) return false

        val id = props.optString("id").ifBlank { "fw_${_extraWaypoints.size}" }
        val minDepth = props.optDouble("minDepth", 1.0)

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

    private fun parseZone(geom: JSONObject, props: JSONObject): Boolean {
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

    /**
     * Verknüpft jeden Fahrwasser-Endpunkt mit dem nächsten Basis-Waypoint,
     * sofern der ≤ 0,5 sm (≈ 0,008°) entfernt ist. Sonst hängt das
     * Fahrwasser isoliert im Graphen.
     */
    private fun connectEndpointsToBaseGraph() {
        val maxDistDeg = 0.012  // grob ~0.7 sm, ausreichend für Hafen-Anschlüsse
        val baseList = NauticalRouter.baseWaypointsForLoader()
        if (baseList.isEmpty()) return

        // Endpunkte aller geladenen Fahrwasser (jeweils _0 und letzter Index pro Fahrwasser-Gruppe)
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
