package com.example.trnberechnung.model

import org.junit.Test
import io.kotest.matchers.shouldBe
import io.kotest.matchers.booleans.shouldBeTrue

class ModelEntityTest {

    @Test
    fun `LogbookEntry construction and equality`() {
        val entry = LogbookEntry(
            id = 0, date = "2024-01-01", routeDesc = "Borkum-Juist",
            distance = "12nm", duration = "3h", status = "completed", details = "test"
        )
        entry.routeDesc shouldBe "Borkum-Juist"
        val copy = entry.copy()
        (entry == copy).shouldBeTrue()
    }

    @Test
    fun `CrewMember construction and equality`() {
        val member = CrewMember(
            id = 0, name = "Max", rank = "Skipper", isOnBoard = true,
            medicalNote = "", emergencyPhone = "0171"
        )
        member.name shouldBe "Max"
        member.isOnBoard.shouldBeTrue()
    }

    @Test
    fun `ChecklistItem construction and enum values`() {
        val item = ChecklistItem(
            tripId = 1, category = "Crew", label = "Einweisung",
            type = ChecklistItemType.CHECK.name, isChecked = false, textValue = "", sortOrder = 0
        )
        item.category shouldBe "Crew"
        item.type shouldBe ChecklistItemType.CHECK.name
    }

    @Test
    fun `TideEvent construction`() {
        val event = TideEvent(timestamp = "2024-01-01T12:00:00", type = "HW", value = 3.5)
        event.value shouldBe 3.5
    }
}
