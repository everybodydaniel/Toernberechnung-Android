package com.example.trnberechnung.ui.map

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.trnberechnung.mapplanning.HarbourId
import com.example.trnberechnung.mapplanning.RoutePlanningViewModel
import com.example.trnberechnung.mapplanning.RouteStatus
import com.example.trnberechnung.model.BoatProfileRepository
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.nauti.NautiAction
import com.example.trnberechnung.nauti.NautiStationMatcher
import com.example.trnberechnung.navigation.ActiveVoyageManager
import com.example.trnberechnung.navigation.FusedLocationProvider
import com.example.trnberechnung.navigation.LocationAccess
import com.example.trnberechnung.navigation.VoyageServiceController
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.TideNodeInk
import com.example.trnberechnung.ui.components.tideNodeGlass
import com.example.trnberechnung.ui.nauti.NautiDrawer
import com.example.trnberechnung.viewmodel.NautiPanelMode
import com.example.trnberechnung.viewmodel.NautiViewModel
import com.example.trnberechnung.viewmodel.TideViewModel
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng

@Composable
fun MapTabScreen(
    tideViewModel: TideViewModel,
    planningViewModel: RoutePlanningViewModel,
    nautiViewModel: NautiViewModel,
    activeVoyageManager: ActiveVoyageManager,
    locationProvider: FusedLocationProvider,
    topOverlayClearance: Dp,
    bottomOverlayClearance: Dp,
    onOpenWeather: () -> Unit,
    onOpenNavigation: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val routeState by planningViewModel.uiState.collectAsState()
    val nautiState by nautiViewModel.uiState.collectAsState()
    val stations by tideViewModel.allStations.collectAsState()
    val boatProfile = remember(context) { BoatProfileRepository(context) }

    var showPlanner by remember { mutableStateOf(false) }
    var showRestrictedConfirmation by remember { mutableStateOf(false) }
    var permissionStartPending by remember { mutableStateOf(false) }
    // Set when the restricted-route dialog was triggered by a Nauti chat instruction rather than by
    // the skipper tapping start, so the dialog can say where the suggestion came from.
    var restrictedFromNauti by remember { mutableStateOf(false) }
    val nautiVoyageLauncher =
        remember(planningViewModel) { NautiVoyageLauncher(planningViewModel) }

    DisposableEffect(nautiViewModel) {
        nautiViewModel.showCompact()
        onDispose(nautiViewModel::showCompact)
    }

    fun startNavigationAfterValidation() {
        val route = routeState.toNavigationRouteOrNull()
        if (route == null) {
            Toast.makeText(context, "Für die Navigation fehlt eine vollständige Route.", Toast.LENGTH_LONG).show()
            return
        }
        if (locationProvider.access() != LocationAccess.PRECISE) {
            permissionStartPending = true
            return
        }
        val visibleActivity = activity
        if (visibleActivity == null) {
            Toast.makeText(context, "Navigation kann gerade nicht gestartet werden.", Toast.LENGTH_LONG).show()
            return
        }
        scope.launch {
            runCatching {
                activeVoyageManager.startVoyage(
                    route = route,
                    plannedSpeedKnots = routeState.boatSettings.speedKnots,
                )
            }.onSuccess { started ->
                if (started) {
                    VoyageServiceController.startFromVisibleActivity(visibleActivity)
                }
                onOpenNavigation()
            }.onFailure {
                Toast.makeText(
                    context,
                    it.message ?: "Die Navigation konnte nicht gestartet werden.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    fun requestNavigationStart() {
        when (routeState.routeStatus) {
            RouteStatus.NICHT_BEFAHRBAR ->
                Toast.makeText(
                    context,
                    "Navigation ist für eine nicht befahrbare Route gesperrt.",
                    Toast.LENGTH_LONG,
                ).show()
            RouteStatus.UNVOLLSTAENDIG ->
                Toast.makeText(
                    context,
                    "Navigation ist gesperrt, solange Sicherheitsdaten fehlen.",
                    Toast.LENGTH_LONG,
                ).show()
            RouteStatus.EINGESCHRAENKT -> showRestrictedConfirmation = true
            RouteStatus.BEFAHRBAR -> startNavigationAfterValidation()
        }
    }
    val currentRouteState by rememberUpdatedState(routeState)
    val currentNavigationStarter by
        rememberUpdatedState<(Unit) -> Unit>(
            newValue = { requestNavigationStart() },
        )

    val locationPermissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { result ->
            val precise =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                    ) == PackageManager.PERMISSION_GRANTED
            val shouldStart = permissionStartPending
            permissionStartPending = false
            if (precise && shouldStart) {
                startNavigationAfterValidation()
            } else if (shouldStart) {
                Toast.makeText(
                    context,
                    "Für GPS-Navigation ist der genaue Standort erforderlich. Planung bleibt verfügbar.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    LaunchedEffect(permissionStartPending) {
        if (permissionStartPending) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                ),
            )
        }
    }

    LaunchedEffect(Unit) {
        planningViewModel.updateBoatSettings(
            draftMeters = boatProfile.draft.toDouble(),
            safetyMarginMeters = boatProfile.safetyMargin.toDouble(),
            speedKnots = boatProfile.speed.toDouble(),
            waterLevelCorrectionMeters = boatProfile.waterLevelCorrection.toDouble(),
        )
    }

    LaunchedEffect(stations) {
        if (stations.isNotEmpty() && routeState.hasCompleteRouteInput) {
            planningViewModel.refresh()
        }
    }

    LaunchedEffect(nautiViewModel) {
        nautiViewModel.actions.collect { action ->
            when (action) {
                NautiAction.OpenTripPlanner -> showPlanner = true
                is NautiAction.PlanTrip -> {
                    val start = HarbourId.fromRawValue(action.startHarbourId)
                    val destination = HarbourId.fromRawValue(action.destinationHarbourId)
                    val stops = action.intermediateHarbourIds.mapNotNull(HarbourId::fromRawValue)
                    if (start == null || destination == null || start == destination) {
                        Toast.makeText(context, "Nauti hat ungültige Hafenangaben geliefert.", Toast.LENGTH_LONG).show()
                    } else {
                        planningViewModel.clearRoute()
                        planningViewModel.selectStart(start)
                        planningViewModel.selectDestination(destination)
                        planningViewModel.addIntermediateStops(stops)
                        action.departure?.let(planningViewModel::updateDeparture)
                        showPlanner = true
                    }
                }
                NautiAction.StartNavigation -> currentNavigationStarter(Unit)
                is NautiAction.StartVoyage -> {
                    // Plan first, then let the deterministic route status decide. The skipper has
                    // already confirmed the action in the chat bubble; these are the remaining gates.
                    val preflight = nautiVoyageLauncher.planAndPreflight(action)
                    // Always reveal what was actually computed, whatever the outcome.
                    showPlanner = true
                    when (preflight.outcome) {
                        VoyagePreflight.READY -> {
                            // Reuses the existing permission -> startVoyage -> foreground service
                            // chain; no second start implementation.
                            permissionStartPending = true
                        }
                        VoyagePreflight.RESTRICTED -> {
                            restrictedFromNauti = true
                            showRestrictedConfirmation = true
                        }
                        else ->
                            Toast.makeText(
                                context,
                                preflight.message ?: "Die Fahrt konnte nicht gestartet werden.",
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
                NautiAction.ShowPassageWindow -> {
                    if (currentRouteState.hasCompleteRouteInput) {
                        planningViewModel.refreshPassageWindow()
                    } else {
                        showPlanner = true
                    }
                }
                // Data questions are answered by a widget inside the chat bubble, so these only
                // keep the Revier tab pointed at the same harbour - they never navigate. Jumping
                // tabs mid-conversation is exactly what the widget replaced.
                is NautiAction.ShowWeather ->
                    selectNautiStation(action.harbourId, stations, tideViewModel)
                is NautiAction.ShowTides ->
                    selectNautiStation(action.harbourId, stations, tideViewModel)
                is NautiAction.ShowBshWaterLevel ->
                    selectNautiStation(action.harbourId, stations, tideViewModel)
            }
        }
    }

    Box(Modifier.fillMaxSize().testTag("full_bleed_map_tab")) {
        FullBleedMap(
            route = routeState.routeGeometry.map { LatLng(it.latitude, it.longitude) },
            routeColor = routeState.mapRouteColor(),
            harbours = routeState.mapHarbourMarkers(),
            modifier = Modifier.fillMaxSize(),
            // Only harbours that are already part of the plan carry a marker, so a tap can no
            // longer be a "pick this as start/destination" gesture - it opens the planner instead.
            onHarbourClick = { showPlanner = true },
        )

        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val panelWidthModifier = if (isLandscape) Modifier.fillMaxWidth(0.48f).widthIn(max = 440.dp) else Modifier

        RoutePlanningPill(
            title = if (routeState.hasCompleteRouteInput) routeState.routeTitle else "Törn planen",
            subtitle =
                if (routeState.hasCompleteRouteInput) {
                    routeState.departure.format(PILL_DATE_FORMAT)
                } else {
                    "Start, Ziel und Abfahrt auswählen"
                },
            isLoading = routeState.isCalculating,
            onClick = { showPlanner = true },
            modifier =
                Modifier
                    .align(if (isLandscape) Alignment.TopStart else Alignment.TopCenter)
                    .then(panelWidthModifier)
                    .padding(
                        start = 16.dp,
                        end = if (isLandscape) 4.dp else 16.dp,
                        top = topOverlayClearance + 8.dp,
                    ),
        )

        val openRevierForHarbour: (String?) -> Unit = { harbourId ->
            selectNautiStation(harbourId, stations, tideViewModel)
            onOpenWeather()
        }

        if (nautiState.mode != NautiPanelMode.COMPACT) {
            NautiDrawer(
                viewModel = nautiViewModel,
                stations = stations,
                onOpenRevier = openRevierForHarbour,
                modifier =
                    Modifier
                        .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
                        .then(panelWidthModifier)
                        .padding(
                            start = 14.dp,
                            end = if (isLandscape) 4.dp else 14.dp,
                            bottom = bottomOverlayClearance + 4.dp,
                        ),
            )
        } else if (routeState.hasCompleteRouteInput || routeState.routeMetrics != null) {
            RouteResultDashboard(
                state = routeState,
                onOpenNauti = nautiViewModel::showChat,
                onRefreshPassageWindow = planningViewModel::refreshPassageWindow,
                onStartNavigation = ::requestNavigationStart,
                onSave = {
                    val metrics = routeState.routeMetrics
                    val distStr = metrics?.distanceNm?.let { String.format(Locale.GERMANY, "%.1f nm", it) } ?: "–"
                    val durStr = metrics?.travelTime?.toMinutes()?.let { durationLabel(it) } ?: "–"
                    val wtStr = metrics?.worstUnderKeelClearanceMeters?.let { String.format(Locale.GERMANY, "%.2f m", it) } ?: "–"
                    val erftStr = String.format(Locale.GERMANY, "%.2f m", routeState.boatSettings.draftMeters + routeState.boatSettings.safetyMarginMeters)
                    tideViewModel.saveLog(
                        LogbookEntry(
                            date = routeState.departure.format(LOGBOOK_DATE_FORMAT),
                            routeDesc = routeState.routeTitle,
                            distance = distStr,
                            duration = durStr,
                            status = "planned",
                            details =
                                "abfahrt:${routeState.departure.format(PILL_DATE_FORMAT)}|" +
                                    "ukc:$wtStr|" +
                                    "erft:$erftStr|" +
                                    "bem:Geplant mit Status ${routeState.routeStatus.name}",
                        ),
                    )
                    Toast.makeText(context, "Törn im Logbuch gespeichert", Toast.LENGTH_SHORT).show()
                },
                modifier =
                    Modifier
                        .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
                        .then(panelWidthModifier)
                        .padding(
                            start = 14.dp,
                            end = if (isLandscape) 4.dp else 14.dp,
                            bottom = bottomOverlayClearance + 4.dp,
                        ),
            )
        } else {
            NautiDrawer(
                viewModel = nautiViewModel,
                stations = stations,
                onOpenRevier = openRevierForHarbour,
                modifier =
                    Modifier
                        .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
                        .then(panelWidthModifier)
                        .padding(
                            start = 16.dp,
                            end = if (isLandscape) 4.dp else 16.dp,
                            bottom = bottomOverlayClearance + 10.dp,
                        ),
            )
        }
    }

    if (showPlanner) {
        RoutePlannerSheet(
            viewModel = planningViewModel,
            onDismiss = { showPlanner = false },
        )
    }

    if (showRestrictedConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showRestrictedConfirmation = false
                restrictedFromNauti = false
            },
            title = { Text("Route mit Einschränkungen") },
            text = {
                Text(
                    if (restrictedFromNauti) {
                        "Nauti hat diesen Törn vorbereitet, er ist aber nur mit Einschränkungen " +
                            "befahrbar. Prüfe Wetter, Tide, WuK und amtliche Meldungen vor dem Start."
                    } else {
                        "Die Route ist nur mit Einschränkungen befahrbar. Prüfe Wetter, Tide, WuK und amtliche Meldungen vor dem Start."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRestrictedConfirmation = false
                        restrictedFromNauti = false
                        startNavigationAfterValidation()
                    },
                ) {
                    Text("Verstanden, starten")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showRestrictedConfirmation = false
                        restrictedFromNauti = false
                    },
                ) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

@Composable
private fun RoutePlanningPill(
    title: String,
    subtitle: String,
    isLoading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF62666C)
    val accentColor = if (isDark) Color(0xFF60A5FA) else TideNodeBlue

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(76.dp)
                .tideNodeGlass(cornerRadius = 28.dp, elevation = 12.dp, alpha = 0.85f)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp)
                .testTag("route_planning_pill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .tideNodeGlass(cornerRadius = 24.dp, elevation = 0.dp, alpha = 0.55f),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = accentColor,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.AltRoute, null, tint = accentColor)
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = titleColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != "Törn planen") {
                    Icon(
                        Icons.Default.Schedule,
                        null,
                        tint = subtitleColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.size(5.dp))
                }
                Text(
                    subtitle,
                    color = subtitleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text("›", color = accentColor, fontWeight = FontWeight.Bold, fontSize = 30.sp)
    }
}

private fun selectNautiStation(
    rawHarbourId: String?,
    stations: List<com.example.trnberechnung.model.TideStationData>,
    tideViewModel: TideViewModel,
) {
    NautiStationMatcher.nearestStation(rawHarbourId, stations)?.let(tideViewModel::selectStation)
}

private fun durationLabel(minutes: Long): String {
    val safe = minutes.coerceAtLeast(0)
    return "${safe / 60}h ${safe % 60}m"
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private val PILL_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy · HH:mm 'Uhr'")
private val LOGBOOK_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy")
