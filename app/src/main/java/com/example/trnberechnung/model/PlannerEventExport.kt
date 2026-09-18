package com.example.trnberechnung.model

import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val plannerDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMANY)

/** A calendar entry derived from the planner event, without exposing planner-specific metadata. */
data class PlannerCalendarData(
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val allDay: Boolean,
    val timeZoneId: String,
    val location: String?,
    val description: String?,
)

enum class PlannerExternalActionStatus {
    READY,
    INCOMPLETE_EVENT,
    NO_TARGET_APP,
}

/** Validates that this planner entry can be safely handed to another app. */
fun PlannerEvent.isReadyForExternalAction(): Boolean {
    if (title.isBlank() || endDate.isBefore(startDate)) return false

    val parsedStart = startTime?.takeIf { it.isNotBlank() }?.let(::parsePlannerTime)
    val parsedEnd = endTime?.takeIf { it.isNotBlank() }?.let(::parsePlannerTime)
    return (startTime.isNullOrBlank() || parsedStart != null) &&
        (endTime.isNullOrBlank() || parsedEnd != null) &&
        !(startTime.isNullOrBlank() && !endTime.isNullOrBlank())
}

fun plannerExternalActionStatus(
    event: PlannerEvent,
    hasTargetApp: Boolean,
): PlannerExternalActionStatus =
    when {
        !event.isReadyForExternalAction() -> PlannerExternalActionStatus.INCOMPLETE_EVENT
        !hasTargetApp -> PlannerExternalActionStatus.NO_TARGET_APP
        else -> PlannerExternalActionStatus.READY
    }

/** Formats only the information that belongs to the appointment for Android's share sheet. */
fun PlannerEvent.toShareText(participantNames: List<String> = emptyList()): String {
    require(isReadyForExternalAction()) { "Unvollständiger Termin kann nicht geteilt werden." }
    return buildList {
        add("Termin: ${title.trim()}")
        add(
            "Datum: " +
                if (startDate == endDate) startDate.format(plannerDateFormatter) else {
                    "${startDate.format(plannerDateFormatter)} bis ${endDate.format(plannerDateFormatter)}"
                },
        )
        startTime?.takeIf { it.isNotBlank() }?.let { start ->
            val end = endTime?.takeIf { it.isNotBlank() }
            add("Zeit: $start${end?.let { " bis $it" }.orEmpty()} Uhr")
        }
        location?.trim()?.takeIf { it.isNotEmpty() }?.let { add("Ort: $it") }
        description.trim().takeIf { it.isNotEmpty() }?.let { add("Hinweis: $it") }
        participantNames.cleanParticipantNames().takeIf { it.isNotEmpty() }?.let {
            add("Dabei: ${it.joinToString(separator = ", ")}")
        }
    }.joinToString(separator = "\n")
}

/** Converts the existing planner dates and times to instants for the calendar insert intent. */
fun PlannerEvent.toCalendarData(
    zoneId: ZoneId,
    participantNames: List<String> = emptyList(),
): PlannerCalendarData? {
    if (!isReadyForExternalAction()) return null
    val start = startTime?.takeIf { it.isNotBlank() }?.let(::parsePlannerTime)
    val end = endTime?.takeIf { it.isNotBlank() }?.let(::parsePlannerTime)
    val allDay = start == null
    val startDateTime = LocalDateTime.of(startDate, start ?: LocalTime.MIDNIGHT)
    val endDateTime =
        if (allDay) {
            LocalDateTime.of(endDate.plusDays(1), LocalTime.MIDNIGHT)
        } else if (end != null) {
            LocalDateTime.of(endDate, end).let { candidate ->
                if (candidate.isAfter(startDateTime)) candidate else candidate.plusDays(1)
            }
        } else if (endDate.isAfter(startDate)) {
            LocalDateTime.of(endDate, start)
        } else {
            startDateTime.plusHours(DEFAULT_DURATION_HOURS)
        }
    return PlannerCalendarData(
        title = title.trim(),
        startEpochMillis = startDateTime.atZone(zoneId).toInstant().toEpochMilli(),
        endEpochMillis = endDateTime.atZone(zoneId).toInstant().toEpochMilli(),
        allDay = allDay,
        timeZoneId = zoneId.id,
        location = location?.trim()?.takeIf { it.isNotEmpty() },
        description = calendarDescription(participantNames),
    )
}

private fun parsePlannerTime(value: String): LocalTime? =
    runCatching { LocalTime.parse(value.trim(), DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()

private fun PlannerEvent.calendarDescription(participantNames: List<String>): String? =
    listOfNotNull(
        description.trim().takeIf { it.isNotEmpty() },
        participantNames.cleanParticipantNames()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(separator = ", ", prefix = "Dabei: "),
    ).joinToString(separator = "\n").takeIf { it.isNotEmpty() }

private fun List<String>.cleanParticipantNames(): List<String> =
    map { it.trim() }.filter { it.isNotEmpty() }.distinct()

private const val DEFAULT_DURATION_HOURS = 1L
