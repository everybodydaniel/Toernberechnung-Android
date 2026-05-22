package com.example.trnberechnung.logic

import android.util.Log
import com.example.trnberechnung.model.SegmentType
import com.example.trnberechnung.model.TideEvent
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng
import java.time.LocalDateTime

class NauticalRouterTest {

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.e(any(), any()) } returns 0

        // Mocking LatLng if the MapLibre SDK is not available in the classpath or behaves as a stub
        // However, usually it works as a value object. If it fails, we mock it.
    }

    @Test
    fun `classifyDepth returns SAFE when depth is enough`() {
        val type = NauticalRouter.classifyDepth(depth = 3.0, draft = 1.5, margin = 0.5)
        type shouldBe SegmentType.SAFE
    }

    @Test
    fun `classifyDepth returns CRITICAL when depth is tight`() {
        val type = NauticalRouter.classifyDepth(depth = 1.8, draft = 1.5, margin = 0.5)
        type shouldBe SegmentType.CRITICAL
    }

    @Test
    fun `classifyDepth returns NO_GO when depth is too low`() {
        val type = NauticalRouter.classifyDepth(depth = 1.2, draft = 1.5, margin = 0.5)
        type shouldBe SegmentType.NO_GO
    }

    @Test
    fun `calculateTideOffset interpolates correctly`() {
        // High tide at 12:00 (3.0m), Low tide at 18:00 (0.0m)
        val events = listOf(
            TideEvent("2024-01-01 12:00:00", "High", 3.0),
            TideEvent("2024-01-01 18:00:00", "Low", 0.0)
        )

        // At 15:00 (middle), it should be 1.5m (Rule of Twelfths)
        val midTime = LocalDateTime.of(2024, 1, 1, 15, 0)
        val offset = NauticalRouter.calculateTideOffset(midTime, events)

        // Use proper Kotest double matcher
        offset shouldBe (1.5 plusOrMinus 0.1)
    }

    @Test
    fun `calculateRoute finds a path from Emden to Norderney`() {
        // Using real coordinates from the NauticalRouter's baseWaypoints
        val start = LatLng(53.3382, 7.1945)
        val end = LatLng(53.7012, 7.1585)

        val route = NauticalRouter.calculateRoute(start, end)

        route shouldHaveAtLeastSize 2
        route.first().latitude shouldBe (start.latitude plusOrMinus 0.001)
        route.last().latitude shouldBe (end.latitude plusOrMinus 0.001)
    }

    @Test
    fun `calculateSegmentedRoute detects dangerous segments`() {
        val start = LatLng(53.6265, 7.1615) // Norddeich
        val end = LatLng(53.6732, 7.0015)   // Juist

        // Low draft (0.5m) -> should be SAFE in most fairways
        val safeSegments = NauticalRouter.calculateSegmentedRoute(start, end, draft = 0.5, margin = 0.2)
        safeSegments.any { it.type == SegmentType.SAFE } shouldBe true

        // Extremely high draft (10.0m) -> should be NO_GO in the Wadden Sea
        val dangerSegments = NauticalRouter.calculateSegmentedRoute(start, end, draft = 10.0, margin = 0.5)
        dangerSegments.all { it.type == SegmentType.NO_GO } shouldBe true
    }
}
