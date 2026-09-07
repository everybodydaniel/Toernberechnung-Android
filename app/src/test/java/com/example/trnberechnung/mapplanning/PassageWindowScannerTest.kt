package com.example.trnberechnung.mapplanning

import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PassageWindowScannerTest {
    private val center =
        ZonedDateTime.parse("2026-07-29T13:00:00+02:00[Europe/Berlin]")

    private val scanner =
        PassageWindowScanner(
            scanIncrement = Duration.ofMinutes(10),
            scanBackward = Duration.ofHours(1),
            scanForward = Duration.ofHours(2),
        )

    @Test
    fun `window containing selected departure wins`() =
        runTest {
            val window =
                scanner.findSafeWindow(
                    center = center,
                    evaluator =
                        PassageCandidateEvaluator { departure ->
                            assessment(
                                safe =
                                    !departure.isBefore(center.minusMinutes(20)) &&
                                        !departure.isAfter(center.plusMinutes(20)),
                            )
                        },
                )

            window?.start shouldBe center.minusMinutes(20)
            window?.end shouldBe center.plusMinutes(20)
            window?.contains(center) shouldBe true
        }

    @Test
    fun `first later window is selected when center is unsafe`() =
        runTest {
            val window =
                scanner.findSafeWindow(
                    center = center,
                    evaluator =
                        PassageCandidateEvaluator { departure ->
                            assessment(
                                safe =
                                    !departure.isBefore(center.plusMinutes(30)) &&
                                        !departure.isAfter(center.plusMinutes(50)),
                            )
                        },
                )

            window?.start shouldBe center.plusMinutes(30)
            window?.end shouldBe center.plusMinutes(50)
        }

    @Test
    fun `candidate is unsafe if some waypoints have missing data (strict mode)`() =
        runTest {
            val window =
                scanner.findSafeWindow(
                    center = center,
                    evaluator =
                        PassageCandidateEvaluator {
                            PassageCandidateAssessment(
                                expectedWaypointCount = 2,
                                waypointClearances =
                                    listOf(
                                        ClearanceSample("Emden", 1.0),
                                        ClearanceSample("Juist", null), // Missing data
                                    ),
                                allLegsValid = true,
                            )
                        },
                )

            window shouldBe null
        }

    @Test
    fun `candidate is unsafe when route assessment marks a leg invalid`() =
        runTest {
            val window =
                scanner.findSafeWindow(
                    center = center,
                    evaluator =
                        PassageCandidateEvaluator {
                            PassageCandidateAssessment(
                                expectedWaypointCount = 1,
                                waypointClearances = listOf(ClearanceSample("Fahrwasser", 2.0)),
                                allLegsValid = false,
                            )
                        },
                )

            window shouldBe null
        }

    @Test
    fun `worst quality and bottleneck metadata are retained`() =
        runTest {
            val highWater = center.plusHours(1)
            val window =
                PassageWindowScanner(
                    scanIncrement = Duration.ofMinutes(10),
                    scanBackward = Duration.ZERO,
                    scanForward = Duration.ZERO,
                ).findSafeWindow(
                    center = center,
                    evaluator =
                        PassageCandidateEvaluator {
                            PassageCandidateAssessment(
                                expectedWaypointCount = 2,
                                waypointClearances =
                                    listOf(
                                        ClearanceSample(
                                            waypointName = "Emden",
                                            clearanceMeters = 1.0,
                                        ),
                                        ClearanceSample(
                                            waypointName = "Wattenhoch",
                                            clearanceMeters = 0.1,
                                            waterLevelQuality =
                                                WaterLevelQuality.OUTSIDE_FORECAST_HORIZON,
                                            anchoredHighWater = highWater,
                                        ),
                                    ),
                                allLegsValid = true,
                            )
                        },
                )

            window?.bottleneckName shouldBe "Wattenhoch"
            window?.anchoredHighWater shouldBe highWater
            window?.waterLevelQuality shouldBe WaterLevelQuality.OUTSIDE_FORECAST_HORIZON
        }

    @Test
    fun `window respects safety margin`() =
        runTest {
            val margin = 0.5
            val window =
                scanner.findSafeWindow(
                    center = center,
                    evaluator =
                        PassageCandidateEvaluator { departure ->
                            PassageCandidateAssessment(
                                expectedWaypointCount = 1,
                                waypointClearances = listOf(
                                    ClearanceSample("Point", 0.4)
                                ),
                                allLegsValid = true,
                                safetyMarginMeters = margin
                            )
                        },
                )

            window shouldBe null
        }

    private fun assessment(safe: Boolean, margin: Double = 0.0): PassageCandidateAssessment =
        PassageCandidateAssessment(
            expectedWaypointCount = 2,
            waypointClearances =
                if (safe) {
                    listOf(
                        ClearanceSample("Emden", 1.0),
                        ClearanceSample("Juist", 0.5),
                    )
                } else {
                    listOf(
                        ClearanceSample("Emden", 1.0),
                        ClearanceSample("Juist", -0.1),
                    )
                },
            allLegsValid = true,
            safetyMarginMeters = margin
        )
}
