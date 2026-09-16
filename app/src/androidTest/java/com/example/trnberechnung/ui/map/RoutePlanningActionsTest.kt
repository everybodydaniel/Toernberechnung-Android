package com.example.trnberechnung.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.example.trnberechnung.mapplanning.BoatSettings
import com.example.trnberechnung.mapplanning.GeoPoint
import com.example.trnberechnung.mapplanning.HarbourId
import com.example.trnberechnung.mapplanning.PassageWindow
import com.example.trnberechnung.mapplanning.RouteMetrics
import com.example.trnberechnung.mapplanning.RoutePlanningUiState
import com.example.trnberechnung.mapplanning.RouteStatus
import java.time.Duration
import java.time.ZonedDateTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RoutePlanningActionsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun plannerSpeedControlsAndCalculateButtonFollowDraftState() {
        var state by
            mutableStateOf(
                RoutePlanningUiState(
                    departure = ZonedDateTime.parse("2026-09-16T09:00:00+02:00[Europe/Berlin]"),
                    boatSettings = BoatSettings(speedKnots = 6.0),
                ),
            )
        var calculationCalls = 0
        var dismissCalls = 0

        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Surface(Modifier.requiredSize(320.dp, 480.dp)) {
                    RoutePlannerSheetContent(
                        uiState = state,
                        onStartSelected = { state = state.copy(startHarbourId = it) },
                        onDestinationSelected = { state = state.copy(destinationHarbourId = it) },
                        onAddStops = { 0 },
                        onRemoveStop = {},
                        onDepartureChanged = { state = state.copy(departure = it) },
                        onSpeedChanged = {
                            state = state.copy(boatSettings = state.boatSettings.copy(speedKnots = it))
                        },
                        onCalculate = {
                            calculationCalls += 1
                            state = state.copy(isCalculating = true)
                        },
                        onDismiss = { dismissCalls += 1 },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("route_calculate").assertIsDisplayed().assertIsNotEnabled()
        composeTestRule.onNodeWithTag("route_speed_value").performScrollTo().assertTextEquals("6,0 kn")
        composeTestRule.onNodeWithTag("route_speed_increase").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("route_speed_value").assertTextEquals("6,5 kn")
        composeTestRule.onNodeWithTag("route_speed_decrease").performClick()
        composeTestRule.onNodeWithTag("route_speed_value").assertTextEquals("6,0 kn")

        composeTestRule.runOnIdle {
            state =
                state.copy(
                    startHarbourId = HarbourId.EMDEN_HARBOR,
                    destinationHarbourId = HarbourId.NORDERNEY_HARBOR,
                )
        }
        composeTestRule.onNodeWithTag("route_calculate").assertIsEnabled().performClick()
        composeTestRule.onNodeWithTag("route_calculate").assertIsNotEnabled()
        composeTestRule.onNodeWithTag("route_planner_close").performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, calculationCalls)
            assertEquals(1, dismissCalls)
        }
    }

    @Test
    fun finalResultShowsTwoByTwoActionsAndDispatchesEachCallbackOnce() {
        var saveCalls = 0
        var navigationCalls = 0
        var editCalls = 0
        var cancelCalls = 0

        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Surface(Modifier.requiredSize(420.dp, 360.dp)) {
                    RouteResultDashboard(
                        state = calculatedState(),
                        onOpenNauti = {},
                        onStartNavigation = { navigationCalls += 1 },
                        onSave = { saveCalls += 1 },
                        onEdit = { editCalls += 1 },
                        onCancel = { cancelCalls += 1 },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        val tags =
            listOf(
                "route_dashboard_save",
                "route_dashboard_navigation",
                "route_dashboard_edit",
                "route_dashboard_cancel",
            )
        tags.forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsEnabled()
        }

        composeTestRule.onNodeWithTag("route_dashboard_cancel").performScrollTo()
        val saveBounds = composeTestRule.onNodeWithTag("route_dashboard_save").fetchSemanticsNode().boundsInRoot
        val navigationBounds =
            composeTestRule.onNodeWithTag("route_dashboard_navigation").fetchSemanticsNode().boundsInRoot
        val editBounds = composeTestRule.onNodeWithTag("route_dashboard_edit").fetchSemanticsNode().boundsInRoot
        val cancelBounds = composeTestRule.onNodeWithTag("route_dashboard_cancel").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(saveBounds.top - navigationBounds.top) < 1f)
        assertTrue(abs(editBounds.top - cancelBounds.top) < 1f)
        assertTrue(editBounds.top >= saveBounds.bottom)

        composeTestRule.onNodeWithTag("route_dashboard_save").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(0, navigationCalls)
            assertEquals(0, editCalls)
            assertEquals(0, cancelCalls)
        }
        composeTestRule.onNodeWithTag("route_dashboard_navigation").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("route_dashboard_edit").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("route_dashboard_cancel").performScrollTo().performClick()
        composeTestRule.runOnIdle {
            assertEquals(1, saveCalls)
            assertEquals(1, navigationCalls)
            assertEquals(1, editCalls)
            assertEquals(1, cancelCalls)
        }
    }

    @Test
    fun narrowResultStacksActionsWithoutTruncatingLabels() {
        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Surface(Modifier.requiredSize(280.dp, 480.dp)) {
                    RouteResultDashboard(
                        state = calculatedState(),
                        onOpenNauti = {},
                        onStartNavigation = {},
                        onSave = {},
                        onEdit = {},
                        onCancel = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("route_dashboard_cancel").performScrollTo()
        val save = composeTestRule.onNodeWithTag("route_dashboard_save")
        val navigation = composeTestRule.onNodeWithTag("route_dashboard_navigation")
        val edit = composeTestRule.onNodeWithTag("route_dashboard_edit")
        val cancel = composeTestRule.onNodeWithTag("route_dashboard_cancel")

        save.assertTextEquals("Speichern")
        navigation.assertTextEquals("Fahrt starten")
        edit.assertTextEquals("Bearbeiten")
        cancel.assertTextEquals("Abbrechen")

        val saveBounds = save.fetchSemanticsNode().boundsInRoot
        val navigationBounds = navigation.fetchSemanticsNode().boundsInRoot
        val editBounds = edit.fetchSemanticsNode().boundsInRoot
        val cancelBounds = cancel.fetchSemanticsNode().boundsInRoot
        assertTrue(navigationBounds.top >= saveBounds.bottom)
        assertTrue(editBounds.top >= navigationBounds.bottom)
        assertTrue(cancelBounds.top >= editBounds.bottom)
        assertTrue(abs(saveBounds.width - navigationBounds.width) < 1f)
        assertTrue(abs(saveBounds.width - editBounds.width) < 1f)
        assertTrue(abs(saveBounds.width - cancelBounds.width) < 1f)
    }

    @Test
    fun actionsStayDisabledUntilMetricsBelongToAFinalResult() {
        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Surface(Modifier.requiredSize(320.dp, 360.dp)) {
                    RouteResultDashboard(
                        state = calculatedState().copy(hasCalculatedResult = false),
                        onOpenNauti = {},
                        onStartNavigation = {},
                        onSave = {},
                        onEdit = {},
                        onCancel = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        listOf(
            "route_dashboard_save",
            "route_dashboard_navigation",
            "route_dashboard_edit",
            "route_dashboard_cancel",
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).performScrollTo().assertIsNotEnabled()
        }
    }

    @Test
    fun calculatedPassageRemainsVisibleUntilResultIsOpenedExplicitly() {
        var resultOpenCalls = 0

        composeTestRule.setContent {
            MaterialTheme {
                androidx.compose.material3.Surface(Modifier.requiredSize(320.dp, 480.dp)) {
                    RoutePlannerSheetContent(
                        uiState = calculatedState(),
                        onStartSelected = {},
                        onDestinationSelected = {},
                        onAddStops = { 0 },
                        onRemoveStop = {},
                        onDepartureChanged = {},
                        onSpeedChanged = {},
                        onCalculate = { resultOpenCalls += 1 },
                        onDismiss = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("route_passage_result").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("route_calculate")
            .assertTextEquals("Planungsergebnis anzeigen")
            .assertIsEnabled()
            .performClick()
        composeTestRule.runOnIdle { assertEquals(1, resultOpenCalls) }
    }

    private fun calculatedState(): RoutePlanningUiState {
        val departure = ZonedDateTime.parse("2026-09-16T09:00:00+02:00[Europe/Berlin]")
        return RoutePlanningUiState(
            startHarbourId = HarbourId.EMDEN_HARBOR,
            destinationHarbourId = HarbourId.NORDERNEY_HARBOR,
            departure = departure,
            boatSettings = BoatSettings(speedKnots = 6.0),
            routeGeometry = listOf(GeoPoint(53.3421, 7.1852), GeoPoint(53.7024, 7.1637)),
            routeStatus = RouteStatus.BEFAHRBAR,
            tidalStatus = RouteStatus.BEFAHRBAR,
            routeMetrics =
                RouteMetrics(
                    distanceNm = 24.0,
                    travelTime = Duration.ofHours(4),
                    arrival = departure.plusHours(4),
                    worstUnderKeelClearanceMeters = 0.8,
                    dieselLiters = 8.4,
                ),
            passageWindows =
                listOf(
                    PassageWindow(
                        start = departure.minusMinutes(30),
                        end = departure.plusMinutes(90),
                        recommendedDeparture = departure,
                        anchoredHighWater = departure.plusMinutes(20),
                        bottleneckName = "Osterems",
                    ),
                ),
            hasCalculatedResult = true,
        )
    }
}
