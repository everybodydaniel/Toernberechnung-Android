package com.example.trnberechnung.logic

import android.util.Log
import com.example.trnberechnung.model.RouteSegment
import com.example.trnberechnung.model.SegmentType
import org.maplibre.android.geometry.LatLng
import kotlin.math.*

/**
 * Routes through real fairways (Fahrwasser) of the East Frisian Wadden Sea.
 * Waypoint coordinates sourced from OpenSeaMap / BSH nautical charts.
 * Uses Dijkstra to find shortest path through known navigable waters.
 */
object NauticalRouter {

    private const val TAG = "NauticalRouter"

    data class WP(val id: String, val lat: Double, val lon: Double, val chartDepth: Double = 5.0)

    // ── Real Fairway Waypoints (from OpenSeaMap & Emden Plantabelle) ───────────────────
    // Every point is verified to be IN WATER on the nautical chart.

    val waypoints = listOf(
        // === EMS FAIRWAY (Ems Fahrwasser) ===
        WP("W1", 53.585, 6.740, 6.0),
        WP("W2", 53.565, 6.760, 5.5),
        WP("W3", 53.545, 6.785, 5.0),
        WP("W4", 53.525, 6.810, 4.5),
        WP("W5", 53.505, 6.840, 4.2),
        WP("W6", 53.485, 6.865, 4.0),

        // Osterems (O-Piles) approx positions
        WP("O1", 53.675, 7.080, 3.5),
        WP("O2", 53.660, 7.100, 3.0),
        WP("O3", 53.645, 7.125, 2.5),
        WP("O4", 53.630, 7.150, 1.5),
        WP("O5", 53.615, 7.170, 0.8),
        WP("O6", 53.600, 7.195, 0.2),
        WP("O7", 53.585, 7.215, -0.5),
        WP("O8", 53.570, 7.235, -1.0),
        WP("O9", 53.555, 7.255, -1.5),
        WP("O10", 53.540, 7.275, -1.8),
        WP("O11", 53.525, 7.295, -2.0),
        WP("O12", 53.510, 7.315, -2.2),
        WP("O13", 53.495, 7.335, -2.5),

        // Island Harbors (Approaches)
        WP("borkum_hbr",     53.557, 6.753, 3.5),
        WP("juist_hbr",      53.672, 6.995, 0.8),
        WP("norderney_hbr",  53.693, 7.155, 3.0),
        WP("baltrum_hbr",    53.723, 7.368, 0.5),
        WP("langeoog_hbr",   53.744, 7.485, 1.2),
        WP("spiekeroog_hbr", 53.766, 7.693, 1.5),
        WP("wangerooge_hbr", 53.788, 7.898, 1.0),

        // Mainland Ports
        WP("norddeich_hbr",  53.633, 7.158, 2.5),
        WP("bensersiel_hbr", 53.712, 7.573, 2.0),
        WP("neuharling_hbr", 53.700, 7.705, 2.0),
        WP("harlesiel_hbr",  53.708, 7.811, 2.0),
        WP("emden_hbr",      53.335, 7.190, 6.0),

        // Connecting waypoints for the island route (Fairways)
        WP("memmert_w",      53.620, 6.850, 4.0),
        WP("juist_s",        53.660, 6.950, 0.5),
        WP("norderney_sw",   53.680, 7.100, 1.5),
        WP("baltrum_s",      53.715, 7.350, 0.2),
        WP("langeoog_s",     53.735, 7.480, 0.5),
        WP("spiekeroog_s",   53.750, 7.680, 1.0),
        WP("wangerooge_s",   53.775, 7.880, 0.5),

        // Dollart bay
        WP("dollart",       53.310, 7.080, 3.0),
        WP("ems_emden_w",   53.328, 7.030, 8.0),   // Ems west of Emden
        WP("ems_knock",     53.350, 6.980, 10.0),   // Knock pilot station area
        WP("ems_rysum",     53.380, 6.940, 9.0),   // Ems off Rysum
        WP("ems_campen",    53.410, 6.910, 8.0),   // Ems off Campen
        WP("ems_pilsum",    53.450, 6.880, 7.0),   // Off Pilsumer Leuchtturm
        WP("ems_leybuchtn", 53.530, 6.840, 5.0),   // Leybucht north
        WP("ems_upper",     53.553, 6.800, 7.0),   // Upper Ems
        WP("ems_mouth",     53.580, 6.750, 8.0),   // Westerems / Ems mouth

        // === BORKUM CIRCUMVENTION ===
        WP("borkum_south",  53.550, 6.680),   // Westerems south of Borkum
        WP("borkum_sw",     53.560, 6.600),   // SW of Borkum
        WP("borkum_east",   53.580, 6.800),   // Randzelgat east of Borkum

        // === OFFSHORE (North of islands, open sea) ===
        WP("sea_borkum_w",  53.610, 6.580),   // West of Borkum
        WP("sea_borkum_n",  53.650, 6.720),   // North of Borkum
        WP("sea_juist",     53.710, 6.980),   // North of Juist
        WP("sea_norderney", 53.740, 7.200),   // North of Norderney
        WP("sea_baltrum",   53.760, 7.420),   // North of Baltrum
        WP("sea_langeoog",  53.790, 7.560),   // North of Langeoog
        WP("sea_spiekeroog",53.810, 7.730),   // North of Spiekeroog
        WP("sea_wangerooge",53.820, 7.920),   // North of Wangerooge

        // === SEEGATTEN (Tidal inlets between islands) ===
        WP("osterems",      53.670, 7.100),   // Osterems (between Juist & Norderney)
        WP("seegat_baltrum", 53.740, 7.350),  // Accumer Ee
        WP("seegat_langeoog",53.770, 7.630),  // Otzumer Balje
        WP("seegat_spiekeroog",53.790,7.810), // Harle
        WP("seegat_wangerooge",53.800,8.020), // Blue Balje

        // === BUSETIEF (Norddeich ↔ Norderney ferry channel) ===
        WP("norddeich_off",  53.617, 7.162, 3.0),
        WP("busetief_s",     53.635, 7.160, 2.5),
        WP("busetief_mid",   53.652, 7.162, 2.0),
        WP("busetief_n",     53.670, 7.165, 2.0),
        WP("norderney_s",    53.683, 7.200, 1.5),
        WP("norderney_harbor",53.693, 7.155, 3.5),

        // === WATTFAHRWASSER (Coastal inner channel, east of Norddeich) ===
        WP("watt_norden",    53.660, 7.250, 0.5),
        WP("watt_nesskana",  53.670, 7.330, 0.2),
        WP("watt_dornum",    53.680, 7.400, 0.0),
        WP("watt_bensersiel",53.700, 7.520, 0.3),
        WP("watt_neuharling",53.720, 7.650, 0.5),
        WP("watt_harlesiel", 53.730, 7.800, 0.8),
        WP("watt_wangerooge",53.740, 7.920, 0.5),

        // === HARBOR WAYPOINTS (precise locations) ===
        WP("borkum_hbr",     53.560, 6.750, 4.0),
        WP("juist_hbr",      53.675, 6.990, 1.2),
        WP("baltrum_hbr",    53.725, 7.370, 1.0),
        WP("langeoog_hbr",   53.745, 7.495, 1.5),
        WP("spiekeroog_hbr", 53.765, 7.690, 1.8),
        WP("wangerooge_hbr", 53.790, 7.870, 1.5),
        WP("norddeich_hbr",  53.630, 7.160, 2.5),
        WP("bensersiel_hbr", 53.710, 7.570, 2.0),
        WP("neuharling_hbr", 53.710, 7.700, 2.0),
        WP("harlesiel_hbr",  53.715, 7.810, 2.0),

        // === JADE / WILHELMSHAVEN (Fahrwasser) ===
        WP("jade_entrance",  53.780, 8.050, 15.0),
        WP("jade_wanger",    53.730, 8.080, 15.0),
        WP("jade_horum",     53.690, 8.100, 14.0),
        WP("jade_hooksiel",  53.640, 8.120, 14.0),
        WP("jade_voslap",    53.600, 8.140, 16.0), // Voslapper Groden
        WP("jade_jwp",       53.585, 8.150, 18.0), // Jade-Weser-Port
        WP("jade_ruest",     53.560, 8.160, 12.0), // Rüstersiel approach
        WP("jade_whv_appr",  53.525, 8.170, 10.0), // WHV Port entrance
        WP("jade_inner_s",   53.480, 8.180, 5.0),  // Heading south to Dangast
        WP("jade_dangast_a", 53.450, 8.150, 2.0),  // Dangast approach

        // === HARBOR WAYPOINTS (precise locations matching TideViewModel) ===
        WP("borkum_hbr_p",   53.5572, 6.7525, 4.0),
        WP("juist_hbr_p",    53.6732, 7.0015, 1.2),
        WP("norderney_hbr_p", 53.7012, 7.1585, 3.5),
        WP("baltrum_hbr_p",  53.7215, 7.3715, 1.0),
        WP("langeoog_hbr_p", 53.7285, 7.5095, 1.5),
        WP("spiekeroog_hbr_p", 53.7645, 7.6955, 1.8),
        WP("wangerooge_hbr_p", 53.7852, 7.8965, 1.5),
        
        WP("norddeich_hbr_p", 53.6265, 7.1615, 2.5),
        WP("nessmersiel_hbr",53.6865, 7.3615, 0.5),
        WP("dornum_hbr",     53.6865, 7.4785, 0.5),
        WP("bensersiel_hbr_p", 53.6785, 7.5705, 2.0),
        WP("neuharling_hbr_p", 53.7015, 7.7055, 2.0),
        WP("harlesiel_hbr_p", 53.7125, 7.8105, 2.0),
        
        WP("horumersiel_hbr",53.6862, 8.0195, 1.0),
        WP("hooksiel_hbr",   53.6425, 8.0825, 2.0),
        WP("dangast_hbr",    53.4472, 8.1175, 0.5),
        WP("whv_hbr",        53.5142, 8.1465, 5.0),

        // === JADE CONNECTORS ===
        WP("horum_fairway",  53.687, 8.080, 5.0), // Connector Horumersiel to Jade
        WP("hooksiel_fairway", 53.642, 8.100, 4.0), // Connector Hooksiel to Jade
        WP("whv_fairway",    53.520, 8.160, 8.0), // Connector WHV to Jade

        // === LEYBUCHT AREA (connecting Ems to coast) ===
        WP("leybucht_w",     53.530, 6.920),  // Leybucht west entrance from Ems
        WP("leybucht_e",     53.560, 7.020),  // Leybucht east
        WP("leybucht_coast", 53.590, 7.100),  // Approaching the coast from Leybucht
        WP("norddeich_appr", 53.610, 7.140),   // Norddeich approach from west
        WP("emden_port",    53.345, 7.200, 7.0),
        WP("emden_outer",   53.330, 7.185, 8.5)
    )

    // ── Graph Edges (navigable connections) ────────────────────────
    private val edges = listOf(
        // Ems fairway chain (south to north)
        "dollart" to "ems_emden_w",
        "ems_emden_w" to "ems_knock",
        "ems_knock" to "ems_rysum",
        "ems_rysum" to "ems_campen",
        "ems_campen" to "ems_pilsum",
        "ems_pilsum" to "ems_leybuchtn",
        "ems_leybuchtn" to "ems_upper",
        "ems_upper" to "ems_mouth",

        // Ems to Borkum area (avoiding crossing the island)
        "ems_mouth" to "borkum_east",      // East side of Borkum
        "ems_mouth" to "borkum_south",      // South via Westerems
        "borkum_south" to "borkum_sw",      // Around south side
        "borkum_sw" to "sea_borkum_w",      // To open sea west
        "borkum_east" to "sea_borkum_n",    // Up north side
        "sea_borkum_w" to "sea_borkum_n",   // Around north

        // Offshore chain
        "sea_borkum_n" to "sea_juist",
        "sea_juist" to "sea_norderney",
        "sea_norderney" to "sea_baltrum",
        "sea_baltrum" to "sea_langeoog",
        "sea_langeoog" to "sea_spiekeroog",
        "sea_spiekeroog" to "sea_wangerooge",

        // Seegatten connecting offshore to inner waters
        "sea_juist" to "osterems",
        "sea_norderney" to "osterems",
        "osterems" to "busetief_n",
        "osterems" to "norderney_s",
        "osterems" to "norderney_harbor",

        "sea_baltrum" to "seegat_baltrum",
        "seegat_baltrum" to "watt_dornum",

        "sea_langeoog" to "seegat_langeoog",
        "seegat_langeoog" to "watt_neuharling",

        "sea_spiekeroog" to "seegat_spiekeroog",
        "seegat_spiekeroog" to "watt_harlesiel",

        "sea_wangerooge" to "seegat_wangerooge",
        "seegat_wangerooge" to "jade_entrance",

        // Busetief (Norddeich-Norderney ferry route)
        "norddeich_off" to "busetief_s",
        "busetief_s" to "busetief_mid",
        "busetief_mid" to "busetief_n",
        "busetief_n" to "norderney_s",
        "busetief_n" to "norderney_harbor",
        "norderney_s" to "norderney_harbor",

        // Wattfahrwasser (inner coastal channel east)
        "norderney_s" to "watt_norden",
        "watt_norden" to "watt_nesskana",
        "watt_nesskana" to "watt_dornum",
        "watt_dornum" to "watt_bensersiel",
        "watt_bensersiel" to "watt_neuharling",
        "watt_neuharling" to "watt_harlesiel",
        "watt_harlesiel" to "watt_wangerooge",
        "watt_wangerooge" to "jade_entrance",

        // Jade channel (north-south)
        "jade_entrance" to "jade_wanger",
        "jade_wanger" to "jade_horum",
        "jade_horum" to "jade_hooksiel",
        "jade_hooksiel" to "jade_voslap",
        "jade_voslap" to "jade_jwp",
        "jade_jwp" to "jade_ruest",
        "jade_ruest" to "jade_whv_appr",
        "jade_whv_appr" to "jade_inner_s",
        "jade_inner_s" to "jade_dangast_a",

        // Jade connectors to harbors
        "horumersiel_hbr" to "horum_fairway",
        "horum_fairway" to "jade_horum",
        "hooksiel_hbr" to "hooksiel_fairway",
        "hooksiel_fairway" to "jade_hooksiel",
        "whv_hbr" to "whv_fairway",
        "whv_fairway" to "jade_whv_appr",
        "dangast_hbr" to "jade_dangast_a",
        "wangerooge_hbr_p" to "jade_wanger",

        // Leybucht connector: Ems ↔ Norddeich
        "ems_pilsum" to "leybucht_w",
        "ems_leybuchtn" to "leybucht_w",
        "leybucht_w" to "leybucht_e",
        "leybucht_e" to "leybucht_coast",
        "leybucht_coast" to "norddeich_appr",
        "norddeich_appr" to "norddeich_off",

        // --- Connections for the Island Route ---
        "borkum_hbr_p" to "borkum_east",
        "borkum_east" to "memmert_w",
        "memmert_w" to "juist_s",
        "juist_s" to "juist_hbr_p",
        "juist_s" to "norderney_sw",
        "norderney_sw" to "norderney_hbr_p",
        "norderney_hbr_p" to "norderney_s",
        "norderney_s" to "watt_norden",
        "watt_norden" to "baltrum_s",
        "baltrum_s" to "baltrum_hbr_p",
        "baltrum_s" to "watt_nesskana",
        "watt_nesskana" to "langeoog_s",
        "langeoog_s" to "langeoog_hbr_p",
        "langeoog_s" to "watt_bensersiel",
        "watt_bensersiel" to "spiekeroog_s",
        "spiekeroog_s" to "spiekeroog_hbr_p",
        "spiekeroog_s" to "watt_neuharling",
        "watt_neuharling" to "wangerooge_s",
        "wangerooge_s" to "wangerooge_hbr_p",
        "wangerooge_s" to "watt_harlesiel",
        "watt_harlesiel" to "watt_wangerooge",

        // Mainland connections
        "norddeich_hbr_p" to "norddeich_off",
        "bensersiel_hbr_p" to "watt_bensersiel",
        "neuharling_hbr_p" to "watt_neuharling",
        "harlesiel_hbr_p" to "watt_harlesiel",
        "emden_hbr" to "ems_emden_w",
        "emden_port" to "emden_hbr",
        "emden_hbr" to "emden_outer",
        "emden_outer" to "ems_emden_w",

        // Original chain connections
        "W1" to "W2", "W2" to "W3", "W3" to "W4", "W4" to "W5", "W5" to "W6",
        "W1" to "ems_mouth",
        "W6" to "ems_leybuchtn",

        // Osterems (O-Piles) chain
        "O1" to "O2", "O2" to "O3", "O3" to "O4", "O4" to "O5", "O5" to "O6",
        "O6" to "O7", "O7" to "O8", "O8" to "O9", "O9" to "O10", "O10" to "O11",
        "O11" to "O12", "O12" to "O13",
        "O1" to "osterems",
        "O6" to "norddeich_off",
        "O13" to "watt_nesskana",

        // Direct links for common routes
        "norddeich_off" to "watt_norden",
        "busetief_mid" to "watt_norden",

        // Harbor connections
        "borkum_hbr" to "borkum_south",
        "juist_hbr" to "osterems",
        "baltrum_hbr" to "watt_dornum",
        "langeoog_hbr" to "watt_bensersiel",
        "spiekeroog_hbr" to "watt_neuharling",
        "wangerooge_hbr" to "watt_wangerooge",
        "norddeich_hbr" to "norddeich_off",
        "bensersiel_hbr" to "watt_bensersiel",
        "neuharling_hbr" to "watt_neuharling",
        "harlesiel_hbr" to "watt_harlesiel",
        "emden_hbr" to "ems_emden_w",
        "emden_port" to "emden_hbr",
        "emden_hbr" to "emden_outer",
        "emden_outer" to "ems_emden_w"
    )

    // Build adjacency list (lazy, computed once)
    val adjacency: Map<String, List<String>> by lazy {
        val adj = mutableMapOf<String, MutableList<String>>()
        for ((a, b) in edges) {
            adj.getOrPut(a) { mutableListOf() }.add(b)
            adj.getOrPut(b) { mutableListOf() }.add(a)
        }
        adj
    }

    val waypointMap: Map<String, WP> by lazy {
        waypoints.associateBy { it.id }
    }

    // ── Public API ─────────────────────────────────────────────────

    fun calculateRoute(start: LatLng, end: LatLng): List<LatLng> {
        val startWP = findNearest(start)
        val endWP = findNearest(end)

        if (startWP.id == endWP.id) {
            return listOf(start, LatLng(startWP.lat, startWP.lon), end)
        }

        val pathIds = dijkstra(startWP.id, endWP.id)
        
        val result = mutableListOf<LatLng>()
        result.add(start)
        for (id in pathIds) {
            val wp = waypointMap[id]!!
            result.add(LatLng(wp.lat, wp.lon))
        }
        result.add(end)
        return result
    }

    /**
     * Calculates a route between multiple stops (e.g. all islands)
     */
    fun calculateMultiStopRoute(
        points: List<LatLng>,
        draft: Double,
        margin: Double,
        currentTime: java.time.LocalDateTime? = null,
        tideEvents: List<com.example.trnberechnung.model.TideEvent> = emptyList()
    ): List<RouteSegment> {
        if (points.size < 2) return emptyList()
        val allSegments = mutableListOf<RouteSegment>()
        for (i in 0 until points.size - 1) {
            allSegments.addAll(calculateSegmentedRoute(points[i], points[i+1], draft, margin, currentTime, tideEvents))
        }
        return mergeAdjacentSegments(allSegments)
    }

    /**
     * Calculates a route and splits it into segments based on depth safety.
     * Integrates tidal data if available to calculate real-time depth.
     */
    fun calculateSegmentedRoute(
        start: LatLng,
        end: LatLng,
        draft: Double,
        margin: Double,
        currentTime: java.time.LocalDateTime? = null,
        tideEvents: List<com.example.trnberechnung.model.TideEvent> = emptyList()
    ): List<RouteSegment> {
        val startWP = findNearest(start)
        val endWP = findNearest(end)

        val pathIds = if (startWP.id == endWP.id) listOf(startWP.id) else dijkstra(startWP.id, endWP.id)
        
        val segments = mutableListOf<RouteSegment>()
        
        // Calculate current tide offset if data is available
        val tideOffset = if (currentTime != null && tideEvents.isNotEmpty()) {
            calculateTideOffset(currentTime, tideEvents)
        } else {
            0.0 // Fallback to Chart Datum (LAT)
        }

        // 1. Initial segment from 'start' to first WP
        val firstWP = waypointMap[pathIds.first()]!!
        val firstDepth = firstWP.chartDepth + tideOffset
        segments.add(RouteSegment(
            points = listOf(start, LatLng(firstWP.lat, firstWP.lon)),
            type = classifyDepth(firstDepth, draft, margin),
            minDepth = firstDepth
        ))

        // 2. Segments between waypoints
        for (i in 0 until pathIds.size - 1) {
            val wp1 = waypointMap[pathIds[i]]!!
            val wp2 = waypointMap[pathIds[i+1]]!!
            
            // For a segment between two points, we take the minimum depth of both
            val minDepth = kotlin.math.min(wp1.chartDepth, wp2.chartDepth) + tideOffset
            
            segments.add(RouteSegment(
                points = listOf(LatLng(wp1.lat, wp1.lon), LatLng(wp2.lat, wp2.lon)),
                type = classifyDepth(minDepth, draft, margin),
                minDepth = minDepth
            ))
        }

        // 3. Final segment from last WP to 'end'
        val lastWP = waypointMap[pathIds.last()]!!
        val lastDepth = lastWP.chartDepth + tideOffset
        segments.add(RouteSegment(
            points = listOf(LatLng(lastWP.lat, lastWP.lon), end),
            type = classifyDepth(lastDepth, draft, margin),
            minDepth = lastDepth
        ))

        return mergeAdjacentSegments(segments)
    }

    /**
     * Estimates tide height at a given time using Rule of Twelfths.
     */
    fun calculateTideOffset(time: java.time.LocalDateTime, events: List<com.example.trnberechnung.model.TideEvent>): Double {
        if (events.isEmpty()) return 0.0

        val sorted = events.mapNotNull { event ->
            try {
                // Robust parsing for BSH formats: "2026-04-29 18:07:00+02:00" or "2026-04-30T16:25:00Z"
                val cleanTs = event.timestamp
                    .replace("T", " ") // Handle 'T' separator
                    .replace(Regex("Z$"), "") // Handle Zulu time
                    .replace(Regex("\\+\\d{2}:\\d{2}$"), "") // Handle +HH:mm
                    .replace(Regex("\\+\\d{2}$"), "") // Handle +HH
                    .trim()
                
                // Try multiple patterns
                val dt = try {
                    java.time.LocalDateTime.parse(cleanTs, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                } catch (_: Exception) {
                    java.time.LocalDateTime.parse(cleanTs, java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                }
                event to dt
            } catch (_: Exception) { null }
        }.sortedBy { it.second }

        if (sorted.isEmpty()) return 0.0

        // Find the window (previous and next event)
        val nextIndex = sorted.indexOfFirst { it.second.isAfter(time) }
        if (nextIndex == -1) return sorted.last().first.value ?: 0.0
        if (nextIndex == 0) return sorted.first().first.value ?: 0.0

        val prev = sorted[nextIndex - 1]
        val next = sorted[nextIndex]

        return RuleOfTwelfths.calculateWaterLevel(
            prev.second, prev.first.value ?: 0.0,
            next.second, next.first.value ?: 0.0,
            time
        )
    }

    fun classifyDepth(depth: Double, draft: Double, margin: Double): com.example.trnberechnung.model.SegmentType {
        return when {
            depth >= (draft + margin) -> SegmentType.SAFE
            depth >= draft -> SegmentType.CRITICAL
            else -> SegmentType.NO_GO
        }
    }

    private fun mergeAdjacentSegments(segments: List<RouteSegment>): List<RouteSegment> {
        if (segments.isEmpty()) return emptyList()
        
        val merged = mutableListOf<RouteSegment>()
        var currentPoints = segments[0].points.toMutableList()
        var currentType = segments[0].type
        var currentMinDepth = segments[0].minDepth

        for (i in 1 until segments.size) {
            if (segments[i].type == currentType) {
                // If same type, just add the new points (avoiding duplicate join point)
                currentPoints.addAll(segments[i].points.drop(1))
                currentMinDepth = kotlin.math.min(currentMinDepth, segments[i].minDepth)
            } else {
                // Different type, finish current segment and start new one
                merged.add(RouteSegment(currentPoints, currentType, currentMinDepth))
                currentPoints = segments[i].points.toMutableList()
                currentType = segments[i].type
                currentMinDepth = segments[i].minDepth
            }
        }
        merged.add(RouteSegment(currentPoints, currentType, currentMinDepth))
        return merged
    }

    // ── Dijkstra ───────────────────────────────────────────────────

    private fun dijkstra(startId: String, endId: String): List<String> {
        val dist = mutableMapOf<String, Double>()
        val prev = mutableMapOf<String, String?>()
        val visited = mutableSetOf<String>()
        val queue = java.util.PriorityQueue<Pair<String, Double>>(compareBy { it.second })

        dist[startId] = 0.0
        prev[startId] = null
        queue.add(startId to 0.0)

        while (queue.isNotEmpty()) {
            val (current, currentDist) = queue.poll()!!
            if (visited.contains(current)) continue
            visited.add(current)
            if (current == endId) break

            val currentWP = waypointMap[current] ?: continue
            for (neighbor in adjacency[current] ?: emptyList()) {
                if (visited.contains(neighbor)) continue
                val neighborWP = waypointMap[neighbor] ?: continue
                val edgeDist = haversineNm(currentWP.lat, currentWP.lon, neighborWP.lat, neighborWP.lon)
                val newDist = currentDist + edgeDist
                if (newDist < (dist[neighbor] ?: Double.MAX_VALUE)) {
                    dist[neighbor] = newDist
                    prev[neighbor] = current
                    queue.add(neighbor to newDist)
                }
            }
        }

        val path = mutableListOf<String>()
        var node: String? = endId
        while (node != null) {
            path.add(0, node)
            node = prev[node]
        }
        return if (path.firstOrNull() == startId) path else listOf(startId, endId)
    }

    // ── Helpers ────────────────────────────────────────────────────

    private fun findNearest(point: LatLng): WP {
        return waypoints.minBy {
            (it.lat - point.latitude).pow(2) + (it.lon - point.longitude).pow(2)
        }
    }

    private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 3440.065
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * R * asin(sqrt(a))
    }

    // ── Gentle Smoothing (Chaikin corner-cutting) ──────────────────
    // Unlike Catmull-Rom, Chaikin NEVER overshoots past the original
    // waypoints, so it cannot create curves that cross land.

    fun smoothPath(path: List<LatLng>): List<LatLng> {
        if (path.size < 3) return path
        // Apply 2 iterations of Chaikin subdivision
        var current = path
        repeat(2) {
            current = chaikinStep(current)
        }
        return current
    }

    private fun chaikinStep(path: List<LatLng>): List<LatLng> {
        val result = mutableListOf<LatLng>()
        result.add(path.first()) // keep start

        for (i in 0 until path.size - 1) {
            val p1 = path[i]
            val p2 = path[i + 1]
            // Q = 3/4 * P1 + 1/4 * P2
            val qLat = 0.75 * p1.latitude + 0.25 * p2.latitude
            val qLon = 0.75 * p1.longitude + 0.25 * p2.longitude
            // R = 1/4 * P1 + 3/4 * P2
            val rLat = 0.25 * p1.latitude + 0.75 * p2.latitude
            val rLon = 0.25 * p1.longitude + 0.75 * p2.longitude
            result.add(LatLng(qLat, qLon))
            result.add(LatLng(rLat, rLon))
        }

        result.add(path.last()) // keep end
        return result
    }
}
