package com.example.trnberechnung.model

import java.time.LocalDate
import java.time.ZoneId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlannerEventExportTest {
    private val zone = ZoneId.of("Europe/Berlin")

    @Test
    fun `share text contains only filled German appointment fields`() {
        val event = event(
            startTime = "09:30",
            endTime = "11:00",
            location = "Greetsiel",
            description = "Treffen am Steg",
        )

        assertEquals(
            "Termin: Ablegen\nDatum: 04.07.2026\nZeit: 09:30 bis 11:00 Uhr\n" +
                "Ort: Greetsiel\nHinweis: Treffen am Steg",
            event.toShareText(),
        )
    }

    @Test
    fun `share text omits absent optional fields`() {
        val text = event().toShareText()

        assertEquals("Termin: Ablegen\nDatum: 04.07.2026", text)
        assertFalse(text.contains("Ort:"))
        assertFalse(text.contains("Hinweis:"))
        assertFalse(text.contains("Zeit:"))
    }

    @Test
    fun `share text contains crew display names but never internal participant IDs`() {
        val text = event().withParticipantIds(listOf(42, 99)).toShareText(listOf("Anna", "Bennet"))

        assertTrue(text.contains("Dabei: Anna, Bennet"))
        assertFalse(text.contains("42"))
        assertFalse(text.contains("99"))
    }

    @Test
    fun `calendar data uses selected timezone and defaults a timed event to one hour`() {
        val completeEvent = event(
            startTime = "10:15",
            location = "Greetsiel",
            description = "Treffen am Steg",
        )
        val data = completeEvent.toCalendarData(zone)!!

        assertEquals("Europe/Berlin", data.timeZoneId)
        assertFalse(data.allDay)
        assertEquals(3_600_000L, data.endEpochMillis - data.startEpochMillis)
        assertEquals("Ablegen", data.title)
        assertEquals("Greetsiel", data.location)
        assertEquals("Treffen am Steg", data.description)
        assertEquals(
            PlannerExternalActionStatus.READY,
            plannerExternalActionStatus(completeEvent, hasTargetApp = true),
        )
    }

    @Test
    fun `calendar description appends selected crew names without creating guests`() {
        val data = event(description = "Treffen am Steg")
            .withParticipantIds(listOf(42))
            .toCalendarData(zone, listOf("Anna"))!!

        assertEquals("Treffen am Steg\nDabei: Anna", data.description)
        assertFalse(data.description!!.contains("42"))
    }

    @Test
    fun `calendar data carries an overnight appointment into the next day`() {
        val data = event(startTime = "23:30", endTime = "01:00").toCalendarData(zone)!!

        assertEquals(90 * 60 * 1000L, data.endEpochMillis - data.startEpochMillis)
    }

    @Test
    fun `event without a start time is exported as all day over its full date range`() {
        val data = event(endDate = LocalDate.of(2026, 7, 6)).toCalendarData(zone)!!

        assertTrue(data.allDay)
        assertEquals(3 * 24 * 60 * 60 * 1000L, data.endEpochMillis - data.startEpochMillis)
    }

    @Test
    fun `incomplete events cannot start external actions or create calendar data`() {
        val incomplete = event(title = "", startTime = "not a time")

        assertFalse(incomplete.isReadyForExternalAction())
        assertNull(incomplete.toCalendarData(zone))
        assertEquals(
            PlannerExternalActionStatus.INCOMPLETE_EVENT,
            plannerExternalActionStatus(incomplete, hasTargetApp = true),
        )
    }

    @Test
    fun `missing target app prevents external launch`() {
        assertEquals(
            PlannerExternalActionStatus.NO_TARGET_APP,
            plannerExternalActionStatus(event(), hasTargetApp = false),
        )
    }

    private fun event(
        title: String = "Ablegen",
        endDate: LocalDate = LocalDate.of(2026, 7, 4),
        startTime: String? = null,
        endTime: String? = null,
        location: String? = null,
        description: String = "",
    ) = PlannerEvent(
        startDate = LocalDate.of(2026, 7, 4),
        endDate = endDate,
        title = title,
        startTime = startTime,
        endTime = endTime,
        location = location,
        description = description,
    )
}
