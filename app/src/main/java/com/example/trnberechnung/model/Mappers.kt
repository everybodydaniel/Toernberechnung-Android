package com.example.trnberechnung.model

import com.example.trnberechnung.database.PlannerEventEntity

fun PlannerEventEntity.toModel(): PlannerEvent {
    fun formatTime(time: String?): String? {
        if (time == null || time.contains(":")) return time
        val seconds = time.toLongOrNull() ?: return time
        return try {
            java.time.LocalTime.ofSecondOfDay(seconds % 86400)
                .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            time
        }
    }

    return PlannerEvent(
        id = id,
        startDate = startDate,
        endDate = endDate,
        title = title,
        description = description,
        startTime = formatTime(startTime),
        endTime = formatTime(endTime),
        location = location,
        category = category,
        participantIds = participantIds,
    )
}

fun PlannerEvent.toEntity(): PlannerEventEntity {
    return PlannerEventEntity(
        id = id,
        startDate = startDate,
        endDate = endDate,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime,
        location = location,
        category = category,
        participantIds = participantIds,
    )
}
