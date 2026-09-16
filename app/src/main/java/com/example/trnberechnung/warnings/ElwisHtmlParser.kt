package com.example.trnberechnung.warnings

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

data class ElwisNoticeSummary(
    val officialId: String,
    val reference: String,
    val publisher: String,
    val title: String,
    val area: String,
    val publishedDate: LocalDate?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val sourceUrl: String,
)

data class ElwisListPage(
    val notices: List<ElwisNoticeSummary>,
    val pageNumbers: Set<Int>,
    val skippedNoticeRows: Int,
)

object ElwisHtmlParser {
    private val rowRegex = Regex("(?is)<tr(?:\\s[^>]*)?>(.*?)</tr>")
    private val cellRegex = Regex("(?is)<t[dh](?:\\s[^>]*)?>(.*?)</t[dh]>")
    private val detailIdRegex = Regex("(?i)bfsMeldung:(\\d+):elwis_bfs_showBfs")
    private val pageRegex = Regex("(?i)changePage:(\\d+):elwis_bfs_showBfs")
    private val inputRegex = Regex("(?is)<input(?:\\s[^>]*)?>")
    private val attributeRegex = Regex("(?i)([a-z_:][-a-z0-9_:.]*)\\s*=\\s*[\"']([^\"']*)[\"']")
    private val dateRegex = Regex("\\b\\d{2}\\.\\d{2}\\.\\d{4}\\b")
    private val coordinateRegex =
        Regex(
            pattern =
                """(?i)(\d{1,2})\s*°\s*(\d{1,2}(?:[.,]\d+)?)\s*[\u0027‘’′´]?\s*([NS])""" +
                    """\s*[,;/\s]+\s*(\d{1,3})\s*°\s*(\d{1,2}(?:[.,]\d+)?)""" +
                    """\s*[\u0027‘’′´]?\s*([EW])""",
        )
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.GERMAN)
    private val berlinZone = ZoneId.of("Europe/Berlin")

    fun parseCsrfToken(html: String): String? =
        inputRegex
            .findAll(html)
            .map { input ->
                attributeRegex
                    .findAll(input.value)
                    .associate { attribute ->
                        attribute.groupValues[1].lowercase(Locale.ROOT) to attribute.groupValues[2]
                    }
            }.firstOrNull { attributes -> attributes["name"] == "form[_token]" }
            ?.get("value")
            ?.takeIf(String::isNotBlank)

    fun parseListPage(html: String): ElwisListPage {
        val notices = mutableListOf<ElwisNoticeSummary>()
        var candidateRows = 0
        rowRegex.findAll(html).forEach { rowMatch ->
            val row = rowMatch.groupValues[1]
            val officialId = detailIdRegex.find(row)?.groupValues?.getOrNull(1) ?: return@forEach
            candidateRows += 1
            val cells = cellRegex.findAll(row).map { it.groupValues[1] }.toList()
            if (cells.size < 5) return@forEach

            val identityLines = cellLines(cells[1])
            val contentLines = cellLines(cells[2])
            val validityDates = dateRegex.findAll(plainText(cells[3])).map { it.value.toDateOrNull() }.toList()
            val publishedDate = dateRegex.find(plainText(cells[4]))?.value.toDateOrNull()
            val reference = identityLines.firstOrNull().orEmpty()
            val publisher = identityLines.drop(1).firstOrNull().orEmpty()
            val area = contentLines.firstOrNull().orEmpty()
            val title = contentLines.drop(1).lastOrNull().orEmpty()
            if (
                reference.isBlank() ||
                publisher.isBlank() ||
                area.isBlank() ||
                title.isBlank()
            ) {
                return@forEach
            }
            notices +=
                ElwisNoticeSummary(
                    officialId = officialId,
                    reference = reference,
                    publisher = publisher,
                    title = title,
                    area = area,
                    publishedDate = publishedDate,
                    validFrom = validityDates.getOrNull(0),
                    validUntil = validityDates.getOrNull(1),
                    sourceUrl =
                        "https://www.elwis.de/DE/dynamisch/Bfs/" +
                            "bfsMeldung:$officialId:elwis_bfs_showBfs",
                )
        }
        return ElwisListPage(
            notices = notices.distinctBy(ElwisNoticeSummary::officialId),
            pageNumbers = pageRegex.findAll(html).mapNotNull { it.groupValues[1].toIntOrNull() }.toSet(),
            skippedNoticeRows = (candidateRows - notices.size).coerceAtLeast(0),
        )
    }

    fun summaryToWarning(
        summary: ElwisNoticeSummary,
        fetchedAt: Instant,
        today: LocalDate = fetchedAt.atZone(berlinZone).toLocalDate(),
    ): NorthSeaWarning {
        val lifecycle =
            if (summary.validUntil?.isBefore(today) == true) {
                WarningLifecycle.EXPIRED
            } else {
                WarningLifecycle.ACTIVE
            }
        val base =
            NorthSeaWarning(
                id =
                    WarningIdentity.stableId(
                        source = WarningSourceId.ELWIS,
                        officialId = summary.officialId,
                        title = summary.title,
                        publishedDate = summary.publishedDate,
                        area = summary.area,
                    ),
                officialId = summary.officialId,
                reference = summary.reference,
                title = summary.title,
                fullText = "",
                source = WarningSourceId.ELWIS,
                publisher = summary.publisher,
                category = WarningCategoryRules.categorize(summary.title, ""),
                publishedDate = summary.publishedDate,
                validFrom = summary.validFrom,
                validUntil = summary.validUntil,
                area = summary.area,
                geometry = null,
                sourceUrl = summary.sourceUrl,
                linkKind = OfficialLinkKind.WEB,
                lastUpdatedAt = fetchedAt,
                lifecycle = lifecycle,
                isComplete = false,
                contentRevision = "",
            )
        return base.copy(contentRevision = WarningIdentity.contentRevision(base))
    }

    fun parseDetailPage(
        html: String,
        summary: NorthSeaWarning,
        fetchedAt: Instant,
    ): NorthSeaWarning? {
        if (summary.source != WarningSourceId.ELWIS) return null
        val rows =
            rowRegex.findAll(html).map { rowMatch ->
                cellRegex.findAll(rowMatch.groupValues[1]).map { plainText(it.groupValues[1]) }.toList()
            }.filter(List<String>::isNotEmpty).toList()
        val header = rows.firstOrNull { row -> row.first().startsWith("Bekanntmachung für Seefahrer") }
        val fields = mutableMapOf<String, String>()
        var body = ""
        rows.forEachIndexed { index, row ->
            if (row.size >= 2 && row[0].endsWith(':')) {
                fields[normalizeLabel(row[0])] = row.drop(1).joinToString(" ").trim()
            }
            if (row.firstOrNull()?.equals("Angaben:", ignoreCase = true) == true) {
                body = rows.getOrNull(index + 1)?.joinToString("\n").orEmpty().trim()
            }
        }
        if (header == null || body.isBlank()) return null

        val headerPublisherAndDate = header.getOrNull(1).orEmpty()
        val publisher =
            headerPublisherAndDate.substringBeforeLast(',').trim().ifBlank { summary.publisher }
        val publishedDate =
            dateRegex.find(headerPublisherAndDate)?.value.toDateOrNull() ?: summary.publishedDate
        val regionAndTitle =
            rows
                .asSequence()
                .flatten()
                .firstOrNull { value -> value.startsWith("Deutschland.") || value == "Deutschland" }
        val area = regionAndTitle?.substringBefore(" , ")?.trim().orEmpty().ifBlank { summary.area.orEmpty() }
        val title = regionAndTitle?.substringAfter(" , ", missingDelimiterValue = "")?.trim().orEmpty().ifBlank { summary.title }
        val validFrom = fields["gültig von"]?.firstDateOrNull() ?: summary.validFrom
        val validUntil = fields["gültig bis (einschl.)"]?.firstDateOrNull() ?: summary.validUntil
        val currentlyPublished =
            when (fields["aktuell veröffentlicht"]?.trim()?.lowercase(Locale.GERMAN)) {
                "ja" -> true
                "nein" -> false
                else -> return null
            }
        val today = fetchedAt.atZone(berlinZone).toLocalDate()
        val lifecycle =
            when {
                !currentlyPublished -> WarningLifecycle.WITHDRAWN
                validUntil?.isBefore(today) == true -> WarningLifecycle.EXPIRED
                else -> WarningLifecycle.ACTIVE
            }
        val coordinateSystem = fields["geografische angabe in"].orEmpty()
        val location = fields["geografische lage"].orEmpty()
        val coordinates =
            if (coordinateSystem.contains("WGS 84", ignoreCase = true)) {
                parseCoordinates("$location\n$body")
            } else {
                emptyList()
            }
        val geometry = classifyGeometry(body, coordinates)
        val base =
            summary.copy(
                title = title,
                fullText = body,
                publisher = publisher,
                category = WarningCategoryRules.categorize(title, body),
                publishedDate = publishedDate,
                validFrom = validFrom,
                validUntil = validUntil,
                area = area.ifBlank { null },
                geometry = geometry,
                lastUpdatedAt = fetchedAt,
                lifecycle = lifecycle,
                isComplete = true,
                contentRevision = "",
            )
        return base.copy(contentRevision = WarningIdentity.contentRevision(base))
    }

    internal fun parseCoordinates(text: String): List<WarningCoordinate> =
        coordinateRegex
            .findAll(text)
            .mapNotNull { match ->
                val latitudeDegrees = match.groupValues[1].toDoubleOrNull() ?: return@mapNotNull null
                val latitudeMinutes = match.groupValues[2].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
                val longitudeDegrees = match.groupValues[4].toDoubleOrNull() ?: return@mapNotNull null
                val longitudeMinutes = match.groupValues[5].replace(',', '.').toDoubleOrNull() ?: return@mapNotNull null
                if (latitudeMinutes !in 0.0..<60.0 || longitudeMinutes !in 0.0..<60.0) {
                    return@mapNotNull null
                }
                val latitude =
                    (latitudeDegrees + latitudeMinutes / 60.0) *
                        if (match.groupValues[3].equals("S", ignoreCase = true)) -1 else 1
                val longitude =
                    (longitudeDegrees + longitudeMinutes / 60.0) *
                        if (match.groupValues[6].equals("W", ignoreCase = true)) -1 else 1
                WarningCoordinate(latitude, longitude).takeIf(WarningCoordinate::isValid)
            }.distinct()
            .toList()

    private fun classifyGeometry(
        body: String,
        coordinates: List<WarningCoordinate>,
    ): WarningGeometry? {
        if (coordinates.isEmpty()) return null
        if (coordinates.size == 1) {
            return WarningGeometry.validated(WarningGeometryType.POINT, coordinates)
        }
        val normalizedBody = body.lowercase(Locale.GERMAN).replace(Regex("\\s+"), " ")
        val explicitPolygon =
            listOf(
                "sperrgebiet liegt innerhalb der folgenden koordinaten",
                "sperrgebiet wird durch folgende positionen begrenzt",
                "eckpunkte des sperrgebiets",
            ).any(normalizedBody::contains)
        val explicitLine =
            listOf("verbindungslinie", "linie von", "entlang der linie").any(normalizedBody::contains)
        val type =
            when {
                explicitPolygon && coordinates.size >= 3 -> WarningGeometryType.POLYGON
                explicitLine -> WarningGeometryType.LINE
                else -> WarningGeometryType.MULTI_POINT
            }
        return WarningGeometry.validated(type, coordinates)
    }

    private fun cellLines(value: String): List<String> =
        decodeEntities(
            value
                .replace(Regex("(?i)<br\\s*/?>"), "\n")
                .replace(Regex("(?i)</p\\s*>"), "\n")
                .replace(Regex("(?is)<[^>]+>"), " "),
        ).replace('\u00A0', ' ')
            .lines()
            .map { line -> line.replace(Regex("[\\t ]+"), " ").trim() }
            .filter(String::isNotBlank)

    private fun plainText(value: String): String = cellLines(value).joinToString("\n").trim()

    private fun normalizeLabel(value: String): String =
        value.removeSuffix(":").trim().lowercase(Locale.GERMAN)

    private fun String?.toDateOrNull(): LocalDate? {
        if (this.isNullOrBlank()) return null
        return try {
            LocalDate.parse(this, dateFormatter)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun String.firstDateOrNull(): LocalDate? = dateRegex.find(this)?.value.toDateOrNull()

    private fun decodeEntities(value: String): String =
        Regex("&(#x?[0-9a-fA-F]+|[A-Za-z]+);").replace(value) { match ->
            val entity = match.groupValues[1]
            when {
                entity.startsWith("#x", ignoreCase = true) ->
                    entity.drop(2).toIntOrNull(16)?.let(::codePointString) ?: match.value
                entity.startsWith('#') ->
                    entity.drop(1).toIntOrNull()?.let(::codePointString) ?: match.value
                else -> namedEntities[entity.lowercase(Locale.ROOT)] ?: match.value
            }
        }

    private fun codePointString(codePoint: Int): String =
        runCatching { String(Character.toChars(codePoint)) }.getOrDefault("")

    private val namedEntities =
        mapOf(
            "amp" to "&",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to " ",
            "lt" to "<",
            "gt" to ">",
            "auml" to "ä",
            "ouml" to "ö",
            "uuml" to "ü",
            "szlig" to "ß",
        )
}
