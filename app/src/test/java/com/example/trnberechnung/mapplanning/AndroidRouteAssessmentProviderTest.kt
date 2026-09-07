package com.example.trnberechnung.mapplanning

import com.example.trnberechnung.dto.WeatherDto
import com.example.trnberechnung.model.TideEvent
import com.example.trnberechnung.model.TideStationData
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.ZonedDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AndroidRouteAssessmentProviderTest {
    @Test
    fun `live snapshot produces interpolated WuK and weather status`() =
        runTest {
            val departure =
                ZonedDateTime.parse("2026-07-29T15:00:00+02:00[Europe/Berlin]")
            val station =
                TideStationData(
                    area = "Emden",
                    region = "Nordsee",
                    latitude = 53.3421,
                    longitude = 7.1852,
                    waterLevel = null,
                    meanHighWater = null,
                    meanLowWater = null,
                    gaugeLabel = "Emden",
                    forecastTimestamp = "2026-07-29T13:00:00Z",
                    weatherForecast =
                        listOf(
                            weather("2026-07-29T13:00:00Z"),
                            weather("2026-07-29T14:00:00Z"),
                        ),
                    events =
                        listOf(
                            TideEvent("2026-07-29T12:00:00+02:00", "NW", 1.0),
                            TideEvent("2026-07-29T18:00:00+02:00", "HW", 3.0),
                        ),
                )
            val provider =
                AndroidRouteAssessmentProvider(
                    tideStationProvider = { listOf(station) },
                    chartDepthProvider = { 2.0 },
                    fairwayRouteResolver = null,
                )
            val request =
                RoutePlanningRequest(
                    startHarbourId = HarbourId.EMDEN_HARBOR,
                    destinationHarbourId = HarbourId.JUIST_HARBOR,
                    intermediateStops = emptyList(),
                    departure = departure,
                    boatSettings =
                        BoatSettings(
                            draftMeters = 1.0,
                            safetyMarginMeters = 0.3,
                            speedKnots = 6.0,
                        ),
                )
            val geometry =
                listOf(
                    GeoPoint(53.3421, 7.1852),
                    GeoPoint(53.3430, 7.1840),
                )
            val metrics =
                requireNotNull(
                    RouteMetricsCalculator.calculate(
                        geometry,
                        departure,
                        request.boatSettings,
                    ),
                )

            val assessment =
                provider.assess(
                    RouteAssessmentInput(
                        request = request,
                        routeGeometry = geometry,
                        routeMetrics = metrics,
                    ),
                )

            assessment.expectedWaypointCount shouldBe 2
            assessment.clearanceSamples.size shouldBe 2
            assessment.clearanceSamples.first().clearanceMeters shouldBe
                (3.0 plusOrMinus 0.1)
            // Bright Sky reports km/h: 18.52 / 27.78 km/h are 10 / 15 kn and safe.
            assessment.weatherStatus shouldBe WeatherStatus.BEFAHRBAR
            assessment.allLegsValid shouldBe true
        }

    @Test
    fun `missing chart depth can never become passable`() =
        runTest {
            val departure =
                ZonedDateTime.parse("2026-07-29T15:00:00+02:00[Europe/Berlin]")
            val station =
                TideStationData(
                    area = "Emden",
                    region = "Nordsee",
                    latitude = 53.3421,
                    longitude = 7.1852,
                    waterLevel = null,
                    meanHighWater = null,
                    meanLowWater = null,
                    gaugeLabel = "Emden",
                    forecastTimestamp = "2026-07-29T13:00:00Z",
                    weatherForecast = listOf(weather("2026-07-29T13:00:00Z")),
                    events =
                        listOf(
                            TideEvent("2026-07-29T10:00:00Z", "NW", 1.0),
                            TideEvent("2026-07-29T16:00:00Z", "HW", 3.0),
                        ),
                )
            val provider =
                AndroidRouteAssessmentProvider(
                    tideStationProvider = { listOf(station) },
                    chartDepthProvider = { null },
                    fairwayRouteResolver = null,
                )
            val request =
                RoutePlanningRequest(
                    HarbourId.EMDEN_HARBOR,
                    HarbourId.JUIST_HARBOR,
                    emptyList(),
                    departure,
                    BoatSettings(),
                )
            val geometry =
                listOf(
                    GeoPoint(53.3421, 7.1852),
                    GeoPoint(53.3430, 7.1840),
                )
            val metrics =
                requireNotNull(
                    RouteMetricsCalculator.calculate(
                        geometry,
                        departure,
                        request.boatSettings,
                    ),
                )
            val assessment =
                provider.assess(RouteAssessmentInput(request, geometry, metrics))

            UnderKeelSafetyEvaluator.evaluate(
                samples = assessment.clearanceSamples,
                safetyMarginMeters = request.boatSettings.safetyMarginMeters,
            ).status shouldBe RouteStatus.UNVOLLSTAENDIG
        }

    private fun weather(timestamp: String): WeatherDto =
        WeatherDto(
            timestamp = timestamp,
            temperature = 18.0,
            windSpeed = 18.52,
            windDirection = 270,
            windGustSpeed = 27.78,
            condition = "dry",
            icon = null,
            cloudCover = 20,
            pressureMsl = 1_015.0,
            relativeHumidity = 70,
            precipitation = 0.0,
            visibility = 10_000,
            sunshine = 30.0,
            dewPoint = 10.0,
            solar = 100.0,
            precipitationProbability = 10,
        )
}
