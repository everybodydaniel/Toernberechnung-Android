package com.example.trnberechnung.logic

import com.example.trnberechnung.model.TideEvent
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Parsing of BSH tide timestamps.
 *
 * The BSH forecast mixes formats: `yyyy-MM-dd HH:mm:ss`, ISO with a `T`, and either flavour with a
 * trailing `Z` or a `+02:00` / `+02` offset. This used to be re-implemented at every call site
 * (`TideViewModel.updateTideEvents`, `TideContent` in the Revier screen), which is how a "next high
 * water" could differ between two screens showing the same station. One parser, one behaviour.
 *
 * Explicit offsets are honoured. Dropping a `Z`/offset and then assigning Europe/Berlin moves a
 * tide by one or two hours, which directly produces incorrect WuK and passage windows.
 */
object TideTimes {
    private val SPACE_SEPARATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val BERLIN_ZONE = ZoneId.of("Europe/Berlin")

    /** Returns the event time, or `null` if the timestamp cannot be read at all. */
    fun parse(timestamp: String?): LocalDateTime? = parseZDT(timestamp)?.toLocalDateTime()

    /** Returns the event time as ZonedDateTime in Europe/Berlin, or `null`. */
    fun parseZDT(timestamp: String?): ZonedDateTime? {
        val raw = timestamp?.trim()?.takeIf(String::isNotEmpty) ?: return null
        val isoCandidate = raw.replace(' ', 'T')

        return runCatching {
            OffsetDateTime.parse(isoCandidate).atZoneSameInstant(BERLIN_ZONE)
        }.getOrNull()
            ?: runCatching {
                ZonedDateTime.parse(isoCandidate).withZoneSameInstant(BERLIN_ZONE)
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw.replace('T', ' '), SPACE_SEPARATED).atZone(BERLIN_ZONE)
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(isoCandidate).atZone(BERLIN_ZONE)
            }.getOrNull()
    }

    /** Events paired with their parsed time, unreadable ones dropped, oldest first. */
    fun sortedByTime(events: List<TideEvent>): List<Pair<TideEvent, LocalDateTime>> =
        events
            .mapNotNull { event -> parse(event.timestamp)?.let { event to it } }
            .sortedBy { it.second }
}
