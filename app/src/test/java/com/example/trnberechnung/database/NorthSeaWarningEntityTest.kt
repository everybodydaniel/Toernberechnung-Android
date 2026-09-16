package com.example.trnberechnung.database

import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialLinkKind
import com.example.trnberechnung.warnings.WarningCategory
import com.example.trnberechnung.warnings.WarningIdentity
import com.example.trnberechnung.warnings.WarningLifecycle
import com.example.trnberechnung.warnings.WarningSourceId
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NorthSeaWarningEntityTest {
    private val today = LocalDate.of(2026, 9, 16)
    private val cachedAt = Instant.parse("2026-09-16T10:00:00Z")

    @Test
    fun `persisted seen revision survives restart and a content update becomes unseen`() {
        val first = warning("Erste amtliche Fassung")
        val unseen = first.toEntity(cachedAt = cachedAt, previousSeenRevision = null)
        assertTrue(unseen.isUnseenOn(today))

        val seen = unseen.copy(seenRevision = unseen.contentRevision)
        assertFalse(seen.isUnseenOn(today))

        val updated =
            warning("Amtlich aktualisierte Fassung").toEntity(
                cachedAt = cachedAt.plusSeconds(60),
                previousSeenRevision = seen.seenRevision,
            )
        assertTrue(updated.isUnseenOn(today))
    }

    @Test
    fun `expired or withdrawn persisted entries never create a badge`() {
        val expiredByDate =
            warning("Abgelaufen").copy(validUntil = today.minusDays(1)).withCalculatedRevision()
        val withdrawn =
            warning("Aufgehoben").copy(lifecycle = WarningLifecycle.WITHDRAWN).withCalculatedRevision()

        assertFalse(expiredByDate.toEntity(cachedAt, null).isUnseenOn(today))
        assertFalse(withdrawn.toEntity(cachedAt, null).isUnseenOn(today))
    }

    private fun warning(text: String): NorthSeaWarning =
        NorthSeaWarning(
            id = "elwis:4711",
            officialId = "4711",
            reference = "BfS 4711/26",
            title = "Tonne verlegt",
            fullText = text,
            source = WarningSourceId.ELWIS,
            publisher = "WSA Ems-Nordsee",
            category = WarningCategory.NOTICE,
            publishedDate = today,
            validFrom = today,
            validUntil = today.plusDays(7),
            area = "Außenems",
            geometry = null,
            sourceUrl = "https://www.elwis.de/DE/dynamisch/Bfs/bfsMeldung:4711:elwis_bfs_showBfs",
            linkKind = OfficialLinkKind.WEB,
            lastUpdatedAt = cachedAt,
            lifecycle = WarningLifecycle.ACTIVE,
            isComplete = true,
            contentRevision = "",
        ).withCalculatedRevision()

    private fun NorthSeaWarning.withCalculatedRevision(): NorthSeaWarning =
        copy(contentRevision = WarningIdentity.contentRevision(this))
}
