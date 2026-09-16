package com.example.trnberechnung.repository

import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialLinkKind
import com.example.trnberechnung.warnings.WarningCategory
import com.example.trnberechnung.warnings.WarningCoordinate
import com.example.trnberechnung.warnings.WarningGeometry
import com.example.trnberechnung.warnings.WarningGeometryType
import com.example.trnberechnung.warnings.WarningIdentity
import com.example.trnberechnung.warnings.WarningLifecycle
import com.example.trnberechnung.warnings.WarningSourceId
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NorthSeaWarningCacheMergeTest {
    @Test
    fun `unchanged list summary preserves cached details and revision`() {
        val incoming = summary(updatedAt = Instant.parse("2026-09-16T12:00:00Z"))
        val cached = cachedDetail()

        val result = preserveCachedDetailsWhenSummaryIsUnchanged(incoming, cached)

        assertTrue(result.isComplete)
        assertEquals(cached.fullText, result.fullText)
        assertEquals(cached.category, result.category)
        assertEquals(cached.geometry, result.geometry)
        assertEquals(cached.contentRevision, result.contentRevision)
        assertEquals(incoming.lastUpdatedAt, result.lastUpdatedAt)
    }

    @Test
    fun `changed list summary discards cached details and uses incoming revision`() {
        val incoming =
            summary(updatedAt = Instant.parse("2026-09-16T12:00:00Z"))
                .copy(
                    reference = "BfS 4711/26 (1. Berichtigung)",
                    contentRevision = "",
                ).withCalculatedRevision()
        val cached = cachedDetail()

        val result = preserveCachedDetailsWhenSummaryIsUnchanged(incoming, cached)

        assertFalse(result.isComplete)
        assertEquals("", result.fullText)
        assertNull(result.geometry)
        assertEquals(incoming.contentRevision, result.contentRevision)
        assertNotEquals(cached.contentRevision, result.contentRevision)
    }

    private fun summary(updatedAt: Instant): NorthSeaWarning =
        NorthSeaWarning(
            id = "elwis:4711",
            officialId = "4711",
            reference = "BfS 4711/26",
            title = "Sperrgebiet Außenems",
            fullText = "",
            source = WarningSourceId.ELWIS,
            publisher = "WSA Ems-Nordsee",
            category = WarningCategory.NOTICE,
            publishedDate = LocalDate.of(2026, 9, 16),
            validFrom = LocalDate.of(2026, 9, 16),
            validUntil = LocalDate.of(2026, 9, 20),
            area = "Deutschland. Nordsee",
            geometry = null,
            sourceUrl = "https://www.elwis.de/DE/dynamisch/Bfs/bfsMeldung:4711:elwis_bfs_showBfs",
            linkKind = OfficialLinkKind.WEB,
            lastUpdatedAt = updatedAt,
            lifecycle = WarningLifecycle.ACTIVE,
            isComplete = false,
            contentRevision = "",
        ).withCalculatedRevision()

    private fun cachedDetail(): NorthSeaWarning =
        summary(updatedAt = Instant.parse("2026-09-16T10:00:00Z"))
            .copy(
                fullText = "Vor dem Sperrgebiet wird ausdrücklich gewarnt.",
                category = WarningCategory.WARNING,
                geometry =
                    WarningGeometry.validated(
                        type = WarningGeometryType.POINT,
                        coordinates = listOf(WarningCoordinate(latitude = 53.5, longitude = 7.25)),
                    ),
                isComplete = true,
                contentRevision = "",
            ).withCalculatedRevision()

    private fun NorthSeaWarning.withCalculatedRevision(): NorthSeaWarning =
        copy(contentRevision = WarningIdentity.contentRevision(this))
}
