package com.example.trnberechnung.warnings

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WarningModelsTest {
    @Test
    fun `official source id is preferred for a stable warning id`() {
        val id =
            WarningIdentity.stableId(
                source = WarningSourceId.ELWIS,
                officialId = " 4711 ",
                title = "Dieser Titel darf sich ändern",
                publishedDate = LocalDate.of(2026, 9, 16),
                area = "Nordsee",
            )

        assertEquals("elwis:4711", id)
    }

    @Test
    fun `fallback fingerprint normalizes immutable text but changes for another notice`() {
        val first =
            WarningIdentity.stableId(
                source = WarningSourceId.ELWIS,
                officialId = null,
                title = "  Tonne   verlegt ",
                publishedDate = LocalDate.of(2026, 9, 16),
                area = "Außenems",
            )
        val normalized =
            WarningIdentity.stableId(
                source = WarningSourceId.ELWIS,
                officialId = " ",
                title = "tonne verlegt",
                publishedDate = LocalDate.of(2026, 9, 16),
                area = "außenems",
            )
        val anotherDate =
            WarningIdentity.stableId(
                source = WarningSourceId.ELWIS,
                officialId = null,
                title = "Tonne verlegt",
                publishedDate = LocalDate.of(2026, 9, 17),
                area = "Außenems",
            )

        assertEquals(first, normalized)
        assertNotEquals(first, anotherDate)
        assertTrue(first.startsWith("elwis:sha256:"))
    }

    @Test
    fun `content update keeps id and changes revision`() {
        val original = warning(fullText = "Tonne liegt auf Position A")
        val update =
            original
                .copy(
                    fullText = "Tonne liegt nun auf Position B",
                    lastUpdatedAt = Instant.parse("2026-09-16T12:00:00Z"),
                    contentRevision = "",
                ).withCalculatedRevision()

        assertEquals(original.id, update.id)
        assertNotEquals(original.contentRevision, update.contentRevision)
        assertEquals(update, mergeAndDeduplicateWarnings(listOf(original, update)).single())
    }

    @Test
    fun `reference and publication date are part of the content revision`() {
        val original = warning()
        val changedReference =
            original
                .copy(
                    reference = "BfS 4711/26 (1. Berichtigung)",
                    contentRevision = "",
                ).withCalculatedRevision()
        val changedPublicationDate =
            original
                .copy(
                    publishedDate = LocalDate.of(2026, 9, 17),
                    contentRevision = "",
                ).withCalculatedRevision()

        assertNotEquals(original.contentRevision, changedReference.contentRevision)
        assertNotEquals(original.contentRevision, changedPublicationDate.contentRevision)
        assertNotEquals(changedReference.contentRevision, changedPublicationDate.contentRevision)
    }

    @Test
    fun `deduplication prefers a complete detail over a newer list stub`() {
        val complete =
            warning(
                fullText = "Vollständiger amtlicher Meldungstext",
                updatedAt = Instant.parse("2026-09-16T10:00:00Z"),
                isComplete = true,
            )
        val newerStub =
            warning(
                fullText = "",
                updatedAt = Instant.parse("2026-09-16T12:00:00Z"),
                isComplete = false,
            )

        assertEquals(complete, mergeAndDeduplicateWarnings(listOf(newerStub, complete)).single())
    }

    @Test
    fun `active date bounds are inclusive and expired or withdrawn warnings are inactive`() {
        val warning =
            warning(
                validFrom = LocalDate.of(2026, 9, 10),
                validUntil = LocalDate.of(2026, 9, 16),
            )

        assertFalse(warning.isActiveOn(LocalDate.of(2026, 9, 9)))
        assertTrue(warning.isActiveOn(LocalDate.of(2026, 9, 10)))
        assertTrue(warning.isActiveOn(LocalDate.of(2026, 9, 16)))
        assertFalse(warning.isActiveOn(LocalDate.of(2026, 9, 17)))
        assertFalse(warning.copy(lifecycle = WarningLifecycle.EXPIRED).isActiveOn(LocalDate.of(2026, 9, 16)))
        assertFalse(warning.copy(lifecycle = WarningLifecycle.WITHDRAWN).isActiveOn(LocalDate.of(2026, 9, 16)))
    }

    @Test
    fun `category rules remain conservative without official severity`() {
        assertEquals(
            WarningCategory.NOTICE,
            WarningCategoryRules.categorize(
                title = "Sperrung des Fahrwassers",
                body = "Eine Tonne wurde verlegt.",
            ),
        )
        assertEquals(
            WarningCategory.WARNING,
            WarningCategoryRules.categorize(
                title = "Untiefe",
                body = "Vor der Untiefe wird gewarnt.",
            ),
        )
        assertEquals(
            WarningCategory.DANGER,
            WarningCategoryRules.categorize(
                title = "Gefahrenstelle",
                body = "Es besteht unmittelbare Gefahr für die Schifffahrt.",
            ),
        )
    }

    private fun warning(
        fullText: String = "Amtlicher Meldungstext",
        updatedAt: Instant = Instant.parse("2026-09-16T10:00:00Z"),
        validFrom: LocalDate? = LocalDate.of(2026, 9, 10),
        validUntil: LocalDate? = LocalDate.of(2026, 9, 20),
        isComplete: Boolean = true,
    ): NorthSeaWarning {
        val base =
            NorthSeaWarning(
                id = "elwis:4711",
                officialId = "4711",
                reference = "BfS 4711/26",
                title = "Tonne verlegt",
                fullText = fullText,
                source = WarningSourceId.ELWIS,
                publisher = "WSA Ems-Nordsee",
                category = WarningCategory.NOTICE,
                publishedDate = LocalDate.of(2026, 9, 16),
                validFrom = validFrom,
                validUntil = validUntil,
                area = "Außenems",
                geometry = null,
                sourceUrl = "https://www.elwis.de/DE/dynamisch/Bfs/bfsMeldung:4711:elwis_bfs_showBfs",
                linkKind = OfficialLinkKind.WEB,
                lastUpdatedAt = updatedAt,
                lifecycle = WarningLifecycle.ACTIVE,
                isComplete = isComplete,
                contentRevision = "",
            )
        return base.withCalculatedRevision()
    }

    private fun NorthSeaWarning.withCalculatedRevision(): NorthSeaWarning =
        copy(contentRevision = WarningIdentity.contentRevision(this))
}
