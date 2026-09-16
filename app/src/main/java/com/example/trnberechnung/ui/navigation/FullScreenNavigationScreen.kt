@file:Suppress("FunctionName", "ktlint:standard:function-naming")

package com.example.trnberechnung.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.trnberechnung.navigation.ActiveVoyageManager
import com.example.trnberechnung.navigation.ActiveVoyageSession
import com.example.trnberechnung.navigation.ActiveVoyageState
import com.example.trnberechnung.navigation.CompletedVoyage
import com.example.trnberechnung.navigation.GeoMath
import com.example.trnberechnung.navigation.HeadingProvider
import com.example.trnberechnung.navigation.HeadingSample
import com.example.trnberechnung.navigation.LocationFix
import com.example.trnberechnung.navigation.LocationProvider
import com.example.trnberechnung.ui.components.TideNodeCyan
import com.example.trnberechnung.ui.components.TideNodeDanger
import com.example.trnberechnung.ui.components.TideNodeSuccess
import com.example.trnberechnung.ui.components.TideNodeWarning
import com.example.trnberechnung.ui.components.tideNodeDarkGlass
import com.example.trnberechnung.ui.map.FullBleedMap
import com.example.trnberechnung.ui.map.MapHarbourMarker
import com.example.trnberechnung.ui.map.MapHarbourRole
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Edge-to-edge live navigation over MapLibre.
 *
 * The host starts/stops [com.example.trnberechnung.navigation.VoyageForegroundService].
 * This screen owns the confirmed logbook finish through [ActiveVoyageManager].
 */
@Composable
fun FullScreenNavigationScreen(
    activeVoyageManager: ActiveVoyageManager,
    locationProvider: LocationProvider,
    headingProvider: HeadingProvider,
    onMinimize: () -> Unit,
    onVoyageFinished: (CompletedVoyage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voyageState by activeVoyageManager.state.collectAsStateWithLifecycle()
    val latestFix by locationProvider.latestFix.collectAsStateWithLifecycle()
    val safeHeadingFlow =
        remember(headingProvider) {
            headingProvider.headings
                .map<HeadingSample, HeadingSample?> { it }
                .catch { emit(null) }
        }
    val heading by safeHeadingFlow.collectAsStateWithLifecycle(initialValue = null)
    val session = (voyageState as? ActiveVoyageState.Active)?.session

    if (session == null) {
        LaunchedEffect(Unit) {
            onMinimize()
        }
        Box(modifier.fillMaxSize())
        return
    }

    ActiveNavigationContent(
        session = session,
        latestFix = latestFix,
        heading = heading,
        activeVoyageManager = activeVoyageManager,
        onMinimize = onMinimize,
        onVoyageFinished = onVoyageFinished,
        modifier = modifier,
    )
}

@Composable
private fun ActiveNavigationContent(
    session: ActiveVoyageSession,
    latestFix: LocationFix?,
    heading: HeadingSample?,
    activeVoyageManager: ActiveVoyageManager,
    onMinimize: () -> Unit,
    onVoyageFinished: (CompletedVoyage) -> Unit,
    modifier: Modifier,
) {
    var nowEpochMillis by remember(session.id) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    var followHeading by remember(session.id) { mutableStateOf(true) }
    var showFinishConfirmation by remember { mutableStateOf(false) }
    var isFinishing by remember { mutableStateOf(false) }
    var isMinimizing by remember(session.id) { mutableStateOf(false) }
    var finishError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun minimizeOnce() {
        if (isMinimizing) return
        isMinimizing = true
        onMinimize()
    }

    BackHandler(enabled = !showFinishConfirmation && !isMinimizing) {
        minimizeOnce()
    }

    LaunchedEffect(session.id) {
        while (true) {
            nowEpochMillis = System.currentTimeMillis()
            delay(1_000L)
        }
    }

    val display =
        remember(session, latestFix, nowEpochMillis) {
            navigationDisplayValues(session, latestFix, nowEpochMillis)
        }
    val validFix =
        latestFix?.takeIf {
            it.horizontalAccuracyMeters.isFinite() &&
                it.horizontalAccuracyMeters in
                0.0..ActiveVoyageManager.MAX_HORIZONTAL_ACCURACY_METERS
        }
    val route =
        remember(session.route) {
            session.route.waypoints.map {
                LatLng(it.coordinate.latitude, it.coordinate.longitude)
            }
        }
    val breadcrumbs =
        remember(session.breadcrumbs) {
            session.breadcrumbs.map {
                LatLng(it.coordinate.latitude, it.coordinate.longitude)
            }
        }
    val harbourMarkers =
        remember(session.route, session.userWaypointIds) {
            session.toMapHarbourMarkers()
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("FullScreenNavigation"),
    ) {
        FullBleedMap(
            route = route,
            routeColor = TideNodeCyan,
            harbours = harbourMarkers,
            breadcrumbs = breadcrumbs,
            currentLocation =
                validFix?.let {
                    LatLng(it.coordinate.latitude, it.coordinate.longitude)
                },
            // Keep the navigation chart north-up. A non-zero MapLibre camera bearing crashes the
            // native raster renderer on affected Android GL drivers; heading calculations and the
            // navigation dashboard remain unchanged.
            headingDegrees = null,
            followLocation = followHeading && validFix != null,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.34f),
                            0.26f to Color.Transparent,
                            0.62f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.45f),
                        ),
                    ),
        )

        NavigationTopBar(
            routeTitle = session.route.title,
            elapsed = display.elapsed,
            onMinimize = ::minimizeOnce,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .zIndex(NAVIGATION_CONTROL_Z_INDEX),
        )

        HeadingFollowButton(
            enabled = validFix != null,
            following = followHeading,
            onClick = { followHeading = !followHeading },
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 14.dp)
                    .testTag("NavigationFollowHeading"),
        )

        NavigationDashboard(
            display = display,
            hasPreciseFix = validFix != null,
            isFinishing = isFinishing,
            finishError = finishError,
            onFinish = { showFinishConfirmation = true },
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
        )
    }

    if (showFinishConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!isFinishing) showFinishConfirmation = false
            },
            title = { Text("Aktive Fahrt beenden?") },
            text = {
                Text(
                    "Die aufgezeichnete Strecke wird ins Logbuch übernommen und das GPS-Tracking gestoppt.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !isFinishing,
                    onClick = {
                        showFinishConfirmation = false
                        isFinishing = true
                        finishError = null
                        scope.launch {
                            runCatching { activeVoyageManager.finishVoyage() }
                                .onSuccess { completed ->
                                    if (completed != null) {
                                        onVoyageFinished(completed)
                                    } else {
                                        minimizeOnce()
                                    }
                                }.onFailure { error ->
                                    finishError =
                                        error.localizedMessage
                                            ?: "Die Fahrt konnte nicht gespeichert werden."
                                }
                            isFinishing = false
                        }
                    },
                ) {
                    Text("Fahrt beenden", color = TideNodeDanger)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isFinishing,
                    onClick = { showFinishConfirmation = false },
                ) {
                    Text("Weiter aufzeichnen")
                }
            },
        )
    }
}

@Composable
private fun NavigationTopBar(
    routeTitle: String,
    elapsed: String,
    onMinimize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DarkCircularButton(
            icon = Icons.Default.KeyboardArrowDown,
            contentDescription = "Vollbild minimieren",
            onClick = onMinimize,
            modifier = Modifier.testTag("NavigationMinimize"),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .tideNodeDarkGlass(cornerRadius = 16.dp, elevation = 8.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = "NAVIGATION",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = routeTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = elapsed,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier =
                Modifier
                    .tideNodeDarkGlass(cornerRadius = 16.dp, elevation = 8.dp)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun NavigationDashboard(
    display: NavigationDisplayValues,
    hasPreciseFix: Boolean,
    isFinishing: Boolean,
    finishError: String?,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .tideNodeDarkGlass(cornerRadius = 30.dp, elevation = 16.dp)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(width = 44.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.32f)),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(
                            if (display.hasArrived) {
                                TideNodeSuccess.copy(alpha = 0.24f)
                            } else {
                                Color(0xFF14B8A6).copy(alpha = 0.22f)
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector =
                        if (display.hasArrived) {
                            Icons.Default.LocationOn
                        } else {
                            Icons.Default.Navigation
                        },
                    contentDescription = null,
                    tint = if (display.hasArrived) TideNodeSuccess else Color(0xFF2DD4BF),
                    modifier = Modifier.size(27.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = display.waypointTitle,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = display.instruction,
                    color = Color.White.copy(alpha = 0.70f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (display.isOffCourse) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TideNodeWarning)
                        .padding(10.dp)
                        .testTag("NavigationOffCourse"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Warning, null, tint = Color.White)
                Text(
                    text = "Off Course – ${display.xte} abseits der Route",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                )
            }
        } else if (!hasPreciseFix) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF334155).copy(alpha = 0.78f))
                        .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.MyLocation, null, tint = Color.White)
                Text(
                    text = "Warte auf ein präzises GPS-Signal (≤ 30 m)",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavigationMetricTile(
                title = "SOG",
                value = display.sog,
                icon = Icons.Default.Speed,
                modifier = Modifier.weight(1f),
            )
            NavigationMetricTile(
                title = "DTW",
                value = display.dtw,
                icon = Icons.Default.Straighten,
                modifier = Modifier.weight(1f),
            )
            NavigationMetricTile(
                title = "ETA",
                value = display.eta,
                icon = Icons.Default.AccessTime,
                modifier = Modifier.weight(1f),
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavigationCompactMetric(
                title = "COG",
                value = display.cog,
                modifier = Modifier.weight(1f),
            )
            NavigationCompactMetric(
                title = "XTE",
                value = display.xte,
                modifier = Modifier.weight(1f),
            )
            NavigationCompactMetric(
                title = "Gefahren",
                value = display.distance,
                modifier = Modifier.weight(1f),
            )
        }

        finishError?.let {
            Text(
                text = it,
                color = Color(0xFFFCA5A5),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = onFinish,
            enabled = !isFinishing,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = TideNodeDanger,
                    contentColor = Color.White,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("NavigationFinish"),
        ) {
            if (isFinishing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(Icons.Default.StopCircle, null)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = if (isFinishing) "Fahrt wird gespeichert …" else "Fahrt beenden",
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun NavigationMetricTile(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(17.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .padding(11.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF93C5FD),
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavigationCompactMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = title,
            color = Color.White.copy(alpha = 0.54f),
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HeadingFollowButton(
    enabled: Boolean,
    following: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .tideNodeDarkGlass(cornerRadius = 24.dp, elevation = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                imageVector = if (following) Icons.Default.Navigation else Icons.Default.Explore,
                contentDescription =
                    if (following) {
                        "Heading-Follow ausschalten"
                    } else {
                        "Heading-Follow einschalten"
                    },
                tint =
                    when {
                        !enabled -> Color.White.copy(alpha = 0.28f)
                        following -> TideNodeCyan
                        else -> Color.White
                    },
            )
        }
    }
}

@Composable
private fun DarkCircularButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(MINIMUM_TOUCH_TARGET)
                .tideNodeDarkGlass(cornerRadius = 24.dp, elevation = 8.dp)
                .clickable(
                    onClick = onClick,
                    onClickLabel = contentDescription,
                    role = Role.Button,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = Color.White)
    }
}

private val MINIMUM_TOUCH_TARGET = 48.dp
private const val NAVIGATION_CONTROL_Z_INDEX = 3f



internal data class NavigationDisplayValues(
    val waypointTitle: String,
    val instruction: String,
    val sog: String,
    val cog: String,
    val dtw: String,
    val eta: String,
    val xte: String,
    val distance: String,
    val elapsed: String,
    val isOffCourse: Boolean,
    val hasArrived: Boolean,
)

internal fun navigationDisplayValues(
    session: ActiveVoyageSession,
    latestFix: LocationFix?,
    nowEpochMillis: Long,
): NavigationDisplayValues {
    val navigation = session.latestNavigation
    val preciseFix =
        latestFix?.takeIf {
            it.horizontalAccuracyMeters.isFinite() &&
                it.horizontalAccuracyMeters in
                0.0..ActiveVoyageManager.MAX_HORIZONTAL_ACCURACY_METERS
        }
    val fallbackBreadcrumb = session.breadcrumbs.lastOrNull()
    val sog = preciseFix?.speedKnots ?: fallbackBreadcrumb?.speedKnots
    val cog = preciseFix?.courseDegrees ?: fallbackBreadcrumb?.courseDegrees
    val hasArrived = navigation?.hasArrived == true
    val waypointTitle =
        when {
            hasArrived -> "Ziel erreicht"
            !navigation?.activeWaypointName.isNullOrBlank() -> navigation?.activeWaypointName.orEmpty()
            else -> "Route folgen"
        }
    val instruction =
        when {
            hasArrived -> "Du befindest dich im Zielradius von 50 m"
            navigation != null ->
                "${formatNm(navigation.distanceToWaypointNm)} bis zum nächsten Wegpunkt · " +
                    "Peilung ${formatCourse(navigation.bearingToWaypointDegrees)}"
            else -> "Geplante Route ist auf der Karte eingeblendet"
        }

    return NavigationDisplayValues(
        waypointTitle = waypointTitle,
        instruction = instruction,
        sog = sog?.let { formatDecimal(it, 1, "kn") } ?: "–",
        cog = cog?.let(::formatCourse) ?: "–",
        dtw = navigation?.distanceToWaypointNm?.let(::formatNm) ?: "–",
        eta =
            navigation?.dynamicEtaEpochMillis?.let {
                ETA_FORMATTER.format(Instant.ofEpochMilli(it))
            } ?: "–",
        xte =
            navigation?.crossTrackErrorMeters?.let {
                "${it.coerceAtLeast(0.0).toInt()} m"
            } ?: "–",
        distance = formatNm(session.totalDistanceNm),
        elapsed = formatElapsed(session.elapsedSeconds(nowEpochMillis)),
        isOffCourse = navigation?.isOffCourse == true,
        hasArrived = hasArrived,
    )
}

private fun ActiveVoyageSession.toMapHarbourMarkers(): List<MapHarbourMarker> {
    val userWaypoints =
        route.waypoints.filter { it.id in userWaypointIds }
    return userWaypoints.mapIndexed { index, waypoint ->
        MapHarbourMarker(
            id = waypoint.id,
            title = waypoint.name,
            subtitle = "",
            coordinate = LatLng(waypoint.coordinate.latitude, waypoint.coordinate.longitude),
            role =
                when (index) {
                    0 -> MapHarbourRole.START
                    userWaypoints.lastIndex -> MapHarbourRole.DESTINATION
                    else -> MapHarbourRole.STOP
                },
            order = index.takeIf { it in 1 until userWaypoints.lastIndex },
        )
    }
}

private fun formatNm(value: Double): String = formatDecimal(value, 2, "nm")

private fun formatDecimal(
    value: Double,
    decimals: Int,
    unit: String,
): String = String.format(Locale.GERMANY, "%.${decimals}f %s", value, unit)

private fun formatCourse(value: Double): String =
    String.format(
        Locale.GERMANY,
        "%03d°",
        GeoMath.normalizeDegrees(value).roundToInt() % 360,
    )

private fun formatElapsed(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3_600L
    val minutes = (safe % 3_600L) / 60L
    val remainder = safe % 60L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder)
}

private val ETA_FORMATTER =
    DateTimeFormatter
        .ofPattern("HH:mm", Locale.GERMANY)
        .withZone(ZoneId.of("Europe/Berlin"))
