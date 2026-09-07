package com.example.trnberechnung.mapplanning

import android.util.Log
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkStatic
import org.junit.Before
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutePlanningViewModelTest {
    @get:Rule
    val mainDispatcherRule = MapPlanningMainDispatcherRule()

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0

        mockkStatic(Dispatchers::class)
        every { Dispatchers.Default } returns mainDispatcherRule.dispatcher
    }

    @Test
    fun `refresh and departure changes retain complete route input`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.addIntermediateStops(
                listOf(HarbourId.JUIST_HARBOR, HarbourId.NORDERNEY_HARBOR),
            )
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.WANGEROOGE_HARBOR)
            advanceUntilIdle()

            val changedDeparture = viewModel.uiState.value.departure.plusHours(2)
            viewModel.updateDeparture(changedDeparture)
            viewModel.refresh()
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                startHarbourId shouldBe HarbourId.EMDEN_HARBOR
                destinationHarbourId shouldBe HarbourId.WANGEROOGE_HARBOR
                intermediateStops.map(IntermediateStop::harbourId) shouldContainExactly
                    listOf(HarbourId.JUIST_HARBOR, HarbourId.NORDERNEY_HARBOR)
                departure shouldBe changedDeparture
                // Note: routeStatus BEFAHRBAR is expected from mocks
                routeStatus shouldBe RouteStatus.BEFAHRBAR
                routeMetrics?.worstUnderKeelClearanceMeters shouldBe 0.8
                passageWindow?.contains(changedDeparture) shouldBe true
                error shouldBe null
            }
        }

    @Test
    fun `choosing an endpoint removes only the colliding stop`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.addIntermediateStops(
                listOf(HarbourId.JUIST_HARBOR, HarbourId.NORDERNEY_HARBOR),
            )
            advanceUntilIdle()

            viewModel.selectStart(HarbourId.NORDERNEY_HARBOR)
            advanceUntilIdle()

            viewModel.uiState.value.intermediateStops.map(IntermediateStop::harbourId) shouldContainExactly
                listOf(HarbourId.JUIST_HARBOR)
        }

    @Test
    fun `configured passage scanner is used instead of rebuilding a day scanner`() =
        runTest {
            var assessmentCalls = 0
            val viewModel =
                RoutePlanningViewModel(
                    routeGeometryProvider =
                        RouteGeometryProvider {
                            RouteGeometryResult.Success(
                                listOf(GeoPoint(53.3421, 7.1852), GeoPoint(53.6722, 6.9982)),
                            )
                        },
                    routeAssessmentProvider =
                        RouteAssessmentProvider {
                            assessmentCalls += 1
                            RouteSafetyAssessment(
                                expectedWaypointCount = 2,
                                clearanceSamples =
                                    listOf(ClearanceSample("Start", 1.0), ClearanceSample("Ziel", 0.8)),
                                allLegsValid = true,
                                weatherStatus = WeatherStatus.BEFAHRBAR,
                            )
                        },
                    passageWindowScanner =
                        PassageWindowScanner(
                            scanIncrement = Duration.ofMinutes(10),
                            scanBackward = Duration.ZERO,
                            scanForward = Duration.ZERO,
                        ),
                    clock = Clock.fixed(Instant.parse("2026-07-29T11:35:00Z"), ZoneOffset.UTC),
                )

            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.JUIST_HARBOR)
            advanceUntilIdle()

            // initial assessment + one configured scan point + final assessment
            assessmentCalls shouldBe 3
            viewModel.uiState.value.passageWindows.size shouldBe 1
        }

    private fun createViewModel(): RoutePlanningViewModel =
        RoutePlanningViewModel(
            routeGeometryProvider =
                RouteGeometryProvider {
                    RouteGeometryResult.Success(
                        listOf(
                            GeoPoint(53.3421, 7.1852),
                            GeoPoint(53.7755, 7.8683),
                        ),
                    )
                },
            routeAssessmentProvider =
                RouteAssessmentProvider { input ->
                    RouteSafetyAssessment(
                        expectedWaypointCount = 2,
                        clearanceSamples =
                            listOf(
                                ClearanceSample("Start", 1.0),
                                ClearanceSample("Ziel", 0.8),
                            ),
                        allLegsValid = true,
                        weatherStatus = WeatherStatus.BEFAHRBAR,
                        messages = emptyList(),
                    )
                },
            passageWindowScanner =
                PassageWindowScanner(
                    scanIncrement = Duration.ofMinutes(10),
                    scanBackward = Duration.ZERO,
                    scanForward = Duration.ZERO,
                ),
            clock = Clock.fixed(Instant.parse("2026-07-29T11:35:00Z"), ZoneOffset.UTC),
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MapPlanningMainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
