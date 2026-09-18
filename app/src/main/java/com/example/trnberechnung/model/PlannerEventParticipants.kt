package com.example.trnberechnung.model

/** Keeps only stable, valid member IDs when a participant selection is saved. */
fun PlannerEvent.withParticipantIds(participantIds: Iterable<Int>): PlannerEvent =
    copy(participantIds = participantIds.filter { it > 0 }.distinct().sorted())

/** Resolves display names at the UI/export boundary, so technical IDs never leave the app. */
fun PlannerEvent.participantDisplayNames(crewMembers: List<CrewMember>): List<String> {
    val namesById = crewMembers.associate { it.id to it.name.trim() }
    return participantIds.mapNotNull { id -> namesById[id]?.takeIf { it.isNotEmpty() } }
}
