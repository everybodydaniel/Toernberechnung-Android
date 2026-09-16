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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    fun `calculation and departure changes retain complete route input`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.addIntermediateStops(
                listOf(HarbourId.JUIST_HARBOR, HarbourId.NORDERNEY_HARBOR),
            )
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.WANGEROOGE_HARBOR)
            viewModel.calculateRoute()
            advanceUntilIdle()

            val changedDeparture = viewModel.uiState.value.departure.plusHours(2)
            viewModel.updateDeparture(changedDeparture)
            viewModel.calculateRoute()
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
            viewModel.calculateRoute()
            advanceUntilIdle()

            // one configured scan point + final assessment
            assessmentCalls shouldBe 2
            viewModel.uiState.value.passageWindows.size shouldBe 1
        }

    @Test
    fun `input changes do not calculate automatically and enable calculation only when valid`() =
        runTest {
            var geometryCalls = 0
            val viewModel =
                createViewModel(
                    geometryProvider =
                        RouteGeometryProvider {
                            geometryCalls += 1
                            successfulGeometry()
                        },
                )

            viewModel.uiState.value.canCalculate shouldBe false
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.uiState.value.canCalculate shouldBe false
            viewModel.selectDestination(HarbourId.JUIST_HARBOR)
            advanceUntilIdle()

            geometryCalls shouldBe 0
            viewModel.uiState.value.canCalculate shouldBe true

            viewModel.calculateRoute()
            viewModel.uiState.value.canCalculate shouldBe false
            advanceUntilIdle()

            geometryCalls shouldBe 1
            viewModel.uiState.value.hasCalculatedResult shouldBe true
            viewModel.uiState.value.canCalculate shouldBe true
        }

    @Test
    fun `planning speed is stepped clamped and does not overwrite profile baseline`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.updateBoatSettings(
                draftMeters = 1.2,
                safetyMarginMeters = 0.2,
                speedKnots = 6.0,
                waterLevelCorrectionMeters = 0.1,
            )

            viewModel.updatePlanningSpeed(6.26)
            viewModel.uiState.value.boatSettings.speedKnots shouldBe 6.5
            viewModel.updatePlanningSpeed(100.0)
            viewModel.uiState.value.boatSettings.speedKnots shouldBe MAX_PLANNING_SPEED_KNOTS
            viewModel.updatePlanningSpeed(-4.0)
            viewModel.uiState.value.boatSettings.speedKnots shouldBe MIN_PLANNING_SPEED_KNOTS

            viewModel.discardPlanning()
            viewModel.uiState.value.boatSettings.speedKnots shouldBe 6.0
        }

    @Test
    fun `recalculation uses changed planning speed for travel time and arrival`() =
        runTest {
            val viewModel = createViewModel()
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.WANGEROOGE_HARBOR)
            viewModel.calculateRoute()
            advanceUntilIdle()
            val firstState = viewModel.uiState.value
            val firstDuration = requireNotNull(firstState.routeMetrics).travelTime

            viewModel.updatePlanningSpeed(12.0)
            viewModel.uiState.value.hasCalculatedResult shouldBe false
            viewModel.uiState.value.routeMetrics shouldBe null
            viewModel.calculateRoute()
            advanceUntilIdle()
            val secondState = viewModel.uiState.value
            val secondMetrics = requireNotNull(secondState.routeMetrics)

            (secondMetrics.travelTime < firstDuration) shouldBe true
            secondMetrics.arrival shouldBe secondState.departure.plus(secondMetrics.travelTime)
            secondState.boatSettings.speedKnots shouldBe 12.0
        }

    @Test
    fun `edit calculated plan retains draft values and invalidates result`() =
        runTest {
            val viewModel = createViewModel()
            val departure = viewModel.uiState.value.departure.plusDays(1).withHour(8)
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.WANGEROOGE_HARBOR)
            viewModel.addIntermediateStops(listOf(HarbourId.JUIST_HARBOR))
            viewModel.updateDeparture(departure)
            viewModel.updatePlanningSpeed(7.5)
            viewModel.calculateRoute()
            advanceUntilIdle()

            viewModel.editCalculatedPlan()
            with(viewModel.uiState.value) {
                startHarbourId shouldBe HarbourId.EMDEN_HARBOR
                destinationHarbourId shouldBe HarbourId.WANGEROOGE_HARBOR
                intermediateStops.map(IntermediateStop::harbourId) shouldContainExactly
                    listOf(HarbourId.JUIST_HARBOR)
                this.departure shouldBe departure
                boatSettings.speedKnots shouldBe 7.5
                hasCalculatedResult shouldBe false
                routeMetrics shouldBe null
                passageWindows shouldBe emptyList()
                canCalculate shouldBe true
            }
        }

    @Test
    fun `discard planning resets draft result and temporary speed to profile baseline`() =
        runTest {
            val viewModel = createViewModel()
            val initialDeparture = viewModel.uiState.value.departure
            viewModel.updateBoatSettings(1.3, 0.2, 6.5, 0.0)
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.JUIST_HARBOR)
            viewModel.updatePlanningSpeed(8.0)
            viewModel.calculateRoute()
            advanceUntilIdle()

            viewModel.discardPlanning()
            with(viewModel.uiState.value) {
                startHarbourId shouldBe null
                destinationHarbourId shouldBe null
                intermediateStops shouldBe emptyList()
                departure shouldBe initialDeparture
                boatSettings.speedKnots shouldBe 6.5
                hasCalculatedResult shouldBe false
                routeGeometry shouldBe emptyList()
                routeMetrics shouldBe null
                passageWindows shouldBe emptyList()
                isWorking shouldBe false
            }
        }

    @Test
    fun `discard ignores a late result from a running calculation`() =
        runTest {
            val calculationStarted = CompletableDeferred<Unit>()
            val releaseCalculation = CompletableDeferred<Unit>()
            val viewModel =
                createViewModel(
                    geometryProvider =
                        RouteGeometryProvider {
                            calculationStarted.complete(Unit)
                            releaseCalculation.await()
                            successfulGeometry()
                        },
                )
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.JUIST_HARBOR)
            viewModel.calculateRoute()
            runCurrent()
            calculationStarted.await()

            viewModel.discardPlanning()
            releaseCalculation.complete(Unit)
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                hasCalculatedResult shouldBe false
                routeGeometry shouldBe emptyList()
                routeMetrics shouldBe null
                startHarbourId shouldBe null
                isWorking shouldBe false
            }
        }

    @Test
    fun `calculate route ignores duplicate taps while calculation is running`() =
        runTest {
            var geometryCalls = 0
            val releaseCalculation = CompletableDeferred<Unit>()
            val viewModel =
                createViewModel(
                    geometryProvider =
                        RouteGeometryProvider {
                            geometryCalls += 1
                            releaseCalculation.await()
                            successfulGeometry()
                        },
                )
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.JUIST_HARBOR)

            viewModel.calculateRoute()
            viewModel.calculateRoute()
            runCurrent()
            geometryCalls shouldBe 1

            releaseCalculation.complete(Unit)
            advanceUntilIdle()
            viewModel.uiState.value.hasCalculatedResult shouldBe true
        }

    @Test
    fun `recommended departure drives metrics without replacing the selected departure day`() =
        runTest {
            val clock = Clock.fixed(Instant.parse("2026-07-29T21:55:00Z"), ZoneOffset.UTC)
            val selectedDeparture =
                java.time.ZonedDateTime.ofInstant(
                    Instant.parse("2026-07-29T21:55:00Z"),
                    MAP_PLANNING_ZONE_ID,
                )
            val safestDeparture =
                java.time.ZonedDateTime.ofInstant(
                    Instant.parse("2026-07-29T22:05:00Z"),
                    MAP_PLANNING_ZONE_ID,
                )
            val viewModel =
                createViewModel(
                    assessmentProvider =
                        RouteAssessmentProvider { input ->
                            val clearance = if (input.request.departure == safestDeparture) 2.0 else 1.0
                            safeAssessment(clearance)
                        },
                    scanner =
                        PassageWindowScanner(
                            scanIncrement = Duration.ofMinutes(10),
                            scanBackward = Duration.ZERO,
                            scanForward = Duration.ofMinutes(20),
                        ),
                    clock = clock,
                )
            viewModel.updateDeparture(selectedDeparture)
            viewModel.selectStart(HarbourId.EMDEN_HARBOR)
            viewModel.selectDestination(HarbourId.JUIST_HARBOR)
            viewModel.calculateRoute()
            advanceUntilIdle()

            with(viewModel.uiState.value) {
                departure shouldBe selectedDeparture
                effectiveDeparture shouldBe safestDeparture
                passageWindow?.recommendedDeparture shouldBe safestDeparture
                routeMetrics?.arrival shouldBe safestDeparture.plus(requireNotNull(routeMetrics).travelTime)
                routeMetrics?.worstUnderKeelClearanceMeters shouldBe 2.0
                hasCalculatedResult shouldBe true
            }

            viewModel.editCalculatedPlan()
            viewModel.uiState.value.departure shouldBe selectedDeparture
        }

    private fun createViewModel(
        geometryProvider: RouteGeometryProvider = RouteGeometryProvider { successfulGeometry() },
        assessmentProvider: RouteAssessmentProvider = RouteAssessmentProvider { safeAssessment() },
        scanner: PassageWindowScanner =
            PassageWindowScanner(
                scanIncrement = Duration.ofMinutes(10),
                scanBackward = Duration.ZERO,
                scanForward = Duration.ZERO,
            ),
        clock: Clock = Clock.fixed(Instant.parse("2026-07-29T11:35:00Z"), ZoneOffset.UTC),
    ): RoutePlanningViewModel =
        RoutePlanningViewModel(
            routeGeometryProvider = geometryProvider,
            routeAssessmentProvider = assessmentProvider,
            passageWindowScanner = scanner,
            clock = clock,
        )

    private fun successfulGeometry(): RouteGeometryResult.Success =
        RouteGeometryResult.Success(
            listOf(
                GeoPoint(53.3421, 7.1852),
                GeoPoint(53.7755, 7.8683),
            ),
        )

    private fun safeAssessment(worstClearance: Double = 0.8): RouteSafetyAssessment =
        RouteSafetyAssessment(
            expectedWaypointCount = 2,
            clearanceSamples =
                listOf(
                    ClearanceSample("Start", worstClearance),
                    ClearanceSample("Ziel", worstClearance),
                ),
            allLegsValid = true,
            weatherStatus = WeatherStatus.BEFAHRBAR,
            messages = emptyList(),
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
