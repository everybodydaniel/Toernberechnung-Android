package com.example.trnberechnung.mapplanning

import com.example.trnberechnung.routing.v2.NauticalRouterV2
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RouteGeometryProviderTest {
    @Test
    fun `missing A star path falls back to direct line`() =
        runTest {
            val from = HarbourCatalog[HarbourId.EMDEN_HARBOR]
            val to = HarbourCatalog[HarbourId.JUIST_HARBOR]
            val provider =
                NauticalRouterV2GeometryProvider { _, _ ->
                    NauticalRouterV2.RouteResult.Incomplete(
                        reason = NauticalRouterV2.FailureReason.NO_SEA_PATH,
                        message = "Kein Seeweg",
                    )
                }

            val result =
                provider.calculate(listOf(from, to))

            result shouldBe RouteGeometryResult.Success(
                listOf(from.coordinate, to.coordinate)
            )
        }
}
