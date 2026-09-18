package com.example.trnberechnung.model

import java.time.LocalDate
import java.util.UUID

/**
 * Datenmodell für einen Planungstermin im Törnkalender.
 * Wird vorerst als In-Memory-Mock verwendet.
 */
data class PlannerEvent(
    val id: String = UUID.randomUUID().toString(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val title: String,
    val description: String = "",
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val category: String = "Allgemein",
    /** Stable local [CrewMember.id] values; names are resolved only for display and export. */
    val participantIds: List<Int> = emptyList(),
)
