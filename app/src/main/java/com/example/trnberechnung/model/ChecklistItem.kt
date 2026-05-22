package com.example.trnberechnung.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents the type of a checklist item:
 * - CHECK: Simple checkbox (checked / unchecked)
 * - TEXT:  A text field where the user can enter a value or note
 */
enum class ChecklistItemType {
    CHECK,
    TEXT
}

/**
 * Room entity for a single checklist item belonging to a specific trip (törn).
 * Each trip gets its own set of checklist items, identified by [tripId].
 */
@Entity(tableName = "checklist_items")
data class ChecklistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,              // Links this item to a specific logbook entry / trip
    val category: String,         // e.g. "Sicherheit", "Technik", "Navigation"
    val label: String,            // Display label, e.g. "Einweisung der Crew"
    val type: String,             // "CHECK" or "TEXT" (stored as String for Room compatibility)
    val isChecked: Boolean = false,
    val textValue: String = "",
    val sortOrder: Int = 0        // For display ordering within a category
)
