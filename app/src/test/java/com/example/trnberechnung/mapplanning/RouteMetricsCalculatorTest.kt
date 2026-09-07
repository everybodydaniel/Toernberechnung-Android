package com.example.trnberechnung.mapplanning

import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Test

class RouteMetricsCalculatorTest {
    @Test
    fun `metrics use speed Berlin time and exact diesel factor`() {
        val departure = ZonedDateTime.of(2026, 7, 29, 13, 35, 0, 0, ZoneOffset.UTC)
        val boatSettings = BoatSettings(
            speedKnots = 6.0,
            dieselLitersPerNm = 0.35,
            draftMeters = 1.0
        )

        val metrics =
            RouteMetricsCalculator.fromDistance(
                distanceNm = 25.5,
                departure = departure,
                boatSettings = boatSettings,
                worstClearanceMeters = 1.24,
            )

        metrics.travelTime shouldBe Duration.ofMinutes(255)
        metrics.arrival.zone shouldBe MAP_PLANNING_ZONE_ID
        metrics.arrival.hour shouldBe 19
        metrics.arrival.minute shouldBe 50
        metrics.dieselLiters shouldBe (8.925 plusOrMinus 0.000_001)
        metrics.dieselReserveLiters shouldBe (1.785 plusOrMinus 0.000_001)
        metrics.totalDieselLiters shouldBe (10.71 plusOrMinus 0.000_001)
        metrics.worstUnderKeelClearanceMeters shouldBe 1.24
    }

    @Test
    fun `haversine distance is calculated across every geometry leg`() = kotlinx.coroutines.test.runTest {
        val geometry =
            listOf(
                GeoPoint(53.3421, 7.1852),
                GeoPoint(53.5000, 7.1000),
                GeoPoint(53.6722, 6.9982),
            )
        val boatSettings = BoatSettings(speedKnots = 6.0)

        val metrics = RouteMetricsCalculator.calculate(
            routeGeometry = geometry,
            departure =
                ZonedDateTime.parse(
                    "2026-07-29T13:35:00+02:00[Europe/Berlin]",
                ),
            boatSettings = boatSettings,
        )

        (metrics?.distanceNm ?: 0.0) shouldBe (20.75 plusOrMinus 0.5)
    }

    @Test
    fun `vector math sog calculation with current`() {
        val stw = 6.0
        val cog = 90.0

        // Tailwind current
        val current = CurrentVector(90.0, 2.0)
        val result = VectorMath.calculateSogAndHeading(stw, cog, current)

        result.sogKnots shouldBe (8.0 plusOrMinus 0.1)
        result.headingDegrees shouldBe (90.0 plusOrMinus 0.1)

        // Cross current
        val crossCurrent = CurrentVector(0.0, 2.0)
        val crossResult = VectorMath.calculateSogAndHeading(stw, cog, crossCurrent)

        // STW=6, Cog=90, Cur=2@0. curX=2, curY=0. trackX=0, trackY=1.
        // sog^2 - 2*sog*(0*2 + 1*0) + 2^2 - 6^2 = 0
        // sog^2 + 4 - 36 = 0 => sog^2 = 32 => sog = sqrt(32) approx 5.65
        crossResult.sogKnots shouldBe (5.65 plusOrMinus 0.1)
        crossResult.headingDegrees shouldBe (109.47 plusOrMinus 0.1)
    }

    @Test
    fun `route metrics with tailwind current increases sog`() = kotlinx.coroutines.test.runTest {
        val geometry = listOf(
            GeoPoint(53.0, 7.0),
            GeoPoint(53.0, 7.1) // ~3.6nm East
        )
        val departure = ZonedDateTime.of(2026, 7, 29, 12, 0, 0, 0, ZoneOffset.UTC)
        val boatSettings = BoatSettings(speedKnots = 6.0)

        // Mock provider always returns 2 knots East
        val tailwindProvider = object : CurrentVectorProvider {
            override suspend fun getCurrentVector(point: GeoPoint, time: ZonedDateTime) =
                CurrentVector(90.0, 2.0)
        }

        val metricsNoCurrent = RouteMetricsCalculator.calculate(
            routeGeometry = geometry,
            departure = departure,
            boatSettings = boatSettings,
            currentProvider = null
        )

        val metricsWithCurrent = RouteMetricsCalculator.calculate(
            routeGeometry = geometry,
            departure = departure,
            boatSettings = boatSettings,
            currentProvider = tailwindProvider
        )

        // With 6kts STW and 2kts tailwind, SOG should be 8kts
        // Travel time should be reduced by 25% (6/8 = 0.75)
        val timeNoCurrent = metricsNoCurrent?.travelTime?.toMinutes() ?: 0L
        val timeWithCurrent = metricsWithCurrent?.travelTime?.toMinutes() ?: 0L

        timeWithCurrent.toDouble() shouldBe (timeNoCurrent.toDouble() * 0.75 plusOrMinus 1.0)
    }

    @Test
    fun `diesel consumption uses boat-specific rates`() {
        val boatSettings = BoatSettings(
            dieselLitersPerNm = 0.5,
            speedKnots = 10.0
        )
        val metrics = RouteMetricsCalculator.fromDistance(
            distanceNm = 100.0,
            departure = ZonedDateTime.now(),
            boatSettings = boatSettings
        )

        metrics.dieselLiters shouldBe (50.0 plusOrMinus 0.000_001)
        metrics.dieselReserveLiters shouldBe (10.0 plusOrMinus 0.000_001) // 20% of 50
        metrics.totalDieselLiters shouldBe (60.0 plusOrMinus 0.000_001)
    }
}
