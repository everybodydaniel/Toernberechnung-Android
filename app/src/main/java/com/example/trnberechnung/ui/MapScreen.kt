package com.example.trnberechnung.ui

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.trnberechnung.model.TideStationData
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.utils.ColorUtils
import android.graphics.Color as AndroidColor

private const val TAG = "MapScreen"

/**
 * Wrapper that prevents Compose's scroll from stealing map touches.
 */
class TouchInterceptingFrameLayout(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return false
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN ->
                parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Holds all mutable map state in one place so we can guard against
 * use-after-destroy crashes.
 */
private class MapState {
    var mapLibreMap: MapLibreMap? = null
    var symbolManager: SymbolManager? = null
    var lineManager: LineManager? = null
    var circleManager: CircleManager? = null
    var isDestroyed = false
}

@Composable
fun MapScreen(
    stations: List<TideStationData>,
    routePoints: List<LatLng> = emptyList(),
    routeSegments: List<com.example.trnberechnung.model.RouteSegment> = emptyList(),
    depthPoints: List<com.example.trnberechnung.model.DepthPoint> = emptyList(),
    harbors: List<TideStationData> = emptyList(),
    selectedStartHarbor: TideStationData? = null,
    selectedEndHarbor: TideStationData? = null,
    onHarborClick: (TideStationData) -> Unit = {},
    onMapClick: (LatLng) -> Unit,
    onStationSelected: (TideStationData) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var mapError by remember { mutableStateOf<String?>(null) }
    val mapState = remember { MapState() }
    // Reaktive Beobachtung des FairwayLoader-Status (Peildaten-Banner)
    val fairwayLoaded by com.example.trnberechnung.logic.FairwayLoader.isLoadedFlow.collectAsState()

    if (mapError != null) {
        // Error fallback
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text("⚠️", fontSize = 36.sp)
            androidx.compose.material3.Text(
                "Karte konnte nicht geladen werden",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
            androidx.compose.material3.Text(
                mapError ?: "",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.padding(top = 4.dp)
            )
            androidx.compose.material3.Button(
                onClick = {
                    mapState.isDestroyed = false
                    mapError = null
                },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                androidx.compose.material3.Text("Erneut versuchen")
            }
        }
        return
    }

    // Auto-Camera: zoom to show route when it changes
    LaunchedEffect(routePoints) {
        val map = mapState.mapLibreMap ?: return@LaunchedEffect
        if (mapState.isDestroyed) return@LaunchedEffect
        try {
            if (routePoints.isNotEmpty()) {
                val boundsBuilder = LatLngBounds.Builder()
                routePoints.forEach { boundsBuilder.include(it) }
                
                // Only animate if we have at least 2 points far enough apart
                // otherwise a simple zoom is better
                if (routePoints.size >= 2) {
                    val bounds = boundsBuilder.build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120), 800)
                } else {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 12.0), 600)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera animation failed", e)
        }
    }

    // Line & Marker & Circle updates
    LaunchedEffect(stations, routePoints, routeSegments, depthPoints, harbors, selectedStartHarbor, selectedEndHarbor, mapState.symbolManager, mapState.lineManager, mapState.circleManager) {
        val sm = mapState.symbolManager ?: return@LaunchedEffect
        val lm = mapState.lineManager ?: return@LaunchedEffect
        val cm = mapState.circleManager ?: return@LaunchedEffect
        if (mapState.isDestroyed) return@LaunchedEffect
        
        try {
            sm.deleteAll()
            lm.deleteAll()
            cm.deleteAll()

            // 0. Draw Harbor Markers (⚓ clickable points for all Wadden Sea harbors)
            harbors.forEach { harbor ->
                val isStart = selectedStartHarbor?.area == harbor.area
                val isEnd = selectedEndHarbor?.area == harbor.area
                val circleColor = when {
                    isStart -> "#1B5E20"  // Dark green
                    isEnd -> "#B71C1C"    // Dark red
                    else -> "#0D47A1"     // Deep blue
                }
                val radius = if (isStart || isEnd) 9f else 6f
                val strokeWidth = if (isStart || isEnd) 3f else 1.5f

                cm.create(
                    CircleOptions()
                        .withLatLng(LatLng(harbor.latitude, harbor.longitude))
                        .withCircleRadius(radius)
                        .withCircleColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(circleColor)))
                        .withCircleStrokeColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withCircleStrokeWidth(strokeWidth)
                )

                // Label + click target
                val labelPrefix = when {
                    isStart -> "🟢 "
                    isEnd -> "🔴 "
                    else -> "⚓ "
                }
                sm.create(
                    SymbolOptions()
                        .withLatLng(LatLng(harbor.latitude, harbor.longitude))
                        .withTextField("$labelPrefix${harbor.gaugeLabel ?: harbor.area}")
                        .withTextSize(if (isStart || isEnd) 12f else 10f)
                        .withTextColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withTextHaloColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(circleColor)))
                        .withTextHaloWidth(1.5f)
                        .withTextOffset(arrayOf(0f, 1.5f))
                ).apply {
                    this.data = com.google.gson.JsonPrimitive("harbor:${harbor.area}")
                }
            }

            // 1. Draw Depth Points (Heatmap/Area view)
            depthPoints.forEach { dp ->
                val color = when (dp.type) {
                    com.example.trnberechnung.model.SegmentType.SAFE -> "#4CAF50"    // Green
                    com.example.trnberechnung.model.SegmentType.CRITICAL -> "#FF9800" // Orange
                    com.example.trnberechnung.model.SegmentType.NO_GO -> "#F44336"    // Red
                }
                
                cm.create(
                    CircleOptions()
                        .withLatLng(dp.position)
                        .withCircleRadius(4f)
                        .withCircleColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                        .withCircleOpacity(0.5f)
                )

                // Restored depth labels in white as "not all" names should be removed
                sm.create(
                    SymbolOptions()
                        .withLatLng(dp.position)
                        .withTextField("%.1f".format(dp.depth))
                        .withTextSize(9f)
                        .withTextColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withTextHaloColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                        .withTextHaloWidth(1f)
                        .withTextOpacity(0.7f)
                )
            }

            // 2. Draw Polyline (either segmented or single) — Mockup-Stil:
            //    Grün (NauticalPrimary) für sicher, Amber für kritisch, Rot für No-Go.
            if (routeSegments.isNotEmpty()) {
                routeSegments.forEach { segment ->
                    val color = when (segment.type) {
                        com.example.trnberechnung.model.SegmentType.SAFE -> "#00BFA6"    // Teal-Grün
                        com.example.trnberechnung.model.SegmentType.CRITICAL -> "#FFB74D" // Amber
                        com.example.trnberechnung.model.SegmentType.NO_GO -> "#FF5252"    // Rot
                    }
                    lm.create(
                        LineOptions()
                            .withLatLngs(segment.points)
                            .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                            .withLineWidth(5f)
                    )
                }
            } else if (routePoints.size >= 2) {
                lm.create(
                    LineOptions()
                        .withLatLngs(routePoints)
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#00BFA6")))
                        .withLineWidth(5f)
                )
            }

            // 3. Station Markers (Circles remain, Names removed as requested)
            stations.forEach { station ->
                // Adding a transparent symbol for clicking.
                sm.create(
                    SymbolOptions()
                        .withLatLng(LatLng(station.latitude, station.longitude))
                        .withIconOpacity(0f)
                        .withTextOpacity(0f)
                ).apply {
                    this.data = com.google.gson.JsonPrimitive(station.area)
                }
            }

            // 4. Draw Start/End Route Markers & Circles
            if (routePoints.isNotEmpty()) {
                val start = routePoints.first()

                // Circle for visibility
                cm.create(
                    CircleOptions()
                        .withLatLng(start)
                        .withCircleRadius(8f)
                        .withCircleColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#1B5E20")))
                        .withCircleStrokeColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withCircleStrokeWidth(2f)
                )

                sm.create(
                    SymbolOptions()
                        .withLatLng(start)
                        .withTextField("📍 Start")
                        .withTextSize(18f)
                        .withTextColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#1B5E20")))
                        .withTextHaloColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withTextHaloWidth(2f)
                        .withTextOffset(arrayOf(0f, -1.2f))
                )

                if (routePoints.size > 1) {
                    val end = routePoints.last()

                    // Circle for visibility
                    cm.create(
                        CircleOptions()
                            .withLatLng(end)
                            .withCircleRadius(8f)
                            .withCircleColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#B71C1C")))
                            .withCircleStrokeColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                            .withCircleStrokeWidth(2f)
                    )

                    sm.create(
                        SymbolOptions()
                            .withLatLng(end)
                            .withTextField("🏁 Ziel")
                            .withTextSize(18f)
                            .withTextColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#B71C1C")))
                            .withTextHaloColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                            .withTextHaloWidth(2f)
                            .withTextOffset(arrayOf(0f, -1.2f))
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Map visuals update failed", e)
        }
    }

    // The actual map UI
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                // Everything is created together in the factory - no split between
                // remember and factory which caused the "view already has parent" crash.
                val wrapper = TouchInterceptingFrameLayout(ctx)

                try {
                    MapLibre.getInstance(ctx)
                } catch (e: Exception) {
                    Log.e(TAG, "MapLibre.getInstance failed", e)
                    mapError = "Init: ${e.localizedMessage}"
                    return@AndroidView wrapper
                }

                val mapView = MapView(ctx)
                wrapper.addView(
                    mapView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )

                // Tag the mapView on the wrapper so we can retrieve it for lifecycle
                wrapper.tag = mapView

                try {
                    mapView.onCreate(null)
                    mapView.onStart()
                    mapView.onResume()
                } catch (e: Exception) {
                    Log.e(TAG, "MapView lifecycle init failed", e)
                    mapError = "Lifecycle: ${e.localizedMessage}"
                    return@AndroidView wrapper
                }

                mapView.getMapAsync { map ->
                    if (mapState.isDestroyed) return@getMapAsync
                    mapState.mapLibreMap = map

                    try {
                        map.uiSettings.isZoomGesturesEnabled = true
                        map.uiSettings.isScrollGesturesEnabled = true
                        map.uiSettings.isRotateGesturesEnabled = true
                        map.uiSettings.isTiltGesturesEnabled = true

                        map.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(53.7, 7.5))
                            .zoom(8.0)
                            .build()

                        // Restrict zoom range so user can't zoom out to world view
                        map.setMinZoomPreference(5.0)
                        map.setMaxZoomPreference(18.0)

                        map.setStyle(
                            Style.Builder().fromJson(
                                """
                                {
                                  "version": 8,
                                  "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
                                  "sources": {
                                    "osm": {
                                      "type": "raster",
                                      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                                      "tileSize": 256
                                    },
                                    "openseamap": {
                                      "type": "raster",
                                      "tiles": ["https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png"],
                                      "tileSize": 256
                                    }
                                  },
                                  "layers": [
                                    {"id": "osm-layer", "type": "raster", "source": "osm"},
                                    {"id": "openseamap-layer", "type": "raster", "source": "openseamap"}
                                  ]
                                }
                                """.trimIndent()
                            )
                        ) { style ->
                            if (mapState.isDestroyed) return@setStyle
                            // Restrict camera panning to NL / DE / DK region
                            val regionBounds = LatLngBounds.Builder()
                                .include(LatLng(50.5, 3.3))   // SW – southern Netherlands
                                .include(LatLng(57.8, 15.2))  // NE – northern Denmark
                                .build()
                            map.setLatLngBoundsForCameraTarget(regionBounds)
                            try {
                                val sm = SymbolManager(mapView, map, style)
                                sm.iconAllowOverlap = true
                                sm.textAllowOverlap = true
                                mapState.symbolManager = sm
                                mapState.lineManager = LineManager(mapView, map, style)
                                mapState.circleManager = CircleManager(mapView, map, style)

                                sm.addClickListener { symbol ->
                                    if (mapState.isDestroyed) return@addClickListener false

                                    // Daten SOFORT auslesen — sobald wir asynchron werden,
                                    // kann das Symbol durch sm.deleteAll() im LaunchedEffect
                                    // schon ungültig sein.
                                    val data = try {
                                        symbol.data?.asString.orEmpty()
                                    } catch (_: Exception) { "" }

                                    if (data.isBlank()) return@addClickListener true

                                    // Auf den Main-Looper posten, damit der State-Change nicht
                                    // mitten im MapLibre-Touch-Dispatch passiert.
                                    mapView.post {
                                        if (mapState.isDestroyed) return@post
                                        try {
                                            if (data.startsWith("harbor:")) {
                                                val harborName = data.removePrefix("harbor:")
                                                harbors.firstOrNull { it.area == harborName }
                                                    ?.let { onHarborClick(it) }
                                            } else {
                                                stations.firstOrNull { it.area == data }
                                                    ?.let { onStationSelected(it) }
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Harbor/Station click handler failed", e)
                                        }
                                    }
                                    true
                                }
                                map.addOnMapClickListener { point ->
                                    if (mapState.isDestroyed) return@addOnMapClickListener false
                                    try {
                                        onMapClick(point)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Map click handler failed", e)
                                    }
                                    true
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Style setup failed", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Map async setup failed", e)
                    }
                }

                wrapper
            },
            update = { wrapper ->
                // Update lifecycle if needed (handled by DisposableEffect mostly)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Peildaten-Banner — sichtbar sobald GeoJSON-Fahrwasser/Schutzzonen geladen sind.
        // Entspricht dem Mockup-Hinweis „PEILDATEN & TIDE-BERECHNUNG AKTUALISIERT & VERWENDET".
        if (fairwayLoaded) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp, start = 60.dp, end = 60.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC0D1B2A))  // NauticalBackground mit Alpha
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text("📍", fontSize = 11.sp)
                Spacer(modifier = Modifier.width(6.dp))
                androidx.compose.material3.Text(
                    "PEILDATEN & TIDE-BERECHNUNG AKTUALISIERT & VERWENDET.",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )
            }
        }

        // Zoom Controls
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .shadow(4.dp, RoundedCornerShape(8.dp))
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        if (!mapState.isDestroyed) {
                            mapState.mapLibreMap?.let { map ->
                                try { map.animateCamera(CameraUpdateFactory.zoomIn(), 200) }
                                catch (_: Exception) {}
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text("+", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            }
            Box(modifier = Modifier.width(44.dp).height(1.dp).background(Color(0xFFE0E0E0)))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        if (!mapState.isDestroyed) {
                            mapState.mapLibreMap?.let { map ->
                                try { map.animateCamera(CameraUpdateFactory.zoomOut(), 200) }
                                catch (_: Exception) {}
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text("−", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            }
        }
    }

    // Proper lifecycle management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            // Note: We don't have a direct reference to mapView here anymore
            // because it's managed inside AndroidView. 
            // However, we should at least mark state as destroyed on dispose.
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapState.isDestroyed = true
            try {
                mapState.symbolManager?.deleteAll()
                mapState.lineManager?.deleteAll()
                mapState.circleManager?.deleteAll()
                mapState.symbolManager = null
                mapState.lineManager = null
                mapState.circleManager = null
                mapState.mapLibreMap = null
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup error", e)
            }
        }
    }
}
