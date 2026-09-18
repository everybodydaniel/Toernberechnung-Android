package com.example.trnberechnung.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "planner_events")
data class PlannerEventEntity(
    @PrimaryKey val id: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val title: String,
    val description: String,
    val startTime: String? = null,
    val endTime: String? = null,
    val location: String? = null,
    val category: String = "Allgemein",
    val participantIds: List<Int> = emptyList(),
)
