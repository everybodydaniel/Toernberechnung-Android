package com.example.trnberechnung.ui.map

import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

internal class MapBottomActionsLayoutTest {
    @ParameterizedTest(name = "width={0}, active={1} -> {2}")
    @MethodSource("layoutCases")
    fun `compact map actions use the available width without overlap`(
        availableWidthDp: Float,
        activeVoyage: Boolean,
        expected: MapBottomActionsLayout,
    ) {
        assertEquals(
            expected,
            mapBottomActionsLayout(
                availableWidthDp = availableWidthDp,
                showActiveVoyage = activeVoyage,
            ),
        )
    }

    @Test
    fun `expanded chat starts below planner on phone and tablet`() {
        assertEquals(184.dp, nautiExpandedTopPadding(topOverlayClearance = 88.dp))
        assertEquals(224.dp, nautiExpandedTopPadding(topOverlayClearance = 128.dp))
    }

    @Test
    fun `expanded chat clears bottom navigation and open keyboard`() {
        assertEquals(
            116.dp,
            nautiExpandedBottomPadding(
                bottomOverlayClearance = 112.dp,
                imeBottomPadding = 0.dp,
            ),
        )
        assertEquals(
            328.dp,
            nautiExpandedBottomPadding(
                bottomOverlayClearance = 112.dp,
                imeBottomPadding = 320.dp,
            ),
        )
    }

    companion object {
        @JvmStatic
        fun layoutCases(): Stream<Arguments> =
            Stream.of(
                // Phone portrait: the two full-width touch targets are stacked.
                Arguments.of(328f, true, MapBottomActionsLayout.STACKED),
                // Narrow tablet content remains readable by stacking.
                Arguments.of(559f, true, MapBottomActionsLayout.STACKED),
                // Tablet portrait and landscape, as well as wide phone landscape, use one row.
                Arguments.of(560f, true, MapBottomActionsLayout.SIDE_BY_SIDE),
                Arguments.of(696f, true, MapBottomActionsLayout.SIDE_BY_SIDE),
                // Without an active voyage there is only the Nauti action at every width.
                Arguments.of(328f, false, MapBottomActionsLayout.SINGLE),
                Arguments.of(696f, false, MapBottomActionsLayout.SINGLE),
            )
    }
}
