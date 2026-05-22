package com.example.trnberechnung.ui

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.trnberechnung.network.IslandCoordinatesProvider.IslandData
import com.example.trnberechnung.ui.theme.*
import com.example.trnberechnung.viewmodel.RoutePlanningViewModel
import com.example.trnberechnung.viewmodel.TideViewModel
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

import java.time.format.DateTimeFormatter

private const val TAG = "RoutePlanningScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IslandRoutePlanningScreen(viewModel: RoutePlanningViewModel, tideViewModel: TideViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val tideEvents by tideViewModel.currentTideEvents.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(tideEvents) {
        viewModel.loadAndCalculate(tideEvents)
    }

    val consumeAllScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return available
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NauticalBackground)
            .verticalScroll(scrollState)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "INSELROUTE",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = NauticalTextPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    "Ostfriesische Inseln – Seeroute",
                    style = MaterialTheme.typography.bodySmall,
                    color = NauticalTextSecondary
                )
            }
            IconButton(
                onClick = { viewModel.loadAndCalculate(tideEvents) },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(NauticalSurfaceVariant)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Aktualisieren",
                    tint = NauticalPrimary
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NauticalInfoBg)
                    .border(1.dp, NauticalPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = NauticalPrimary,
                        strokeWidth = 2.dp
                    )
                    Text(
                        uiState.statusMessage,
                        color = NauticalInfoText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NauticalNoGoBg)
                    .border(1.dp, NauticalNoGo.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    uiState.error ?: "",
                    color = NauticalNoGo,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .padding(horizontal = 16.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, NauticalDivider, RoundedCornerShape(16.dp))
                .nestedScroll(consumeAllScrollConnection)
        ) {
        IslandRouteMap(
            islands = uiState.islands,
            route = uiState.route,
            routeSegments = uiState.routeSegments,
            depthPoints = uiState.depthPoints
        )
        }

        Spacer(modifier = Modifier.height(16.dp))

        DepartureTimeSection(
            currentTime = uiState.departureTime,
            onTimeChanged = { newTime ->
                viewModel.updateDepartureTime(newTime, tideEvents)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (!uiState.isLoading && uiState.route.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RouteInfoCard(
                    modifier = Modifier.weight(1f),
                    label = "INSELN",
                    value = "${uiState.islands.size}",
                    detail = "Stationen"
                )
                RouteInfoCard(
                    modifier = Modifier.weight(1f),
                    label = "DISTANZ",
                    value = "%.1f".format(uiState.routeDistanceNm),
                    detail = "Seemeilen"
                )
                RouteInfoCard(
                    modifier = Modifier.weight(1f),
                    label = "ROUTE",
                    value = "${uiState.route.size}",
                    detail = "Wegpunkte"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                RouteLineStart.copy(alpha = 0.15f),
                                RouteLineEnd.copy(alpha = 0.15f)
                            )
                        )
                    )
                    .border(
                        1.dp,
                        Brush.horizontalGradient(
                            listOf(RouteLineStart.copy(alpha = 0.4f), RouteLineEnd.copy(alpha = 0.4f))
                        ),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        "🧭 SEEROUTE BERECHNET",
                        fontWeight = FontWeight.ExtraBold,
                        color = NauticalPrimary,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        uiState.statusMessage,
                        color = NauticalTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (uiState.islands.isNotEmpty()) {
            Text(
                "INSELN DER ROUTE",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = NauticalTextSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                letterSpacing = 0.5.sp
            )

            uiState.islands
                .sortedBy { it.harborPosition.longitude }
                .forEachIndexed { index, island ->
                    IslandCard(index = index + 1, island = island, isLast = index == uiState.islands.size - 1)
                }
        }

        Spacer(modifier = Modifier.height(100.dp))
    }
}

@Composable
private fun DepartureTimeSection(
    currentTime: java.time.LocalDateTime,
    onTimeChanged: (java.time.LocalDateTime) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val formatter = DateTimeFormatter.ofPattern("HH:mm 'Uhr' (dd.MM.)")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(NauticalSurface)
            .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp))
            .clickable { showDialog = true }
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    "SIMULIERTE ABFAHRTSZEIT",
                    style = MaterialTheme.typography.labelSmall,
                    color = NauticalTextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    currentTime.format(formatter),
                    style = MaterialTheme.typography.bodyLarge,
                    color = NauticalPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                tint = NauticalPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Tippen, um die Zeit zu ändern und Gezeiteneinfluss zu sehen.",
            style = MaterialTheme.typography.bodySmall,
            color = NauticalTextSecondary.copy(alpha = 0.8f)
        )
    }

    if (showDialog) {

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Zeit anpassen") },
            text = {
                Column {
                    Text("Simulation der Gezeiten für:")
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { 
                            onTimeChanged(currentTime.minusHours(1))
                        }) { Text("-1h") }
                        Button(onClick = { 
                            onTimeChanged(currentTime.plusHours(1))
                        }) { Text("+1h") }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { 
                        onTimeChanged(java.time.LocalDateTime.now())
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Aktuelle Zeit")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Fertig")
                }
            }
        )
    }
}

@Composable
private fun RouteInfoCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    detail: String
) {
    Card(
        modifier = modifier
            .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = NauticalSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                label,
                color = NauticalTextSecondary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                value,
                color = NauticalPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                detail,
                color = NauticalTextSecondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun IslandCard(index: Int, island: IslandData, isLast: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(RouteLineStart, RouteLineEnd)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$index",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, NauticalDivider, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = NauticalSurface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = NauticalPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        island.name,
                        fontWeight = FontWeight.Bold,
                        color = NauticalTextPrimary,
                        fontSize = 15.sp
                    )
                    if (island.description.isNotEmpty()) {
                        Text(
                            island.description,
                            color = NauticalTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "%.3f°N".format(island.harborPosition.latitude),
                        color = NauticalTextSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        "%.3f°E".format(island.harborPosition.longitude),
                        color = NauticalTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    if (!isLast) {
        Box(
            modifier = Modifier
                .padding(start = 31.dp)
                .width(2.dp)
                .height(8.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(RouteLineStart.copy(alpha = 0.5f), RouteLineEnd.copy(alpha = 0.2f))
                    )
                )
        )
    }
}

private class IslandMapState {
    var mapLibreMap: MapLibreMap? = null
    var symbolManager: SymbolManager? = null
    var lineManager: LineManager? = null
    var circleManager: CircleManager? = null
    var isDestroyed = false
}

@Composable
private fun IslandRouteMap(
    islands: List<IslandData>,
    route: List<LatLng>,
    routeSegments: List<com.example.trnberechnung.model.RouteSegment> = emptyList(),
    depthPoints: List<com.example.trnberechnung.model.DepthPoint> = emptyList()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapError by remember { mutableStateOf<String?>(null) }
    val mapState = remember { IslandMapState() }

    if (mapError != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NauticalSurface),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("⚠️", fontSize = 36.sp)
                Text(
                    "Karte konnte nicht geladen werden",
                    fontWeight = FontWeight.Bold,
                    color = NauticalTextPrimary,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Text(
                    mapError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = NauticalTextSecondary,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Button(
                    onClick = {
                        mapState.isDestroyed = false
                        mapError = null
                    },
                    modifier = Modifier.padding(top = 16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NauticalPrimary)
                ) {
                    Text("Erneut versuchen", color = NauticalTextOnPrimary)
                }
            }
        }
        return
    }

    LaunchedEffect(route) {
        val map = mapState.mapLibreMap ?: return@LaunchedEffect
        if (mapState.isDestroyed) return@LaunchedEffect
        try {
            if (route.size >= 2) {
                val boundsBuilder = LatLngBounds.Builder()
                route.forEach { boundsBuilder.include(it) }
                islands.forEach { boundsBuilder.include(it.position) }
                val bounds = boundsBuilder.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80), 1000)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera animation failed", e)
        }
    }

    LaunchedEffect(islands, route, routeSegments, depthPoints, mapState.symbolManager, mapState.lineManager, mapState.circleManager) {
        val sm = mapState.symbolManager ?: return@LaunchedEffect
        val lm = mapState.lineManager ?: return@LaunchedEffect
        val cm = mapState.circleManager ?: return@LaunchedEffect
        if (mapState.isDestroyed) return@LaunchedEffect

        try {
            sm.deleteAll()
            lm.deleteAll()
            cm.deleteAll()

            depthPoints.forEach { dp ->
                val color = when (dp.type) {
                    com.example.trnberechnung.model.SegmentType.SAFE -> "#007AFF"
                    com.example.trnberechnung.model.SegmentType.CRITICAL -> "#FF9500"
                    com.example.trnberechnung.model.SegmentType.NO_GO -> "#FF3B30"
                }

                cm.create(
                    CircleOptions()
                        .withLatLng(dp.position)
                        .withCircleRadius(5f)
                        .withCircleColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                        .withCircleOpacity(0.4f)
                )

                sm.create(
                    SymbolOptions()
                        .withLatLng(dp.position)
                        .withTextField("%.1f".format(dp.depth))
                        .withTextSize(8f)
                        .withTextColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                        .withTextHaloColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                        .withTextHaloWidth(1f)
                        .withTextOpacity(0.6f)
                )
            }

            if (routeSegments.isNotEmpty()) {
                routeSegments.forEach { segment ->
                    val color = when (segment.type) {
                        com.example.trnberechnung.model.SegmentType.SAFE -> "#007AFF"    
                        com.example.trnberechnung.model.SegmentType.CRITICAL -> "#FF9500" 
                        com.example.trnberechnung.model.SegmentType.NO_GO -> "#FF3B30"    
                    }
                    lm.create(
                        LineOptions()
                            .withLatLngs(segment.points)
                            .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                            .withLineWidth(4.5f)
                    )

                    if (segment.type != com.example.trnberechnung.model.SegmentType.SAFE && segment.points.isNotEmpty()) {
                        val midIndex = segment.points.size / 2
                        val midPoint = segment.points[midIndex]

                        sm.create(
                            SymbolOptions()
                                .withLatLng(midPoint)
                                .withTextField("%.1fm".format(segment.minDepth))
                                .withTextSize(11f)
                                .withTextColor(ColorUtils.colorToRgbaString(AndroidColor.WHITE))
                                .withTextHaloColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor(color)))
                                .withTextHaloWidth(2f)
                                .withTextOffset(arrayOf(0f, -1.2f))
                        )
                    }
                }
            } else if (route.size >= 2) {
                lm.create(
                    LineOptions()
                        .withLatLngs(route)
                        .withLineColor(ColorUtils.colorToRgbaString(AndroidColor.parseColor("#0088FF")))
                        .withLineWidth(4.5f)
                )
            }

            islands.forEach { island ->

            }
        } catch (e: Exception) {
            Log.w(TAG, "Map visuals update failed", e)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
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
                            .target(LatLng(53.72, 7.35))
                            .zoom(8.5)
                            .build()

                        map.setMinZoomPreference(6.0)
                        map.setMaxZoomPreference(16.0)

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

                            val regionBounds = LatLngBounds.Builder()
                                .include(LatLng(52.5, 5.0))
                                .include(LatLng(55.0, 10.0))
                                .build()
                            map.setLatLngBoundsForCameraTarget(regionBounds)

                            try {
                                val sm = SymbolManager(mapView, map, style)
                                sm.iconAllowOverlap = true
                                sm.textAllowOverlap = true
                                mapState.symbolManager = sm
                                mapState.lineManager = LineManager(mapView, map, style)
                                mapState.circleManager = CircleManager(mapView, map, style)
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
            modifier = Modifier.fillMaxSize()
        )

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
                    .size(40.dp)
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
                Text("+", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            }
            Box(modifier = Modifier.width(40.dp).height(1.dp).background(Color(0xFFE0E0E0)))
            Box(
                modifier = Modifier
                    .size(40.dp)
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
                Text("−", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF333333))
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapState.isDestroyed = true
            mapState.symbolManager = null
            mapState.lineManager = null
            mapState.circleManager = null
            mapState.mapLibreMap = null
        }
    }
}
