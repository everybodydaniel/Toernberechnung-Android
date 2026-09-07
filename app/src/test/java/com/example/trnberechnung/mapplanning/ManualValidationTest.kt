package com.example.trnberechnung.mapplanning

import com.example.trnberechnung.model.TideEvent
import com.example.trnberechnung.model.TideStationData
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Locale

class ManualValidationTest {

    @Test
    fun `validate Norderney to Juist window accuracy`() = runTest {
        // Referenzdaten für einen Tag (Beispiel: 2026-07-29)
        // HW Norderney ca. 18:00 (3.1m), NW ca. 11:45 (1.1m)
        val station = TideStationData(
            area = "Norderney",
            region = "Nordsee",
            latitude = 53.70,
            longitude = 7.15,
            waterLevel = null,
            meanHighWater = 2.8,
            meanLowWater = 0.5,
            gaugeLabel = "Norderney",
            forecastTimestamp = "2026-07-29T13:00:00Z",
            events = listOf(
                TideEvent("2026-07-29T05:30:00+02:00", "HW", 2.9),
                TideEvent("2026-07-29T11:45:00+02:00", "NW", 1.1),
                TideEvent("2026-07-29T18:00:00+02:00", "HW", 3.1),
                TideEvent("2026-07-30T00:15:00+02:00", "NW", 1.0)
            )
        )

        val provider = AndroidRouteAssessmentProvider(
            tideStationProvider = { listOf(station) },
            chartDepthProvider = { -0.5 }, // 0.5m über Kartennull (Trockenfallend)
            fairwayRouteResolver = null
        )

        val boatSettings = BoatSettings(
            draftMeters = 1.2,
            safetyMarginMeters = 0.3,
            speedKnots = 5.0
        )

        val geometry = listOf(
            GeoPoint(53.70, 7.15), // Norderney
            GeoPoint(53.68, 7.00)  // Juist (über Watt)
        )

        println("\n=== VALIDIERUNG: Norderney -> Juist (Watt) ===")
        println("Boot: Tiefgang=${boatSettings.draftMeters}m, Reserve=${boatSettings.safetyMarginMeters}m")
        println("Engstelle: -0.5m (Kartennull) -> Erf. Wasserstand (Tide) = 1.2 + 0.3 + 0.5 = 2.0m")
        println("--------------------------------------------------------------------------------")
        println("Abfahrt | Ankunft Engst. | Tide (m) | Tiefe (m) | WuK (m) | Status")
        println("--------------------------------------------------------------------------------")

        val day = ZonedDateTime.parse("2026-07-29T08:00:00+02:00[Europe/Berlin]")
        val formatter = DateTimeFormatter.ofPattern("HH:mm")

        for (i in 0..60 step 2) { // Alle 20 Minuten über 10 Stunden
            val departure = day.plusMinutes(i * 10L)
            val metrics = RouteMetricsCalculator.calculate(geometry, departure, boatSettings) ?: continue

            val assessment = provider.assess(
                RouteAssessmentInput(
                    request = RoutePlanningRequest(
                        HarbourId.NORDERNEY_HARBOR, HarbourId.JUIST_HARBOR, emptyList(), departure, boatSettings
                    ),
                    routeGeometry = geometry,
                    routeMetrics = metrics
                )
            )

            val critical = assessment.criticalSample
            val arrival = critical?.arrivalTime?.format(formatter) ?: "??"
            // Wassertiefe = ChartDepth (-0.5) + Tide
            // WuK = Wassertiefe - Draft
            // => Tide = WuK + Draft - ChartDepth
            val wuk = critical?.clearanceMeters ?: -9.9
            val tide = wuk + boatSettings.draftMeters - (-0.5)
            val totalDepth = wuk + boatSettings.draftMeters
            val status = if (wuk >= boatSettings.safetyMarginMeters) "OK" else "ZU FLACH"

            println(
                "${departure.format(formatter)}   | $arrival       | ${String.format(Locale.US, "%.2f", tide)}     | ${String.format(Locale.US, "%.2f", totalDepth)}     | ${String.format(Locale.US, "%.2f", wuk)}    | $status"
            )
        }
        println("--------------------------------------------------------------------------------")
    }
}
