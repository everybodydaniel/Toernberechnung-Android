package com.example.trnberechnung.warnings

import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OfficialWarningSourcesTest {
    @Test
    fun `bsh adapter exposes only the official north sea pdf document`() =
        runTest {
            val source = BshWarningSource()
            val document = source.documents.single()
            val result = source.fetchActiveWarnings(Instant.parse("2026-09-16T10:00:00Z"))

            assertEquals(WarningSourceId.BSH, source.source)
            assertEquals(OfficialLinkKind.PDF, document.linkKind)
            assertEquals(OfficialLinkKind.PDF, OfficialUrlPolicy.linkKind(document.url))
            assertTrue(OfficialUrlPolicy.isAllowed(document.url))
            val success = assertInstanceOf(WarningSourceResult.Success::class.java, result)
            assertTrue(success.snapshot.isComplete)
            assertTrue(success.snapshot.warnings.isEmpty())
        }

    @Test
    fun `elwis adapter posts the current validity date and follows declared pages`() =
        runTest {
            val gateway =
                FakeElwisGateway(
                    firstPage =
                        listPage(
                            rows = listRow(id = "4711", reference = "BfS 4711/26", title = "Tonne verlegt"),
                            pageLinks = setOf(2),
                        ),
                    pages =
                        mapOf(
                            2 to
                                listPage(
                                    rows =
                                        listRow(
                                            id = "4712",
                                            reference = "BfS 4712/26",
                                            title = "Baggerarbeiten",
                                        ),
                                ),
                        ),
                )
            val source = ElwisWarningSource(gateway)

            val result = source.fetchActiveWarnings(Instant.parse("2026-09-16T10:00:00Z"))

            val success = assertInstanceOf(WarningSourceResult.Success::class.java, result)
            assertTrue(success.snapshot.isComplete)
            assertEquals(listOf("4711", "4712"), success.snapshot.warnings.map(NorthSeaWarning::officialId))
            assertEquals("16", gateway.postedFields["form[gueltigVonTag]"])
            assertEquals("09", gateway.postedFields["form[gueltigVonMonat]"])
            assertEquals("2026", gateway.postedFields["form[gueltigVonJahr]"])
            assertEquals("16", gateway.postedFields["form[gueltigBisTag]"])
            assertTrue(gateway.requestedUrls.any { "changePage:2:" in it })
            assertEquals(OfficialLinkKind.WEB, source.documents.single().linkKind)
            assertTrue(OfficialUrlPolicy.isAllowed(source.documents.single().url))
        }

    @Test
    fun `elwis adapter retains valid rows but marks a partial parse incomplete`() =
        runTest {
            val malformed = "<tr><td><a href=\"bfsMeldung:9999:elwis_bfs_showBfs\">defekt</a></td></tr>"
            val gateway =
                FakeElwisGateway(
                    firstPage =
                        listPage(
                            rows =
                                listRow(id = "4711", reference = "BfS 4711/26", title = "Tonne verlegt") +
                                    malformed,
                        ),
                )

            val result = ElwisWarningSource(gateway).fetchActiveWarnings(Instant.parse("2026-09-16T10:00:00Z"))

            val success = assertInstanceOf(WarningSourceResult.Success::class.java, result)
            assertFalse(success.snapshot.isComplete)
            assertEquals(1, success.snapshot.warnings.size)
            assertTrue(success.snapshot.issue?.contains("nicht verarbeitet") == true)
        }

    @Test
    fun `elwis adapter marks a duplicated follow-up page incomplete`() =
        runTest {
            val duplicateRow = listRow(id = "4711", reference = "BfS 4711/26", title = "Tonne verlegt")
            val gateway =
                FakeElwisGateway(
                    firstPage = listPage(rows = duplicateRow, pageLinks = setOf(2)),
                    pages = mapOf(2 to listPage(rows = duplicateRow)),
                )

            val result = ElwisWarningSource(gateway).fetchActiveWarnings(Instant.parse("2026-09-16T10:00:00Z"))

            val success = assertInstanceOf(WarningSourceResult.Success::class.java, result)
            assertFalse(success.snapshot.isComplete)
            assertEquals(listOf("4711"), success.snapshot.warnings.map(NorthSeaWarning::officialId))
            assertTrue(success.snapshot.issue?.contains("nicht verarbeitet") == true)
        }

    @Test
    fun `elwis adapter reports an unrecognized html response as a source failure`() =
        runTest {
            val gateway = FakeElwisGateway(firstPage = "<html><body>Wartungsseite</body></html>")

            val result = ElwisWarningSource(gateway).fetchActiveWarnings(Instant.parse("2026-09-16T10:00:00Z"))

            val failure = assertInstanceOf(WarningSourceResult.Failure::class.java, result)
            assertEquals(WarningSourceId.ELWIS, failure.source)
            assertTrue(failure.message.contains("verarbeitet"))
        }

    private class FakeElwisGateway(
        private val firstPage: String,
        private val pages: Map<Int, String> = emptyMap(),
    ) : ElwisHttpGateway {
        val requestedUrls = mutableListOf<String>()
        var postedFields: Map<String, String> = emptyMap()

        override suspend fun get(url: String): String {
            requestedUrls += url
            val page = Regex("changePage:(\\d+):").find(url)?.groupValues?.get(1)?.toInt()
            return if (page == null) {
                "<form><input name=\"form[_token]\" value=\"csrf-token\" /></form>"
            } else {
                pages.getValue(page)
            }
        }

        override suspend fun postForm(
            url: String,
            fields: Map<String, String>,
        ): String {
            requestedUrls += url
            postedFields = fields
            return firstPage
        }
    }

    companion object {
        private fun listPage(
            rows: String,
            pageLinks: Set<Int> = emptySet(),
        ): String =
            buildString {
                append("<table>")
                append(rows)
                append("</table>")
                pageLinks.forEach { page ->
                    append("<a href=\"changePage:$page:elwis_bfs_showBfs\">$page</a>")
                }
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
              <td>16.09.2026 <a href="bfsMeldung:$id:elwis_bfs_showBfs">Details</a></td>
            </tr>
            """.trimIndent()
    }
}
