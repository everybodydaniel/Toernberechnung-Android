package com.example.trnberechnung.ui.map

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.junit.Test

class MapWarningOverlayTest {
    @Test
    fun `adapter rejects missing or invalid coordinates instead of estimating a position`() {
        mapWarningOverlayOrNull(
            id = "bsh:1",
            title = "Sperrgebiet",
            summary = "Amtliche Meldung",
            geometryType = MapWarningGeometryType.POINT,
            coordinates = null,
        ).shouldBeNull()

        mapWarningOverlayOrNull(
            id = "bsh:1",
            title = "Sperrgebiet",
            summary = "Amtliche Meldung",
            geometryType = MapWarningGeometryType.POINT,
            coordinates = listOf(MapWarningCoordinate(latitude = 95.0, longitude = 7.2)),
        ).shouldBeNull()
    }

    @Test
    fun `adapter keeps an explicit point unchanged`() {
        val coordinate = MapWarningCoordinate(latitude = 53.706, longitude = 7.154)

        val overlay =
            mapWarningOverlayOrNull(
                id = " elwis:42 ",
                title = " Verlegte Tonne ",
                summary = " Position amtlich ver\u00f6ffentlicht. ",
                geometryType = MapWarningGeometryType.POINT,
                coordinates = listOf(coordinate),
            )

        requireNotNull(overlay)
        overlay.id shouldBe "elwis:42"
        overlay.title shouldBe "Verlegte Tonne"
        overlay.summary shouldBe "Position amtlich ver\u00f6ffentlicht."
        overlay.coordinates.shouldContainExactly(coordinate)
    }

    @Test
    fun `multiple official points remain multipoint and are never inferred as a line`() {
        val coordinates =
            listOf(
                MapWarningCoordinate(latitude = 53.70, longitude = 7.10),
                MapWarningCoordinate(latitude = 53.74, longitude = 7.18),
            )

        val multiPoint =
            mapWarningOverlayOrNull(
                id = "elwis:multi",
                title = "Mehrere Positionen",
                summary = "",
                geometryType = MapWarningGeometryType.MULTI_POINT,
                coordinates = coordinates,
            )

        requireNotNull(multiPoint)
        multiPoint.geometryType shouldBe MapWarningGeometryType.MULTI_POINT
        multiPoint.coordinates.shouldContainExactly(coordinates)
        mapWarningOverlayOrNull(
            id = "elwis:point",
            title = "Nicht eindeutig",
            summary = "",
            geometryType = MapWarningGeometryType.POINT,
            coordinates = coordinates,
        ).shouldBeNull()
    }

    @Test
    fun `line and area require their explicit geometry and minimum distinct vertices`() {
        val twoPoints =
            listOf(
                MapWarningCoordinate(latitude = 53.70, longitude = 7.10),
                MapWarningCoordinate(latitude = 53.72, longitude = 7.16),
            )
        val threePoints = twoPoints + MapWarningCoordinate(latitude = 53.68, longitude = 7.20)

        mapWarningOverlayOrNull(
            id = "elwis:line",
            title = "Fahrwasser",
            summary = "",
            geometryType = MapWarningGeometryType.LINE,
            coordinates = twoPoints,
        )?.geometryType shouldBe MapWarningGeometryType.LINE
        mapWarningOverlayOrNull(
            id = "elwis:area",
            title = "Sperrfl\u00e4che",
            summary = "",
            geometryType = MapWarningGeometryType.AREA,
            coordinates = threePoints,
        )?.geometryType shouldBe MapWarningGeometryType.AREA
        mapWarningOverlayOrNull(
            id = "elwis:not-an-area",
            title = "Zu wenig Punkte",
            summary = "",
            geometryType = MapWarningGeometryType.AREA,
            coordinates = twoPoints,
        ).shouldBeNull()
    }

    @Test
    fun `focus action uses only the validated coordinates of the requested warning`() {
        val expected =
            mapWarningOverlayOrNull(
                id = "bsh:focus",
                title = "Gefahrenstelle",
                summary = "Kurztext",
                geometryType = MapWarningGeometryType.MULTI_POINT,
                coordinates =
                    listOf(
                        MapWarningCoordinate(latitude = 53.8, longitude = 7.3),
                        MapWarningCoordinate(latitude = 53.9, longitude = 7.4),
                    ),
            )
        requireNotNull(expected)

        val action = mapWarningFocusActionOrNull(listOf(expected), "bsh:focus")

        requireNotNull(action)
        action.overlay shouldBe expected
        action.overlay.coordinates.shouldContainExactly(expected.coordinates)
    }

    @Test
    fun `focus and details actions stay absent without a matching renderable warning`() {
        mapWarningFocusActionOrNull(emptyList(), null).shouldBeNull()
        mapWarningFocusActionOrNull(emptyList(), "bsh:missing").shouldBeNull()
        mapWarningDetailsActionOrNull(emptyList(), "bsh:missing").shouldBeNull()
    }

    @Test
    fun `details action returns the stable id used by Zur Meldung`() {
        val overlay =
            mapWarningOverlayOrNull(
                id = "elwis:details",
                title = "Seezeichen",
                summary = "Kurztext",
                geometryType = MapWarningGeometryType.POINT,
                coordinates = listOf(MapWarningCoordinate(latitude = 53.71, longitude = 7.15)),
            )
        requireNotNull(overlay)

        mapWarningDetailsActionOrNull(listOf(overlay), overlay.id)?.warningId shouldBe overlay.id
    }

    @Test
    fun `focus gate processes a navigation request once and accepts it again after reset`() {
        val initial = MapWarningFocusGate()

        initial.canProcess("elwis:focus") shouldBe true
        val consumed = initial.markProcessed("elwis:focus")
        consumed.canProcess("elwis:focus") shouldBe false
        consumed.canProcess("elwis:other") shouldBe true
        consumed.reset().canProcess("elwis:focus") shouldBe true
    }
}
