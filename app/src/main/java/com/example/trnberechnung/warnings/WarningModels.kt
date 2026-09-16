package com.example.trnberechnung.warnings

import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

enum class WarningSourceId {
    BSH,
    ELWIS,
}

enum class WarningCategory {
    DANGER,
    WARNING,
    NOTICE,
}

enum class WarningLifecycle {
    ACTIVE,
    EXPIRED,
    WITHDRAWN,
}

enum class OfficialLinkKind {
    WEB,
    PDF,
}

enum class WarningGeometryType {
    POINT,
    MULTI_POINT,
    LINE,
    POLYGON,
}

data class WarningCoordinate(
    val latitude: Double,
    val longitude: Double,
) {
    val isValid: Boolean
        get() =
            latitude.isFinite() &&
                longitude.isFinite() &&
                latitude in -90.0..90.0 &&
                longitude in -180.0..180.0
}

data class WarningGeometry(
    val type: WarningGeometryType,
    val coordinates: List<WarningCoordinate>,
) {
    val isValid: Boolean
        get() =
            coordinates.all(WarningCoordinate::isValid) &&
                when (type) {
                    WarningGeometryType.POINT -> coordinates.size == 1
                    WarningGeometryType.MULTI_POINT -> coordinates.size >= 2
                    WarningGeometryType.LINE -> coordinates.size >= 2
                    WarningGeometryType.POLYGON -> coordinates.size >= 3
                }

    companion object {
        fun validated(
            type: WarningGeometryType,
            coordinates: List<WarningCoordinate>,
        ): WarningGeometry? =
            WarningGeometry(type, coordinates.distinct()).takeIf(WarningGeometry::isValid)
    }
}

data class OfficialWarningDocument(
    val id: String,
    val title: String,
    val issuer: String,
    val description: String,
    val url: String,
    val linkKind: OfficialLinkKind,
)

data class NorthSeaWarning(
    val id: String,
    val officialId: String?,
    val reference: String?,
    val title: String,
    val fullText: String,
    val source: WarningSourceId,
    val publisher: String,
    val category: WarningCategory,
    val publishedDate: LocalDate?,
    val validFrom: LocalDate?,
    val validUntil: LocalDate?,
    val area: String?,
    val geometry: WarningGeometry?,
    val sourceUrl: String,
    val linkKind: OfficialLinkKind,
    val lastUpdatedAt: Instant,
    val lifecycle: WarningLifecycle,
    val isComplete: Boolean,
    val contentRevision: String,
) {
    fun isActiveOn(date: LocalDate): Boolean =
        lifecycle == WarningLifecycle.ACTIVE &&
            (validFrom == null || !validFrom.isAfter(date)) &&
            (validUntil == null || !validUntil.isBefore(date))

    fun summary(maximumLength: Int = 220): String {
        require(maximumLength > 0)
        val compact = fullText.replace(Regex("\\s+"), " ").trim().ifBlank { title.trim() }
        return if (compact.length <= maximumLength) {
            compact
        } else {
            compact.take(maximumLength).trimEnd() + "…"
        }
    }
}

data class WarningSourceSnapshot(
    val source: WarningSourceId,
    val warnings: List<NorthSeaWarning>,
    val fetchedAt: Instant,
    val isComplete: Boolean,
    val issue: String? = null,
)

sealed interface WarningSourceResult {
    val source: WarningSourceId

    data class Success(
        val snapshot: WarningSourceSnapshot,
    ) : WarningSourceResult {
        override val source: WarningSourceId = snapshot.source
    }

    data class Failure(
        override val source: WarningSourceId,
        val message: String,
        val cause: Throwable? = null,
    ) : WarningSourceResult
}

interface WarningSourceAdapter {
    val source: WarningSourceId
    val documents: List<OfficialWarningDocument>

    suspend fun fetchActiveWarnings(now: Instant): WarningSourceResult

    suspend fun fetchDetails(warning: NorthSeaWarning): Result<NorthSeaWarning> =
        Result.success(warning)
}

object WarningIdentity {
    fun stableId(
        source: WarningSourceId,
        officialId: String?,
        title: String,
        publishedDate: LocalDate?,
        area: String?,
    ): String {
        val normalizedOfficialId = officialId?.trim().orEmpty()
        if (normalizedOfficialId.isNotEmpty()) {
            return "${source.name.lowercase(Locale.ROOT)}:$normalizedOfficialId"
        }
        val immutableParts =
            listOf(
                source.name,
                normalize(title),
                publishedDate?.toString().orEmpty(),
                normalize(area.orEmpty()),
            )
        return "${source.name.lowercase(Locale.ROOT)}:sha256:${sha256(immutableParts.joinToString("|"))}"
    }

    fun contentRevision(warning: NorthSeaWarning): String =
        contentRevision(
            reference = warning.reference,
            title = warning.title,
            fullText = warning.fullText,
            publisher = warning.publisher,
            category = warning.category,
            publishedDate = warning.publishedDate,
            validFrom = warning.validFrom,
            validUntil = warning.validUntil,
            area = warning.area,
            geometry = warning.geometry,
            sourceUrl = warning.sourceUrl,
            lifecycle = warning.lifecycle,
            isComplete = warning.isComplete,
        )

    fun contentRevision(
        reference: String?,
        title: String,
        fullText: String,
        publisher: String,
        category: WarningCategory,
        publishedDate: LocalDate?,
        validFrom: LocalDate?,
        validUntil: LocalDate?,
        area: String?,
        geometry: WarningGeometry?,
        sourceUrl: String,
        lifecycle: WarningLifecycle,
        isComplete: Boolean,
    ): String {
        val canonical =
            listOf(
                normalize(reference.orEmpty()),
                normalize(title),
                normalize(fullText),
                normalize(publisher),
                category.name,
                publishedDate?.toString().orEmpty(),
                validFrom?.toString().orEmpty(),
                validUntil?.toString().orEmpty(),
                normalize(area.orEmpty()),
                geometry?.type?.name.orEmpty(),
                geometry
                    ?.coordinates
                    ?.joinToString(";") { coordinate ->
                        String.format(
                            Locale.ROOT,
                            "%.7f,%.7f",
                            coordinate.latitude,
                            coordinate.longitude,
                        )
                    }.orEmpty(),
                sourceUrl.trim(),
                lifecycle.name,
                isComplete.toString(),
            ).joinToString("|")
        return sha256(canonical)
    }

    private fun normalize(value: String): String =
        value.replace(Regex("\\s+"), " ").trim().lowercase(Locale.ROOT)

    private fun sha256(value: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
}

/**
 * Conservative, documented mapping for ELWIS notices, which do not expose a severity field.
 * Ambiguous content intentionally remains [WarningCategory.NOTICE].
 */
object WarningCategoryRules {
    private val dangerTerms =
        listOf(
            "lebensgefahr",
            "akute gefahr",
            "gefahr für die schifffahrt",
            "unmittelbare gefahr",
        )
    private val warningTerms =
        listOf(
            "warnung für die schifffahrt",
            "wird gewarnt",
            "werden gewarnt",
            "besondere vorsicht",
            "ausdrücklich gewarnt",
        )

    fun categorize(
        title: String,
        body: String,
    ): WarningCategory {
        val searchable = "$title\n$body".lowercase(Locale.GERMAN)
        return when {
            dangerTerms.any(searchable::contains) -> WarningCategory.DANGER
            warningTerms.any(searchable::contains) -> WarningCategory.WARNING
            else -> WarningCategory.NOTICE
        }
    }
}

fun mergeAndDeduplicateWarnings(warnings: Iterable<NorthSeaWarning>): List<NorthSeaWarning> =
    warnings
        .groupBy(NorthSeaWarning::id)
        .map { (_, duplicates) ->
            duplicates.maxWithOrNull(
                compareBy<NorthSeaWarning> { it.isComplete }
                    .thenBy { it.lastUpdatedAt }
                    .thenBy { it.fullText.length },
            ) ?: error("A grouped warning list cannot be empty")
        }.sortedWith(
            compareByDescending<NorthSeaWarning> { it.publishedDate }
                .thenBy(NorthSeaWarning::id),
        )
