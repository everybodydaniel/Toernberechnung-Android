package com.example.trnberechnung.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Query
import androidx.room.Upsert
import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialLinkKind
import com.example.trnberechnung.warnings.OfficialUrlPolicy
import com.example.trnberechnung.warnings.WarningCategory
import com.example.trnberechnung.warnings.WarningCoordinate
import com.example.trnberechnung.warnings.WarningGeometry
import com.example.trnberechnung.warnings.WarningGeometryType
import com.example.trnberechnung.warnings.WarningLifecycle
import com.example.trnberechnung.warnings.WarningSourceId
import java.time.Instant
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "north_sea_warnings",
    indices = [
        Index(value = ["source"], name = "index_north_sea_warnings_source"),
        Index(
            value = ["lifecycle", "validUntil"],
            name = "index_north_sea_warnings_lifecycle_validUntil",
        ),
    ],
)
data class NorthSeaWarningEntity(
    @androidx.room.PrimaryKey val id: String,
    val officialId: String?,
    val reference: String?,
    val title: String,
    val fullText: String,
    val source: String,
    val publisher: String,
    val category: String,
    val publishedDate: String?,
    val validFrom: String?,
    val validUntil: String?,
    val area: String?,
    val geometryType: String?,
    val coordinates: String?,
    val sourceUrl: String,
    val linkKind: String,
    val lastUpdatedAt: Long,
    val cachedAt: Long,
    val lifecycle: String,
    val isComplete: Boolean,
    val contentRevision: String,
    val seenRevision: String?,
)

@Entity(tableName = "warning_source_sync")
data class WarningSourceSyncEntity(
    @androidx.room.PrimaryKey val source: String,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    val isStale: Boolean,
    val isIncomplete: Boolean,
    val lastError: String?,
)

@Dao
interface NorthSeaWarningDao {
    @Query("SELECT * FROM north_sea_warnings ORDER BY publishedDate DESC, id ASC")
    fun observeAll(): Flow<List<NorthSeaWarningEntity>>

    @Query("SELECT * FROM warning_source_sync ORDER BY source ASC")
    fun observeSyncStates(): Flow<List<WarningSourceSyncEntity>>

    @Query("SELECT * FROM north_sea_warnings WHERE source = :source")
    suspend fun getBySource(source: String): List<NorthSeaWarningEntity>

    @Query("SELECT * FROM north_sea_warnings WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NorthSeaWarningEntity?

    @Query("SELECT * FROM warning_source_sync WHERE source = :source LIMIT 1")
    suspend fun getSyncState(source: String): WarningSourceSyncEntity?

    @Upsert
    suspend fun upsertWarnings(warnings: List<NorthSeaWarningEntity>)

    @Upsert
    suspend fun upsertSyncState(state: WarningSourceSyncEntity)

    @Query(
        """
        UPDATE north_sea_warnings
        SET seenRevision = contentRevision
        WHERE lifecycle = 'ACTIVE'
          AND (validFrom IS NULL OR validFrom <= :today)
          AND (validUntil IS NULL OR validUntil >= :today)
        """,
    )
    suspend fun markActiveWarningsSeen(today: String)
}

internal fun NorthSeaWarning.toEntity(
    cachedAt: Instant,
    previousSeenRevision: String?,
): NorthSeaWarningEntity =
    NorthSeaWarningEntity(
        id = id,
        officialId = officialId,
        reference = reference,
        title = title,
        fullText = fullText,
        source = source.name,
        publisher = publisher,
        category = category.name,
        publishedDate = publishedDate?.toString(),
        validFrom = validFrom?.toString(),
        validUntil = validUntil?.toString(),
        area = area,
        geometryType = geometry?.type?.name,
        coordinates = geometry?.coordinates?.let(WarningCoordinateCodec::encode),
        sourceUrl = sourceUrl,
        linkKind = linkKind.name,
        lastUpdatedAt = lastUpdatedAt.toEpochMilli(),
        cachedAt = cachedAt.toEpochMilli(),
        lifecycle = lifecycle.name,
        isComplete = isComplete,
        contentRevision = contentRevision,
        seenRevision = previousSeenRevision,
    )

internal fun NorthSeaWarningEntity.toModelOrNull(): NorthSeaWarning? {
    val parsedSource = enumValueOrNull<WarningSourceId>(source) ?: return null
    val validatedLinkKind = OfficialUrlPolicy.linkKind(sourceUrl) ?: return null
    val parsedGeometry =
        geometryType
            ?.let { enumValueOrNull<WarningGeometryType>(it) }
            ?.let { type ->
                coordinates
                    ?.let(WarningCoordinateCodec::decode)
                    ?.let { WarningGeometry.validated(type, it) }
            }
    return NorthSeaWarning(
        id = id,
        officialId = officialId,
        reference = reference,
        title = title,
        fullText = fullText,
        source = parsedSource,
        publisher = publisher,
        category = enumValueOrNull<WarningCategory>(category) ?: WarningCategory.NOTICE,
        publishedDate = publishedDate.toLocalDateOrNull(),
        validFrom = validFrom.toLocalDateOrNull(),
        validUntil = validUntil.toLocalDateOrNull(),
        area = area,
        geometry = parsedGeometry,
        sourceUrl = sourceUrl,
        linkKind = validatedLinkKind,
        lastUpdatedAt = Instant.ofEpochMilli(lastUpdatedAt),
        lifecycle = enumValueOrNull<WarningLifecycle>(lifecycle) ?: WarningLifecycle.WITHDRAWN,
        isComplete = isComplete,
        contentRevision = contentRevision,
    )
}

internal fun NorthSeaWarningEntity.isUnseenOn(date: LocalDate): Boolean =
    toModelOrNull()?.isActiveOn(date) == true &&
        (seenRevision == null || seenRevision != contentRevision)

internal object WarningCoordinateCodec {
    fun encode(coordinates: List<WarningCoordinate>): String =
        coordinates.joinToString(";") { coordinate ->
            String.format(
                Locale.ROOT,
                "%.8f,%.8f",
                coordinate.latitude,
                coordinate.longitude,
            )
        }

    fun decode(value: String): List<WarningCoordinate>? {
        if (value.isBlank()) return null
        val decoded =
            value.split(';').map { pair ->
                val parts = pair.split(',')
                if (parts.size != 2) return null
                val latitude = parts[0].toDoubleOrNull() ?: return null
                val longitude = parts[1].toDoubleOrNull() ?: return null
                WarningCoordinate(latitude, longitude)
            }
        return decoded.takeIf { coordinates -> coordinates.all(WarningCoordinate::isValid) }
    }
}

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

private fun String?.toLocalDateOrNull(): LocalDate? =
    this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
