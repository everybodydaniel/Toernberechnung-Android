package com.example.trnberechnung.warnings

import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ElwisHtmlParserTest {
    private val fetchedAt = Instant.parse("2026-09-16T10:00:00Z")

    @Test
    fun `csrf token and paginated notice rows are parsed from official html structure`() {
        val html =
            """
            <form>
              <input name="form[_token]" value="csrf-4711" />
            </form>
            <table>
              ${listRow(id = "4711", reference = "BfS 4711/26", title = "Tonne verlegt")}
            </table>
            <a href="/DE/dynamisch/Bfs/changePage:2:elwis_bfs_showBfs">2</a>
            """.trimIndent()

        val page = ElwisHtmlParser.parseListPage(html)

        assertEquals("csrf-4711", ElwisHtmlParser.parseCsrfToken(html))
        assertEquals(setOf(2), page.pageNumbers)
        assertEquals(0, page.skippedNoticeRows)
        assertEquals(1, page.notices.size)
        with(page.notices.single()) {
            assertEquals("4711", officialId)
            assertEquals("BfS 4711/26", reference)
            assertEquals("WSA Ems-Nordsee", publisher)
            assertEquals("Tonne verlegt", title)
            assertEquals("Deutschland. Nordsee", area)
            assertEquals(LocalDate.of(2026, 9, 16), publishedDate)
            assertEquals(LocalDate.of(2026, 9, 16), validFrom)
            assertEquals(LocalDate.of(2026, 9, 20), validUntil)
            assertTrue(sourceUrl.endsWith("bfsMeldung:4711:elwis_bfs_showBfs"))
        }
    }

    @Test
    fun `unreadable candidate rows are reported instead of silently treated as complete`() {
        val html =
            """
            <table>
              ${listRow(id = "4711", reference = "BfS 4711/26", title = "Tonne verlegt")}
              <tr><td><a href="bfsMeldung:9999:elwis_bfs_showBfs">defekt</a></td></tr>
            </table>
            """.trimIndent()

        val page = ElwisHtmlParser.parseListPage(html)

        assertEquals(1, page.notices.size)
        assertEquals(1, page.skippedNoticeRows)
    }

    @Test
    fun `list summary classifies an already ended notice as expired`() {
        val summary =
            ElwisNoticeSummary(
                officialId = "4711",
                reference = "BfS 4711/26",
                publisher = "WSA Ems-Nordsee",
                title = "Tonne verlegt",
                area = "Außenems",
                publishedDate = LocalDate.of(2026, 9, 10),
                validFrom = LocalDate.of(2026, 9, 10),
                validUntil = LocalDate.of(2026, 9, 15),
                sourceUrl = "https://www.elwis.de/DE/dynamisch/Bfs/bfsMeldung:4711:elwis_bfs_showBfs",
            )

        val warning =
            ElwisHtmlParser.summaryToWarning(
                summary = summary,
                fetchedAt = fetchedAt,
                today = LocalDate.of(2026, 9, 16),
            )

        assertEquals(WarningLifecycle.EXPIRED, warning.lifecycle)
        assertFalse(warning.isComplete)
        assertEquals(OfficialLinkKind.WEB, warning.linkKind)
    }

    @Test
    fun `detail page preserves official text and creates polygon only from explicit wording`() {
        val summary = summaryWarning()
        val detail =
            ElwisHtmlParser.parseDetailPage(
                html = detailHtml(currentlyPublished = "ja"),
                summary = summary,
                fetchedAt = fetchedAt,
            )

        requireNotNull(detail)
        assertTrue(detail.isComplete)
        assertEquals("Sperrgebiet Außenems", detail.title)
        assertEquals("Deutschland. Nordsee", detail.area)
        assertEquals("WSA Ems-Nordsee", detail.publisher)
        assertEquals(LocalDate.of(2026, 9, 16), detail.publishedDate)
        assertEquals(LocalDate.of(2026, 9, 16), detail.validFrom)
        assertEquals(LocalDate.of(2026, 9, 20), detail.validUntil)
        assertEquals(WarningLifecycle.ACTIVE, detail.lifecycle)
        assertEquals(WarningCategory.WARNING, detail.category)
        assertTrue(detail.fullText.startsWith("Das Sperrgebiet wird durch folgende Positionen begrenzt"))
        assertEquals(WarningGeometryType.POLYGON, detail.geometry?.type)
        assertEquals(3, detail.geometry?.coordinates?.size)
        assertTrue(detail.contentRevision.isNotBlank())
    }

    @Test
    fun `detail page marks an officially unpublished notice as withdrawn`() {
        val detail =
            ElwisHtmlParser.parseDetailPage(
                html = detailHtml(currentlyPublished = "nein"),
                summary = summaryWarning(),
                fetchedAt = fetchedAt,
            )

        assertEquals(WarningLifecycle.WITHDRAWN, detail?.lifecycle)
    }

    @Test
    fun `detail page without current publication state is rejected instead of treated as withdrawn`() {
        val detail =
            ElwisHtmlParser.parseDetailPage(
                html = detailHtml(currentlyPublished = null),
                summary = summaryWarning(),
                fetchedAt = fetchedAt,
            )

        assertNull(detail)
    }

    @Test
    fun `coordinates are accepted only as valid wgs84 degree minute values`() {
        val coordinates =
            ElwisHtmlParser.parseCoordinates(
                "53\u00b0 30,0' N 007\u00b0 15,0' E; " +
                    "53\u00b0 60,0' N 007\u00b0 15,0' E; " +
                    "12\u00b0 30.0' S 045\u00b0 15.0' W",
            )

        assertEquals(2, coordinates.size)
        assertEquals(53.5, coordinates[0].latitude, 0.000_001)
        assertEquals(7.25, coordinates[0].longitude, 0.000_001)
        assertEquals(-12.5, coordinates[1].latitude, 0.000_001)
        assertEquals(-45.25, coordinates[1].longitude, 0.000_001)
    }

    @Test
    fun `left single quotation mark is accepted as an official minutes symbol`() {
        val coordinates =
            ElwisHtmlParser.parseCoordinates(
                "53\u00b0 30,0\u2018 N 007\u00b0 15,0\u2018 E",
            )

        assertEquals(1, coordinates.size)
        assertEquals(53.5, coordinates.single().latitude, 0.000_001)
        assertEquals(7.25, coordinates.single().longitude, 0.000_001)
    }

    @Test
    fun `malformed detail without official body is rejected`() {
        assertNull(
            ElwisHtmlParser.parseDetailPage(
                html = "<table><tr><td>Bekanntmachung für Seefahrer</td></tr></table>",
                summary = summaryWarning(),
                fetchedAt = fetchedAt,
            ),
        )
    }

    private fun summaryWarning(): NorthSeaWarning {
        val parsed =
            ElwisHtmlParser.parseListPage(
                "<table>${listRow(id = "4711", reference = "BfS 4711/26", title = "Sperrgebiet")}</table>",
            )
        return ElwisHtmlParser.summaryToWarning(parsed.notices.single(), fetchedAt)
    }

    private fun detailHtml(currentlyPublished: String?): String {
        val publicationRow =
            currentlyPublished?.let { value ->
                "<tr><td>Aktuell veröffentlicht:</td><td>$value</td></tr>"
            }.orEmpty()
        return """
            <table>
              <tr><td>Bekanntmachung f&uuml;r Seefahrer</td><td>WSA Ems-Nordsee, 16.09.2026</td></tr>
              <tr><td>Deutschland. Nordsee , Sperrgebiet Außenems</td></tr>
              $publicationRow
              <tr><td>Gültig von:</td><td>16.09.2026</td></tr>
              <tr><td>Gültig bis (einschl.):</td><td>20.09.2026</td></tr>
              <tr><td>Geografische Angabe in:</td><td>WGS 84</td></tr>
              <tr><td>Geografische Lage:</td><td>
                53° 30,0' N 007° 15,0' E;<br/>
                53° 31,0' N 007° 16,0' E;<br/>
                53° 29,0' N 007° 17,0' E
              </td></tr>
              <tr><td>Angaben:</td></tr>
              <tr><td>Das Sperrgebiet wird durch folgende Positionen begrenzt. Es wird ausdrücklich gewarnt.</td></tr>
            </table>
            """.trimIndent()
    }

    private fun listRow(
        id: String,
        reference: String,
        title: String,
    ): String =
        """
        <tr>
          <td>1</td>
          <td>$reference<br/>WSA Ems-Nordsee</td>
          <td>Deutschland. Nordsee<br/>$title</td>
          <td>16.09.2026<br/>20.09.2026</td>
          <td>16.09.2026 <a href="/DE/dynamisch/Bfs/bfsMeldung:$id:elwis_bfs_showBfs">Details</a></td>
        </tr>
        """.trimIndent()
}
