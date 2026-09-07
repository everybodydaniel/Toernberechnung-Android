package com.example.trnberechnung.routing.v2

import com.example.trnberechnung.mapplanning.GeoPoint
import com.example.trnberechnung.mapplanning.HarbourCatalog
import com.example.trnberechnung.mapplanning.HarbourId
import com.example.trnberechnung.mapplanning.NauticalRouterV2GeometryProvider
import com.example.trnberechnung.mapplanning.RouteGeometryResult
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.maplibre.android.geometry.LatLng

@OptIn(ExperimentalCoroutinesApi::class)
class NauticalRoutingValidationTest {

    @Before
    fun setup() {
        mockkObject(SeaMask)
        every { SeaMask.ready } returns true
        // Simulierte Tiefe von 5m überall, damit A* einen Weg findet
        every { SeaMask.depthAtLatLng(any(), any()) } returns 5.0
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `routing between Juist and Norderney should succeed using bridge rules`() = runTest {
        val provider = NauticalRouterV2GeometryProvider()
        val juist = HarbourCatalog[HarbourId.JUIST_HARBOR]
        val norderney = HarbourCatalog[HarbourId.NORDERNEY_HARBOR]

        val result = provider.calculate(listOf(juist, norderney))

        if (result is RouteGeometryResult.Incomplete) {
            throw AssertionError("Routing failed: ${result.reason}")
        }

        // Erfolg prüfen
        val points = (result as RouteGeometryResult.Success).points

        // Wir loggen die Punkte manuell, da stdout in Gradle-Tests oft versteckt ist
        if (points.size < 2) {
             throw AssertionError("Route has less than 2 points")
        }

        // Juist Bridge: (53.67, 6.9982), (53.662, 7.02)
        // Norderney Bridge: (53.7024, 7.1637), (53.7000, 7.1637), (53.6930, 7.1700)

        val containsJuistBridge = points.any { it.latitude == 53.662 && it.longitude == 7.02 }
        val containsNorderneyBridge = points.any { it.latitude == 53.6930 && it.longitude == 7.1700 }

        if (!containsJuistBridge || !containsNorderneyBridge) {
            val details = points.joinToString("\n") { "${it.latitude}, ${it.longitude}" }
            throw AssertionError("Missing bridge points. JuistBridge=$containsJuistBridge, NorderneyBridge=$containsNorderneyBridge\nRoute:\n$details")
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)
