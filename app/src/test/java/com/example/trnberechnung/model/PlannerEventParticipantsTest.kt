package com.example.trnberechnung.model

import com.example.trnberechnung.database.PlannerEventEntity
import com.example.trnberechnung.database.Converters
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlannerEventParticipantsTest {
    @Test
    fun `a single crew member is selected through its stable ID`() {
        val selected = event().withParticipantIds(listOf(7))

        assertEquals(listOf(7), selected.participantIds)
    }

    @Test
    fun `multiple crew members are selected without duplicates`() {
        val selected = event().withParticipantIds(listOf(9, 3, 9, -1))

        assertEquals(listOf(3, 9), selected.participantIds)
    }

    @Test
    fun `participants survive saving and loading an event`() {
        val reloaded = event().withParticipantIds(listOf(4, 8)).toEntity().toModel()

        assertEquals(listOf(4, 8), reloaded.participantIds)
    }

    @Test
    fun `participant IDs round trip through the Room storage converter`() {
        val converters = Converters()
        val stored = converters.participantIdsToStorage(listOf(8, 4, 8))

        assertEquals("4,8", stored)
        assertEquals(listOf(4, 8), converters.participantIdsFromStorage(stored))
    }

    @Test
    fun `editing an existing participant selection replaces the saved IDs`() {
        val edited = event().withParticipantIds(listOf(1, 2)).withParticipantIds(listOf(2, 3))

        assertEquals(listOf(2, 3), edited.participantIds)
    }

    @Test
    fun `an event without participants remains valid and persists`() {
        val reloaded = event().toEntity().toModel()

        assertTrue(reloaded.participantIds.isEmpty())
    }

    @Test
    fun `unavailable crew members are omitted while current names remain visible`() {
        val event = event().withParticipantIds(listOf(1, 99, 2))
        val crew = listOf(member(1, "Anna"), member(2, "Bennet"))

        assertEquals(listOf("Anna", "Bennet"), event.participantDisplayNames(crew))
    }

    @Test
    fun `old database records without participant data load as an empty selection`() {
        val legacyEntity =
            PlannerEventEntity(
                id = "legacy",
                startDate = LocalDate.of(2026, 7, 4),
                endDate = LocalDate.of(2026, 7, 4),
                title = "Bestehender Termin",
                description = "",
            )

        assertTrue(legacyEntity.toModel().participantIds.isEmpty())
    }

    private fun event() =
        PlannerEvent(
            id = "event-1",
            startDate = LocalDate.of(2026, 7, 4),
            endDate = LocalDate.of(2026, 7, 4),
            title = "Ablegen",
        )

    private fun member(id: Int, name: String) =
        CrewMember(
            id = id,
            name = name,
            rank = "Matrose",
            isOnBoard = true,
            medicalNote = "",
            emergencyPhone = "",
        )
}
