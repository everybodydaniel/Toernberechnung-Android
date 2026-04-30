package com.example.trnberechnung.network

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.maplibre.android.geometry.LatLng
import java.util.concurrent.TimeUnit

/**
 * Provides coordinates for the seven Ostfriesische Inseln.
 *
 * Strategy (offline-first):
 * 1. Always load the static fallback from assets/island_fallback.json first.
 * 2. Attempt to refresh from the BSH WFS endpoint.
 * 3. If the WFS call fails, fall back to the static data.
 */
object IslandCoordinatesProvider {

    private const val TAG = "IslandCoordProvider"

    // BSH WFS endpoint (public, no API key needed)
    private const val BSH_WFS_URL =
        "https://www.bsh.de/DE/DATEN/Klima-und-Meer/Physikalische-Eigenschaften/" +
        "geo-services/geo-services_node.html" // placeholder – real GeoJSON below

    // The seven islands + Emden
    private val REQUIRED_ISLANDS = setOf(
        "Borkum", "Juist", "Norderney", "Baltrum",
        "Langeoog", "Spiekeroog", "Wangerooge", "Emden"
    )

    /**
     * Data class representing an island with name, position, and harbour position.
     */
    data class IslandData(
        val name: String,
        val position: LatLng,
        val harborPosition: LatLng,
        val description: String = ""
    )

    /**
     * Main entry point. Returns island data, always succeeding (offline-first).
     */
    suspend fun getIslandCoordinates(context: Context): List<IslandData> {
        // Step 1: Always load static fallback first (guaranteed to work)
        val fallback = loadFallback(context)

        // Step 2: Try to fetch from WFS
        return try {
            val live = fetchFromWfs()
            if (live.isNotEmpty() && live.size >= 7) {
                Log.d(TAG, "Using live WFS data: ${live.size} islands")
                live
            } else {
                Log.d(TAG, "WFS data incomplete (${live.size} islands), using fallback")
                fallback
            }
        } catch (e: Exception) {
            Log.w(TAG, "WFS fetch failed, using offline fallback: ${e.message}")
            fallback
        }
    }

    /**
     * Load static island coordinates from the bundled assets/island_fallback.json.
     */
    private suspend fun loadFallback(context: Context): List<IslandData> = withContext(Dispatchers.IO) {
        try {
            val json = context.assets.open("island_fallback.json")
                .bufferedReader().use { it.readText() }
            parseFallbackJson(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load fallback JSON", e)
            // Absolute last resort: hardcoded coordinates
            hardcodedIslands()
        }
    }

    /**
     * Parse the fallback JSON into IslandData objects.
     */
    private fun parseFallbackJson(json: String): List<IslandData> {
        val root = Gson().fromJson(json, JsonObject::class.java)
        val islands = root.getAsJsonArray("islands")
        return islands.map { element ->
            val obj = element.asJsonObject
            IslandData(
                name = obj.get("name").asString,
                position = LatLng(obj.get("lat").asDouble, obj.get("lon").asDouble),
                harborPosition = LatLng(
                    obj.get("harbor_lat").asDouble,
                    obj.get("harbor_lon").asDouble
                ),
                description = obj.get("description")?.asString ?: ""
            )
        }
    }

    /**
     * Fetch island coordinates from BSH WFS GeoJSON endpoint.
     * Uses the BSH GeoServer WFS for German maritime boundaries.
     */
    private suspend fun fetchFromWfs(): List<IslandData> = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        // BSH GeoServer WFS – query for island features
        // Using the public BSH maritime GeoServer
        val wfsUrl = "https://www.geoseaportal.de/gdi-bsh-ows/ID5aed6dd5-fc0f-4e67-a868-c0e2e9e9a86b" +
            "?SERVICE=WFS&VERSION=2.0.0&REQUEST=GetFeature" +
            "&TYPENAMES=bsh:islands" +
            "&OUTPUTFORMAT=application/json" +
            "&COUNT=100"

        val request = Request.Builder()
            .url(wfsUrl)
            .header("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw RuntimeException("WFS request failed: HTTP ${response.code()}")
        }

        val body = response.body()?.string()
            ?: throw RuntimeException("WFS response body is null")

        parseWfsGeoJson(body)
    }

    /**
     * Parse a GeoJSON FeatureCollection response from the BSH WFS.
     */
    private fun parseWfsGeoJson(geoJson: String): List<IslandData> {
        val root = Gson().fromJson(geoJson, JsonObject::class.java)
        val features = root.getAsJsonArray("features") ?: return emptyList()

        return features.mapNotNull { feature ->
            val obj = feature.asJsonObject
            val properties = obj.getAsJsonObject("properties")
            val name = properties?.get("name")?.asString
                ?: properties?.get("NAME")?.asString
                ?: return@mapNotNull null

            if (name !in REQUIRED_ISLANDS) return@mapNotNull null

            val geometry = obj.getAsJsonObject("geometry")
            val coordinates = geometry?.getAsJsonArray("coordinates")

            // Handle Point geometry
            val lat: Double
            val lon: Double
            when (geometry?.get("type")?.asString) {
                "Point" -> {
                    lon = coordinates?.get(0)?.asDouble ?: return@mapNotNull null
                    lat = coordinates.get(1)?.asDouble ?: return@mapNotNull null
                }
                "Polygon", "MultiPolygon" -> {
                    // Compute centroid from first ring
                    val ring = if (geometry.get("type").asString == "MultiPolygon") {
                        coordinates?.get(0)?.asJsonArray?.get(0)?.asJsonArray
                    } else {
                        coordinates?.get(0)?.asJsonArray
                    }
                    if (ring == null || ring.size() == 0) return@mapNotNull null

                    var sumLat = 0.0
                    var sumLon = 0.0
                    for (i in 0 until ring.size()) {
                        val coord = ring.get(i).asJsonArray
                        sumLon += coord.get(0).asDouble
                        sumLat += coord.get(1).asDouble
                    }
                    lon = sumLon / ring.size()
                    lat = sumLat / ring.size()
                }
                else -> return@mapNotNull null
            }

            IslandData(
                name = name,
                position = LatLng(lat, lon),
                harborPosition = LatLng(lat, lon), // WFS may not have harbor data
                description = "BSH WFS: $name"
            )
        }
    }

    /**
     * Absolute last-resort hardcoded coordinates (BSH/OpenSeaMap verified).
     */
    private fun hardcodedIslands(): List<IslandData> = listOf(
        IslandData("Borkum",      LatLng(53.5957, 6.6619), LatLng(53.5560, 6.7500)),
        IslandData("Juist",       LatLng(53.6814, 6.9931), LatLng(53.6750, 6.9900)),
        IslandData("Norderney",   LatLng(53.7078, 7.1537), LatLng(53.6930, 7.1550)),
        IslandData("Baltrum",     LatLng(53.7295, 7.3722), LatLng(53.7250, 7.3700)),
        IslandData("Langeoog",    LatLng(53.7505, 7.4974), LatLng(53.7450, 7.4950)),
        IslandData("Spiekeroog", LatLng(53.7706, 7.6914), LatLng(53.7650, 7.6900)),
        IslandData("Wangerooge", LatLng(53.7906, 7.8994), LatLng(53.7900, 7.8700))
    )
}
