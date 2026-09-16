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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.example.trnberechnung.model.TideStationData
import com.example.trnberechnung.nauti.NautiAction
import com.example.trnberechnung.nauti.NautiStationMatcher
import com.example.trnberechnung.navigation.ActiveVoyageManager
import com.example.trnberechnung.navigation.ActiveVoyageState
import com.example.trnberechnung.navigation.FusedLocationProvider
import com.example.trnberechnung.navigation.LocationAccess
import com.example.trnberechnung.navigation.VoyageServiceController
import com.example.trnberechnung.ui.components.TideNodeBlue
import com.example.trnberechnung.ui.components.TideNodeInk
import com.example.trnberechnung.ui.components.tideNodeGlass
import com.example.trnberechnung.ui.currentAdaptiveLayout
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
    warningOverlays: List<MapWarningOverlay> = emptyList(),
    focusedWarningId: String? = null,
    onWarningFocusConsumed: (String) -> Unit = {},
    onOpenWarning: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val routeState by planningViewModel.uiState.collectAsState()
    val nautiState by nautiViewModel.uiState.collectAsState()
    val activeVoyageState by activeVoyageManager.state.collectAsState()
    val stations by tideViewModel.allStations.collectAsState()
    val boatProfile = remember(context) { BoatProfileRepository(context) }
    val adaptiveLayout = currentAdaptiveLayout()

    var showPlanner by rememberSaveable { mutableStateOf(false) }
    var showRestrictedConfirmation by remember { mutableStateOf(false) }
    var permissionStartPending by remember { mutableStateOf(false) }
    // Set when the restricted-route dialog was triggered by a Nauti chat instruction rather than by
    // the skipper tapping start, so the dialog can say where the suggestion came from.
    var restrictedFromNauti by remember { mutableStateOf(false) }
    val nautiVoyageLauncher =
        remember(planningViewModel) { NautiVoyageLauncher(planningViewModel) }

    fun openPlanner() {
        if (planningViewModel.uiState.value.hasCalculatedResult) {
            planningViewModel.editCalculatedPlan()
        }
        showPlanner = true
    }

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

    fun resumeActiveNavigation() {
        activity?.let(VoyageServiceController::startFromVisibleActivity)
        onOpenNavigation()
    }
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

    LaunchedEffect(nautiViewModel) {
        nautiViewModel.actions.collect { action ->
            when (action) {
                NautiAction.OpenTripPlanner -> openPlanner()
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
                    // Successful calculations are shown in the result dashboard. Incomplete
                    // calculations stay in the planner so the missing inputs/error remain visible.
                    showPlanner = !planningViewModel.uiState.value.hasCalculatedResult
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
                    // Show the existing passage result (or the neutral planner state) without
                    // starting a second calculation outside the explicit planner action.
                    showPlanner = true
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
            warningOverlays = warningOverlays,
            focusedWarningId = focusedWarningId,
            onWarningFocusConsumed = onWarningFocusConsumed,
            warningSummaryBottomPadding = bottomOverlayClearance + 104.dp,
            modifier = Modifier.fillMaxSize(),
            // Only harbours that are already part of the plan carry a marker, so a tap can no
            // longer be a "pick this as start/destination" gesture - it opens the planner instead.
            onHarbourClick = { openPlanner() },
            onOpenWarning = onOpenWarning,
        )

        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val panelWidthModifier =
            when {
                adaptiveLayout.isTablet -> Modifier.widthIn(max = adaptiveLayout.overlayMaxWidth)
                isLandscape -> Modifier.fillMaxWidth(0.48f).widthIn(max = 440.dp)
                else -> Modifier
            }
        val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
        val expandedPanelTopPadding = nautiExpandedTopPadding(topOverlayClearance)
        val expandedPanelBottomPadding =
            nautiExpandedBottomPadding(bottomOverlayClearance, imeBottomPadding)

        RoutePlanningPill(
            title = if (routeState.hasCompleteRouteInput) routeState.routeTitle else "Törn planen",
            subtitle =
                if (routeState.hasCompleteRouteInput) {
                    val displayedDeparture =
                        if (routeState.hasCalculatedResult) {
                            routeState.effectiveDeparture
                        } else {
                            routeState.departure
                        }
                    displayedDeparture.format(PILL_DATE_FORMAT)
                } else {
                    "Start, Ziel und Abfahrt auswählen"
                },
            isLoading = routeState.isWorking,
            onClick = ::openPlanner,
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
                            top = expandedPanelTopPadding,
                            bottom = expandedPanelBottomPadding,
                        ),
            )
        } else if (
            routeState.hasCalculatedResult &&
            activeVoyageState !is ActiveVoyageState.Active
        ) {
            RouteResultDashboard(
                state = routeState,
                onOpenNauti = nautiViewModel::showChat,
                onStartNavigation = ::requestNavigationStart,
                onEdit = {
                    planningViewModel.editCalculatedPlan()
                    showPlanner = true
                },
                onCancel = {
                    planningViewModel.discardPlanning()
                    showPlanner = false
                },
                onSave = {
                    val metrics = routeState.routeMetrics
                    val distStr = metrics?.distanceNm?.let { String.format(Locale.GERMANY, "%.1f nm", it) } ?: "–"
                    val durStr = metrics?.travelTime?.toMinutes()?.let { durationLabel(it) } ?: "–"
                    val wtStr = metrics?.worstUnderKeelClearanceMeters?.let { String.format(Locale.GERMANY, "%.2f m", it) } ?: "–"
                    val erftStr = String.format(Locale.GERMANY, "%.2f m", routeState.boatSettings.draftMeters + routeState.boatSettings.safetyMarginMeters)
                    tideViewModel.saveLog(
                        LogbookEntry(
                            date = routeState.effectiveDeparture.format(LOGBOOK_DATE_FORMAT),
                            routeDesc = routeState.routeTitle,
                            distance = distStr,
                            duration = durStr,
                            status = "planned",
                            details =
                                "abfahrt:${routeState.effectiveDeparture.format(PILL_DATE_FORMAT)}|" +
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
            MapCompactActions(
                showActiveVoyage = activeVoyageState is ActiveVoyageState.Active,
                onResumeNavigation = ::resumeActiveNavigation,
                viewModel = nautiViewModel,
                stations = stations,
                onOpenRevier = openRevierForHarbour,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(
                            max =
                                if (adaptiveLayout.isTablet) {
                                    adaptiveLayout.overlayMaxWidth
                                } else {
                                    COMPACT_ACTIONS_MAX_WIDTH
                                },
                        )
                        .fillMaxWidth()
                        .padding(
                            start = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                            end = if (adaptiveLayout.isTablet) adaptiveLayout.horizontalScreenPadding else 16.dp,
                            bottom = bottomOverlayClearance + 10.dp,
                        ),
            )
        }
    }

    if (showPlanner) {
        RoutePlannerSheet(
            viewModel = planningViewModel,
            onCalculationCompleted = { showPlanner = false },
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

internal enum class MapBottomActionsLayout {
    SINGLE,
    STACKED,
    SIDE_BY_SIDE,
}

internal fun mapBottomActionsLayout(
    availableWidthDp: Float,
    showActiveVoyage: Boolean,
): MapBottomActionsLayout =
    when {
        !showActiveVoyage -> MapBottomActionsLayout.SINGLE
        availableWidthDp >= SIDE_BY_SIDE_MIN_WIDTH_DP -> MapBottomActionsLayout.SIDE_BY_SIDE
        else -> MapBottomActionsLayout.STACKED
    }

internal fun nautiExpandedTopPadding(topOverlayClearance: Dp): Dp =
    topOverlayClearance + ROUTE_PLANNING_PILL_HEIGHT + 20.dp

internal fun nautiExpandedBottomPadding(
    bottomOverlayClearance: Dp,
    imeBottomPadding: Dp,
): Dp = maxOf(bottomOverlayClearance + 4.dp, imeBottomPadding + 8.dp)

@Composable
private fun MapCompactActions(
    showActiveVoyage: Boolean,
    onResumeNavigation: () -> Unit,
    viewModel: NautiViewModel,
    stations: List<TideStationData>,
    onOpenRevier: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.testTag("map_compact_actions")) {
        val layout = mapBottomActionsLayout(maxWidth.value, showActiveVoyage)
        when (layout) {
            MapBottomActionsLayout.SINGLE ->
                NautiDrawer(
                    viewModel = viewModel,
                    stations = stations,
                    onOpenRevier = onOpenRevier,
                    modifier = Modifier.fillMaxWidth(),
                )

            MapBottomActionsLayout.STACKED ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(COMPACT_ACTIONS_SPACING),
                ) {
                    ActiveVoyageResumePill(
                        onClick = onResumeNavigation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NautiDrawer(
                        viewModel = viewModel,
                        stations = stations,
                        onOpenRevier = onOpenRevier,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

            MapBottomActionsLayout.SIDE_BY_SIDE ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(COMPACT_ACTIONS_SPACING),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ActiveVoyageResumePill(
                        onClick = onResumeNavigation,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.weight(1f)) {
                        NautiDrawer(
                            viewModel = viewModel,
                            stations = stations,
                            onOpenRevier = onOpenRevier,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
        }
    }
}

@Composable
private fun ActiveVoyageResumePill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val titleColor = if (isDark) Color(0xFFF8FAFC) else TideNodeInk
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF62666C)

    Row(
        modifier =
            modifier
                .heightIn(min = MINIMUM_TOUCH_TARGET)
                .tideNodeGlass(cornerRadius = 25.dp, elevation = 10.dp, alpha = 0.88f)
                .clickable(
                    onClick = onClick,
                    onClickLabel = "Aktive Navigation fortsetzen",
                    role = Role.Button,
                )
                .padding(horizontal = 18.dp, vertical = 12.dp)
                .testTag("active_voyage_resume"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Map, null, tint = TideNodeBlue)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Aktive Fahrt",
                color = titleColor,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
            Text(
                "Navigation fortsetzen",
                color = subtitleColor,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            "›",
            color = TideNodeBlue,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
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
                .height(ROUTE_PLANNING_PILL_HEIGHT)
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

private val ROUTE_PLANNING_PILL_HEIGHT = 76.dp
private val MINIMUM_TOUCH_TARGET = 48.dp
private val COMPACT_ACTIONS_SPACING = 12.dp
private val COMPACT_ACTIONS_MAX_WIDTH = 720.dp
private const val SIDE_BY_SIDE_MIN_WIDTH_DP = 560f

private fun selectNautiStation(
    rawHarbourId: String?,
    stations: List<TideStationData>,
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
