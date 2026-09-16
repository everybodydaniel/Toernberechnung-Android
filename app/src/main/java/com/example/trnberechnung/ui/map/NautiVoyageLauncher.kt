package com.example.trnberechnung.ui.map

import com.example.trnberechnung.mapplanning.HarbourId
import com.example.trnberechnung.mapplanning.RoutePlanningViewModel
import com.example.trnberechnung.mapplanning.RouteStatus
import com.example.trnberechnung.nauti.NautiAction
import com.example.trnberechnung.navigation.NavigationRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Outcome of preparing a chat-requested voyage, before anything is started. */
enum class VoyagePreflight {
    /** Route is navigable and complete; only the location permission gate is left. */
    READY,

    /** Navigable with restrictions - needs an explicit second confirmation from the skipper. */
    RESTRICTED,

    /** The deterministic engine says the route is not navigable. Must never start. */
    BLOCKED_UNSAFE,

    /** Safety data is missing or the route could not be fully resolved. Must never start. */
    BLOCKED_INCOMPLETE,

    /** The route calculation did not settle in time. */
    TIMED_OUT,

    /** Nauti supplied harbours that do not exist or start == destination. */
    INVALID_INPUT,
}

data class VoyagePreflightResult(
    val outcome: VoyagePreflight,
    val route: NavigationRoute? = null,
    val status: RouteStatus = RouteStatus.UNVOLLSTAENDIG,
    val message: String? = null,
)

/**
 * Turns a [NautiAction.StartVoyage] into a planned, safety-checked route.
 *
 * Deliberately a plain class rather than part of a ViewModel or a composable:
 *  - `NautiViewModel` must not hold a [RoutePlanningViewModel] (ViewModel-to-ViewModel coupling, and
 *    it is constructed in tests with neither).
 *  - A `LaunchedEffect` can be recomposed away mid-suspend, which would abandon a half-started
 *    voyage.
 *
 * It only ever *prepares*. Starting the GPS recording stays in the existing
 * `MapTabScreen.startNavigationAfterValidation()` path so there is exactly one start implementation.
 */
class NautiVoyageLauncher(
    private val planning: RoutePlanningViewModel,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
) {
    suspend fun planAndPreflight(action: NautiAction.StartVoyage): VoyagePreflightResult {
        val start = HarbourId.fromRawValue(action.startHarbourId)
        val destination = HarbourId.fromRawValue(action.destinationHarbourId)
        val stops = action.intermediateHarbourIds.mapNotNull(HarbourId::fromRawValue)

        if (start == null || destination == null || start == destination) {
            return VoyagePreflightResult(
                outcome = VoyagePreflight.INVALID_INPUT,
                message = "Nauti hat ungültige Hafenangaben geliefert.",
            )
        }

        planning.clearRoute()
        planning.selectStart(start)
        planning.selectDestination(destination)
        planning.addIntermediateStops(stops)
        action.departure?.let(planning::updateDeparture)
        planning.calculateRoute()

        // Planning input changes are deliberately side-effect free. The single explicit calculation
        // above owns geometry, safety and passage-window work; wait until that complete result (or an
        // error) settles before applying the navigation gates.
        val settled =
            withTimeoutOrNull(timeoutMillis) {
                planning.uiState.first { state ->
                    !state.isWorking && (state.hasCalculatedResult || state.error != null)
                }
            }
                ?: return VoyagePreflightResult(
                    outcome = VoyagePreflight.TIMED_OUT,
                    message = "Die Routenberechnung hat zu lange gedauert. Bitte im Planer prüfen.",
                )

        return when (settled.routeStatus) {
            RouteStatus.NICHT_BEFAHRBAR ->
                VoyagePreflightResult(
                    outcome = VoyagePreflight.BLOCKED_UNSAFE,
                    status = settled.routeStatus,
                    message = "Dieser Törn ist nicht befahrbar. Die Fahrt wurde nicht gestartet.",
                )

            RouteStatus.UNVOLLSTAENDIG ->
                VoyagePreflightResult(
                    outcome = VoyagePreflight.BLOCKED_INCOMPLETE,
                    status = settled.routeStatus,
                    message =
                        settled.error
                            ?: "Für diesen Törn fehlen Sicherheitsdaten. Die Fahrt wurde nicht gestartet.",
                )

            RouteStatus.EINGESCHRAENKT ->
                VoyagePreflightResult(
                    outcome = VoyagePreflight.RESTRICTED,
                    route = settled.toNavigationRouteOrNull(),
                    status = settled.routeStatus,
                )

            RouteStatus.BEFAHRBAR -> {
                val route = settled.toNavigationRouteOrNull()
                if (route == null) {
                    VoyagePreflightResult(
                        outcome = VoyagePreflight.BLOCKED_INCOMPLETE,
                        status = settled.routeStatus,
                        message = "Für die Navigation fehlt eine vollständige Route.",
                    )
                } else {
                    VoyagePreflightResult(
                        outcome = VoyagePreflight.READY,
                        route = route,
                        status = settled.routeStatus,
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 25_000L
    }
}
