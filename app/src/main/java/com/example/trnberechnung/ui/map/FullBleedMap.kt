package com.example.trnberechnung.ui.map

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions
import org.maplibre.android.plugins.annotation.FillManager
import org.maplibre.android.plugins.annotation.FillOptions
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import org.maplibre.android.utils.ColorUtils

/**
 * Every marker on the map belongs to a planned route. There is deliberately no "plain harbour"
 * role: drawing the whole harbour catalog covered the chart in dots and labels.
 */
enum class MapHarbourRole {
    START,
    STOP,
    DESTINATION,
}

data class MapHarbourMarker(
    val id: String,
    val title: String,
    val subtitle: String,
    val coordinate: LatLng,
    val role: MapHarbourRole,
    val order: Int? = null,
)

private class FullBleedMapState {
    var map: MapLibreMap? = null
    var fills: FillManager? = null
    var circles: CircleManager? = null
    var lines: LineManager? = null
    var symbols: SymbolManager? = null
    var started = false
    var resumed = false
    var destroyed = false
    var styleRequestId = 0
    var loadOnlineStyle: (() -> Unit)? = null
    var loadOfflineStyle: (() -> Unit)? = null

    fun synchronizeLifecycle(
        view: MapView,
        lifecycle: Lifecycle,
    ) {
        when {
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) -> resume(view)
            lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> start(view)
        }
    }

    fun start(view: MapView) {
        if (!started) {
            view.onStart()
            started = true
        }
    }

    fun resume(view: MapView) {
        start(view)
        if (!resumed) {
            view.onResume()
            resumed = true
        }
    }

    fun pause(view: MapView) {
        if (resumed) {
            view.onPause()
            resumed = false
        }
    }

    fun stop(view: MapView) {
        pause(view)
        if (started) {
            view.onStop()
            started = false
        }
    }
}

private enum class MapLoadStatus {
    STARTING,
    ONLINE_STYLE_READY,
    OFFLINE_FALLBACK,
    FAILED,
}

private const val FULL_BLEED_MAP_TAG = "FullBleedMap"
private const val MAP_START_TIMEOUT_MILLIS = 8_000L
private const val MAP_FALLBACK_TIMEOUT_MILLIS = 2_500L
private const val HARBOUR_DATA_PREFIX = "harbour:"
private const val WARNING_DATA_PREFIX = "warning:"
private val mapWarningRed = Color(0xFFDC2626)
private val mapWarningDarkRed = Color(0xFF991B1B)

@Composable
fun FullBleedMap(
    route: List<LatLng>,
    routeColor: Color,
    harbours: List<MapHarbourMarker>,
    modifier: Modifier = Modifier,
    breadcrumbs: List<LatLng> = emptyList(),
    currentLocation: LatLng? = null,
    headingDegrees: Double? = null,
    followLocation: Boolean = false,
    attributionTopMargin: Dp = 184.dp,
    warningOverlays: List<MapWarningOverlay> = emptyList(),
    focusedWarningId: String? = null,
    warningSummaryBottomPadding: Dp = 24.dp,
    onHarbourClick: (String) -> Unit = {},
    onMapClick: (LatLng) -> Unit = {},
    onWarningFocusConsumed: (String) -> Unit = {},
    onOpenWarning: (String) -> Unit = {},
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { FullBleedMapState() }
    var styleGeneration by remember { mutableIntStateOf(0) }
    var mapError by remember { mutableStateOf<String?>(null) }
    var mapNotice by remember { mutableStateOf<String?>(null) }
    var mapLoading by remember { mutableStateOf(true) }
    var mapLoadStatus by remember { mutableStateOf(MapLoadStatus.STARTING) }
    var selectedWarningId by remember { mutableStateOf<String?>(null) }
    val renderableWarnings = remember(warningOverlays) { validatedMapWarningOverlays(warningOverlays) }
    val focusedWarningAction =
        remember(renderableWarnings, focusedWarningId) {
            mapWarningFocusActionOrNull(renderableWarnings, focusedWarningId)
        }
    val selectedWarning =
        mapWarningFocusActionOrNull(renderableWarnings, selectedWarningId)?.overlay
    val currentOnHarbourClick by rememberUpdatedState(onHarbourClick)
    val currentOnMapClick by rememberUpdatedState(onMapClick)
    val currentWarningOverlays by rememberUpdatedState(renderableWarnings)
    val currentOnWarningFocusConsumed by rememberUpdatedState(onWarningFocusConsumed)
    val currentOnOpenWarning by rememberUpdatedState(onOpenWarning)
    var warningFocusGate by remember { mutableStateOf(MapWarningFocusGate()) }

    fun selectWarning(warningId: String): Boolean {
        val action = mapWarningFocusActionOrNull(currentWarningOverlays, warningId) ?: return false
        selectedWarningId = action.overlay.id
        return true
    }

    // Construct the MapView from AndroidView's Context. A LifecycleOwner is
    // deliberately not assumed to also be a Context.
    val mapViewHolder = remember { mutableStateOf<MapView?>(null) }

    Box(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                MapView(context).also { view ->
                    view.onCreate(Bundle())
                    // The view is created while the host Activity is already resumed in
                    // normal Compose navigation. Synchronise it immediately instead of
                    // waiting for a state-driven recomposition; otherwise getMapAsync
                    // can remain queued indefinitely on a not-yet-started renderer.
                    state.synchronizeLifecycle(view, lifecycleOwner.lifecycle)
                    mapViewHolder.value = view
                    view.addOnDidFailLoadingMapListener { error ->
                        if (state.destroyed) return@addOnDidFailLoadingMapListener
                        Log.w(FULL_BLEED_MAP_TAG, "MapLibre reported a loading error: $error")
                        when (mapLoadStatus) {
                            MapLoadStatus.STARTING -> state.loadOfflineStyle?.invoke()
                            MapLoadStatus.ONLINE_STYLE_READY -> {
                                mapNotice = "Kartenkacheln derzeit nicht vollständig verfügbar"
                                view.foreground = null
                            }
                            MapLoadStatus.OFFLINE_FALLBACK,
                            MapLoadStatus.FAILED,
                            -> Unit
                        }
                    }
                    view.getMapAsync { map ->
                        if (state.destroyed) return@getMapAsync
                        Log.d(FULL_BLEED_MAP_TAG, "MapLibre map instance is ready")
                        state.map = map
                        map.uiSettings.apply {
                            isCompassEnabled = false
                            isLogoEnabled = true
                            isAttributionEnabled = true
                            isZoomGesturesEnabled = true
                            isScrollGesturesEnabled = true
                            isRotateGesturesEnabled = true
                            isTiltGesturesEnabled = true
                            val topMargin =
                                (attributionTopMargin.value * view.resources.displayMetrics.density)
                                    .toInt()
                            setLogoGravity(Gravity.TOP or Gravity.START)
                            setLogoMargins(8, topMargin, 8, 8)
                            setAttributionGravity(Gravity.TOP or Gravity.END)
                            setAttributionMargins(8, topMargin, 8, 8)
                        }
                        map.cameraPosition =
                            CameraPosition.Builder()
                                .target(LatLng(53.7, 7.45))
                                .zoom(8.2)
                                .build()
                        map.setMinZoomPreference(5.0)
                        map.setMaxZoomPreference(19.0)

                        map.addOnMapClickListener {
                            currentOnMapClick(it)
                            false
                        }

                        fun loadStyle(
                            styleJson: String,
                            offlineFallback: Boolean,
                        ) {
                            val requestId = ++state.styleRequestId
                            if (offlineFallback) {
                                mapLoadStatus = MapLoadStatus.STARTING
                                mapLoading = true
                            }
                            runCatching {
                                map.setStyle(
                                    Style.Builder().fromJson(styleJson),
                                ) { style ->
                                    if (state.destroyed || requestId != state.styleRequestId) {
                                        return@setStyle
                                    }
                                    try {
                                        state.fills?.deleteAll()
                                        state.circles?.deleteAll()
                                        state.lines?.deleteAll()
                                        state.symbols?.deleteAll()
                                        state.fills = FillManager(view, map, style)
                                        state.circles = CircleManager(view, map, style)
                                        state.lines = LineManager(view, map, style)
                                        state.symbols =
                                            SymbolManager(view, map, style).apply {
                                                iconAllowOverlap = true
                                                textAllowOverlap = true
                                            }
                                        state.fills?.addClickListener { fill ->
                                            warningIdFromMapAnnotation(
                                                runCatching { fill.data?.asString.orEmpty() }
                                                    .getOrDefault(""),
                                            )?.let(::selectWarning) ?: false
                                        }
                                        state.lines?.addClickListener { line ->
                                            warningIdFromMapAnnotation(
                                                runCatching { line.data?.asString.orEmpty() }
                                                    .getOrDefault(""),
                                            )?.let(::selectWarning) ?: false
                                        }
                                        state.circles?.addClickListener { circle ->
                                            warningIdFromMapAnnotation(
                                                runCatching { circle.data?.asString.orEmpty() }
                                                    .getOrDefault(""),
                                            )?.let(::selectWarning) ?: false
                                        }
                                        state.symbols?.addClickListener { symbol ->
                                            val raw =
                                                runCatching { symbol.data?.asString.orEmpty() }
                                                    .getOrDefault("")
                                            when {
                                                raw.startsWith(HARBOUR_DATA_PREFIX) -> {
                                                    currentOnHarbourClick(raw.removePrefix(HARBOUR_DATA_PREFIX))
                                                    true
                                                }
                                                else ->
                                                    warningIdFromMapAnnotation(raw)
                                                        ?.let(::selectWarning)
                                                        ?: false
                                            }
                                        }
                                        // MapView keeps an opaque loading foreground until it
                                        // considers all remote tiles fully loaded. Removing it
                                        // after the local style is ready lets the background and
                                        // cached/available tiles render even on a weak connection.
                                        view.foreground = null
                                        styleGeneration += 1
                                        mapLoadStatus =
                                            if (offlineFallback) {
                                                MapLoadStatus.OFFLINE_FALLBACK
                                            } else {
                                                MapLoadStatus.ONLINE_STYLE_READY
                                            }
                                        mapNotice =
                                            if (offlineFallback) {
                                                "Offline-Kartenmodus"
                                            } else {
                                                null
                                            }
                                        mapError = null
                                        mapLoading = false
                                        Log.d(
                                            FULL_BLEED_MAP_TAG,
                                            if (offlineFallback) {
                                                "Offline fallback style loaded"
                                            } else {
                                                "Online nautical style loaded"
                                            },
                                        )
                                    } catch (error: Throwable) {
                                        Log.e(FULL_BLEED_MAP_TAG, "Style setup failed", error)
                                        if (!offlineFallback) {
                                            state.loadOfflineStyle?.invoke()
                                        } else {
                                            mapLoadStatus = MapLoadStatus.FAILED
                                            mapError =
                                                error.localizedMessage
                                                    ?: "Kartenstil konnte nicht geladen werden."
                                            mapLoading = false
                                        }
                                    }
                                }
                            }.onFailure { error ->
                                Log.e(FULL_BLEED_MAP_TAG, "setStyle failed", error)
                                if (!offlineFallback) {
                                    state.loadOfflineStyle?.invoke()
                                } else {
                                    mapLoadStatus = MapLoadStatus.FAILED
                                    mapError =
                                        error.localizedMessage
                                            ?: "Kartenstil konnte nicht geladen werden."
                                    mapLoading = false
                                }
                            }
                        }

                        state.loadOnlineStyle = {
                            mapNotice = null
                            mapError = null
                            mapLoading = true
                            mapLoadStatus = MapLoadStatus.STARTING
                            loadStyle(FullBleedMapStyles.online, offlineFallback = false)
                        }
                        state.loadOfflineStyle = {
                            loadStyle(FullBleedMapStyles.offline, offlineFallback = true)
                        }
                        state.loadOnlineStyle?.invoke()
                    }
                }
            },
            onRelease = { view ->
                state.destroyed = true
                runCatching {
                    state.fills?.deleteAll()
                    state.symbols?.deleteAll()
                    state.lines?.deleteAll()
                    state.circles?.deleteAll()
                    state.fills?.onDestroy()
                    state.symbols?.onDestroy()
                    state.lines?.onDestroy()
                    state.circles?.onDestroy()
                    state.stop(view)
                    view.onDestroy()
                }
                state.map = null
                state.fills = null
                state.symbols = null
                state.lines = null
                state.circles = null
                state.loadOnlineStyle = null
                state.loadOfflineStyle = null
            },
        )

        if (mapLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFF0EA5E9),
            )
        }
        if (mapLoadStatus == MapLoadStatus.ONLINE_STYLE_READY) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("maplibre_style_loaded"),
            )
        }
        if (
            mapLoadStatus == MapLoadStatus.ONLINE_STYLE_READY ||
            mapLoadStatus == MapLoadStatus.OFFLINE_FALLBACK
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("maplibre_surface_ready"),
            )
        }
        if (renderableWarnings.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("map_warning_overlays"),
            )
        }
        if (focusedWarningAction != null) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .testTag("map_warning_focus"),
            )
        }
        mapNotice?.let { notice ->
            val offlineFallback = mapLoadStatus == MapLoadStatus.OFFLINE_FALLBACK
            Text(
                text = if (offlineFallback) "$notice · Erneut versuchen" else notice,
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = attributionTopMargin + 34.dp)
                        .background(Color(0xD908263A), RoundedCornerShape(18.dp))
                        .then(
                            if (offlineFallback) {
                                Modifier.clickable { state.loadOnlineStyle?.invoke() }
                            } else {
                                Modifier
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag(
                            if (offlineFallback) {
                                "maplibre_offline_fallback"
                            } else {
                                "maplibre_tile_warning"
                            },
                        ),
            )
        }
        mapError?.let {
            Text(
                text = "Karte nicht verfügbar: $it",
                color = Color.White,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .background(Color(0xD908263A), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("maplibre_error_fallback"),
            )
        }
        selectedWarning?.let { warning ->
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = 16.dp,
                            end = 16.dp,
                            bottom = warningSummaryBottomPadding,
                        )
                        .fillMaxWidth()
                        .widthIn(max = 520.dp)
                        .testTag("map_warning_summary"),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(start = 18.dp, top = 14.dp, end = 12.dp, bottom = 8.dp)) {
                    Text(
                        text = "WARNMELDUNG",
                        color = mapWarningRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Text(
                        text = warning.title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (warning.summary.isNotEmpty()) {
                        Text(
                            text = warning.summary,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = { selectedWarningId = null },
                            modifier = Modifier.testTag("map_warning_summary_close"),
                        ) {
                            Text("Schlie\u00dfen")
                        }
                        TextButton(
                            onClick = {
                                mapWarningDetailsActionOrNull(
                                    currentWarningOverlays,
                                    selectedWarningId,
                                )?.let { currentOnOpenWarning(it.warningId) }
                            },
                            modifier = Modifier.testTag("map_warning_open_details"),
                        ) {
                            Text("Zur Meldung", color = mapWarningRed)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(mapNotice, mapLoadStatus) {
        val notice = mapNotice ?: return@LaunchedEffect
        if (mapLoadStatus != MapLoadStatus.ONLINE_STYLE_READY) return@LaunchedEffect
        delay(4_000)
        if (mapNotice == notice && mapLoadStatus == MapLoadStatus.ONLINE_STYLE_READY) {
            mapNotice = null
        }
    }

    LaunchedEffect(focusedWarningId, renderableWarnings) {
        if (focusedWarningId != null) {
            selectedWarningId =
                mapWarningFocusActionOrNull(renderableWarnings, focusedWarningId)
                    ?.overlay
                    ?.id
        } else if (mapWarningFocusActionOrNull(renderableWarnings, selectedWarningId) == null) {
            selectedWarningId = null
        }
    }

    LaunchedEffect(focusedWarningId) {
        if (focusedWarningId == null) {
            warningFocusGate = warningFocusGate.reset()
        }
    }

    LaunchedEffect(mapLoading, mapLoadStatus) {
        if (!mapLoading || mapLoadStatus != MapLoadStatus.STARTING) return@LaunchedEffect
        delay(MAP_START_TIMEOUT_MILLIS)
        if (!mapLoading || state.destroyed) return@LaunchedEffect

        if (state.map != null && state.loadOfflineStyle != null) {
            Log.w(FULL_BLEED_MAP_TAG, "Online map start timed out; loading offline style")
            state.loadOfflineStyle?.invoke()
            delay(MAP_FALLBACK_TIMEOUT_MILLIS)
        }

        if (mapLoading && !state.destroyed) {
            Log.e(FULL_BLEED_MAP_TAG, "MapLibre initialization timed out")
            mapLoadStatus = MapLoadStatus.FAILED
            mapError = "Initialisierung hat zu lange gedauert."
            mapLoading = false
        }
    }

    LaunchedEffect(
        styleGeneration,
        route,
        routeColor,
        harbours,
        breadcrumbs,
        currentLocation,
        renderableWarnings,
    ) {
        if (styleGeneration == 0 || state.destroyed) return@LaunchedEffect
        val fills = state.fills ?: return@LaunchedEffect
        val circles = state.circles ?: return@LaunchedEffect
        val lines = state.lines ?: return@LaunchedEffect
        val symbols = state.symbols ?: return@LaunchedEffect

        runCatching {
            fills.deleteAll()
            circles.deleteAll()
            lines.deleteAll()
            symbols.deleteAll()

            if (route.size >= 2) {
                lines.create(
                    LineOptions()
                        .withLatLngs(route)
                        .withLineColor(routeColor.toMapLibreRgba())
                        .withLineWidth(6f)
                        .withLineOpacity(0.96f),
                )
            }

            if (breadcrumbs.size >= 2) {
                lines.create(
                    LineOptions()
                        .withLatLngs(breadcrumbs)
                        .withLineColor(Color(0xFFFF7A00).toMapLibreRgba())
                        .withLineWidth(4f)
                        .withLineOpacity(0.95f),
                )
            }

            harbours.forEach { harbour ->
                val markerColor =
                    when (harbour.role) {
                        MapHarbourRole.START -> Color(0xFF14B8A6)
                        MapHarbourRole.STOP -> Color(0xFF2563EB)
                        MapHarbourRole.DESTINATION -> Color(0xFFEA580C)
                    }
                circles.create(
                    CircleOptions()
                        .withLatLng(harbour.coordinate)
                        .withCircleRadius(8f)
                        .withCircleColor(markerColor.toMapLibreRgba())
                        .withCircleStrokeColor(Color.White.toMapLibreRgba())
                        .withCircleStrokeWidth(2f),
                )
                val prefix =
                    when (harbour.role) {
                        MapHarbourRole.START -> "S"
                        MapHarbourRole.DESTINATION -> "Z"
                        MapHarbourRole.STOP -> harbour.order?.toString().orEmpty()
                    }
                symbols.create(
                    SymbolOptions()
                        .withLatLng(harbour.coordinate)
                        .withTextField("$prefix  ${harbour.title}")
                        .withTextColor(Color.White.toMapLibreRgba())
                        .withTextHaloColor(markerColor.toMapLibreRgba())
                        .withTextHaloWidth(1.5f)
                        .withTextSize(12f)
                        .withTextOffset(arrayOf(0f, 1.45f)),
                ).apply {
                    data = JsonPrimitive("$HARBOUR_DATA_PREFIX${harbour.id}")
                }
            }

            renderableWarnings.forEach { warning ->
                val warningPoints = warning.coordinates.map(MapWarningCoordinate::toLatLng)
                val annotationData = JsonPrimitive("$WARNING_DATA_PREFIX${warning.id}")

                fun createWarningMarker(point: LatLng) {
                    circles.create(
                        CircleOptions()
                            .withLatLng(point)
                            .withCircleRadius(10f)
                            .withCircleColor(mapWarningRed.toMapLibreRgba())
                            .withCircleStrokeColor(Color.White.toMapLibreRgba())
                            .withCircleStrokeWidth(3f),
                    ).data = annotationData
                    symbols.create(
                        SymbolOptions()
                            .withLatLng(point)
                            .withTextField("!")
                            .withTextColor(Color.White.toMapLibreRgba())
                            .withTextHaloColor(mapWarningDarkRed.toMapLibreRgba())
                            .withTextHaloWidth(1.5f)
                            .withTextSize(15f),
                    ).data = annotationData
                }

                when (warning.geometryType) {
                    MapWarningGeometryType.POINT,
                    MapWarningGeometryType.MULTI_POINT,
                    -> warningPoints.forEach(::createWarningMarker)

                    MapWarningGeometryType.LINE -> {
                        lines.create(
                            LineOptions()
                                .withLatLngs(warningPoints)
                                .withLineColor(mapWarningRed.toMapLibreRgba())
                                .withLineWidth(5f)
                                .withLineOpacity(0.96f),
                        ).data = annotationData
                        createWarningMarker(warningPoints.first())
                    }

                    MapWarningGeometryType.AREA -> {
                        val ring = warningPoints.closedRing()
                        fills.create(
                            FillOptions()
                                .withLatLngs(listOf(ring))
                                .withFillColor(mapWarningRed.toMapLibreRgba())
                                .withFillOutlineColor(mapWarningDarkRed.toMapLibreRgba())
                                .withFillOpacity(0.24f),
                        ).data = annotationData
                        lines.create(
                            LineOptions()
                                .withLatLngs(ring)
                                .withLineColor(mapWarningDarkRed.toMapLibreRgba())
                                .withLineWidth(3f)
                                .withLineOpacity(0.98f),
                        ).data = annotationData
                        createWarningMarker(warningPoints.first())
                    }
                }
            }

            currentLocation?.let { location ->
                circles.create(
                    CircleOptions()
                        .withLatLng(location)
                        .withCircleRadius(9f)
                        .withCircleColor(Color(0xFF0EA5E9).toMapLibreRgba())
                        .withCircleStrokeColor(Color.White.toMapLibreRgba())
                        .withCircleStrokeWidth(3f),
                )
            }
        }.onFailure {
            Log.w(FULL_BLEED_MAP_TAG, "Overlay update failed", it)
        }
    }

    LaunchedEffect(route, styleGeneration, followLocation) {
        if (
            styleGeneration == 0 ||
            followLocation ||
            route.isEmpty() ||
            selectedWarning != null
        ) {
            return@LaunchedEffect
        }
        val map = state.map ?: return@LaunchedEffect
        runCatching {
            if (route.size == 1) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(route.first(), 12.0), 450)
            } else {
                val bounds =
                    LatLngBounds.Builder().also { builder ->
                        route.forEach(builder::include)
                    }.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 130), 700)
            }
        }
    }

    LaunchedEffect(focusedWarningAction, styleGeneration, followLocation) {
        val warning = focusedWarningAction?.overlay ?: return@LaunchedEffect
        if (styleGeneration == 0 || followLocation) return@LaunchedEffect
        if (!warningFocusGate.canProcess(warning.id)) return@LaunchedEffect
        val map = state.map ?: return@LaunchedEffect
        val points = warning.coordinates.map(MapWarningCoordinate::toLatLng)
        val distinctPoints = points.distinct()
        if (distinctPoints.isEmpty()) return@LaunchedEffect

        runCatching {
            if (distinctPoints.size == 1) {
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(distinctPoints.first(), 13.5),
                    550,
                )
            } else {
                val bounds =
                    LatLngBounds.Builder().also { builder ->
                        distinctPoints.forEach(builder::include)
                    }.build()
                map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 140), 700)
            }
        }.onSuccess {
            warningFocusGate = warningFocusGate.markProcessed(warning.id)
            currentOnWarningFocusConsumed(warning.id)
        }.onFailure {
            Log.w(FULL_BLEED_MAP_TAG, "Warning camera focus failed", it)
        }
    }

    LaunchedEffect(currentLocation, headingDegrees, followLocation, styleGeneration) {
        if (!followLocation || currentLocation == null || styleGeneration == 0) return@LaunchedEffect
        val map = state.map ?: return@LaunchedEffect
        val camera =
            CameraPosition.Builder()
                .target(currentLocation)
                .zoom(14.2)
                .bearing(headingDegrees ?: map.cameraPosition.bearing)
                .tilt(32.0)
                .build()
        runCatching { map.animateCamera(CameraUpdateFactory.newCameraPosition(camera), 420) }
    }

    DisposableEffect(lifecycleOwner, mapViewHolder.value) {
        val view = mapViewHolder.value
        if (view == null) {
            onDispose {}
        } else {
            state.synchronizeLifecycle(view, lifecycleOwner.lifecycle)

            val observer =
                LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> state.start(view)
                        Lifecycle.Event.ON_RESUME -> state.resume(view)
                        Lifecycle.Event.ON_PAUSE -> state.pause(view)
                        Lifecycle.Event.ON_STOP -> state.stop(view)
                        else -> Unit
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)

            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                // LocalLifecycleOwner may legitimately change while the
                // AndroidView itself stays mounted (for example while the
                // NavBackStackEntry moves from CREATED to RESUMED). Only
                // detach from that owner here. AndroidView.onRelease owns
                // the one-time MapView destruction.
                state.stop(view)
            }
        }
    }
}

private fun warningIdFromMapAnnotation(raw: String): String? =
    raw
        .takeIf { it.startsWith(WARNING_DATA_PREFIX) }
        ?.removePrefix(WARNING_DATA_PREFIX)
        ?.takeIf(String::isNotBlank)

private fun MapWarningCoordinate.toLatLng(): LatLng = LatLng(latitude, longitude)

/** MapLibre polygons require a closed ring; this repeats an official vertex without inventing one. */
private fun List<LatLng>.closedRing(): List<LatLng> {
    val first = firstOrNull() ?: return emptyList()
    val last = lastOrNull() ?: return emptyList()
    return if (first.latitude == last.latitude && first.longitude == last.longitude) {
        this
    } else {
        this + first
    }
}

private fun Color.toMapLibreRgba(): String =
    ColorUtils.colorToRgbaString(
        AndroidColor.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt(),
        ),
    )

internal object FullBleedMapStyles {
    val online =
        """
        {
          "version": 8,
          "glyphs": "https://demotiles.maplibre.org/font/{fontstack}/{range}.pbf",
          "sources": {
            "osm": {
              "type": "raster",
              "tiles": ["https://tile.openstreetmap.de/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "attribution": "© OpenStreetMap contributors"
            },
            "openseamap": {
              "type": "raster",
              "tiles": ["https://tiles.openseamap.org/seamark/{z}/{x}/{y}.png"],
              "tileSize": 256,
              "attribution": "© OpenSeaMap contributors"
            }
          },
          "layers": [
            {"id": "sea-background", "type": "background", "paint": {"background-color": "#a8c6cd"}},
            {"id": "osm-layer", "type": "raster", "source": "osm"},
            {"id": "openseamap-layer", "type": "raster", "source": "openseamap"}
          ]
        }
        """.trimIndent()

    val offline =
        """
        {
          "version": 8,
          "sources": {},
          "layers": [
            {"id": "offline-sea-background", "type": "background", "paint": {"background-color": "#a8c6cd"}}
          ]
        }
        """.trimIndent()
}
