package com.example.trnberechnung.repository

import androidx.room.withTransaction
import com.example.trnberechnung.database.AppDatabase
import com.example.trnberechnung.database.NorthSeaWarningEntity
import com.example.trnberechnung.database.WarningSourceSyncEntity
import com.example.trnberechnung.database.isUnseenOn
import com.example.trnberechnung.database.toEntity
import com.example.trnberechnung.database.toModelOrNull
import com.example.trnberechnung.warnings.NorthSeaWarning
import com.example.trnberechnung.warnings.OfficialUrlPolicy
import com.example.trnberechnung.warnings.OfficialWarningDocument
import com.example.trnberechnung.warnings.WarningIdentity
import com.example.trnberechnung.warnings.WarningLifecycle
import com.example.trnberechnung.warnings.WarningSourceAdapter
import com.example.trnberechnung.warnings.WarningSourceId
import com.example.trnberechnung.warnings.WarningSourceResult
import com.example.trnberechnung.warnings.mergeAndDeduplicateWarnings
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class WarningRepositoryState(
    val warnings: List<NorthSeaWarning>,
    val sourceStates: Map<WarningSourceId, WarningSourceSyncState>,
)

data class WarningSourceSyncState(
    val source: WarningSourceId,
    val lastAttemptAt: Instant,
    val lastSuccessAt: Instant?,
    val isStale: Boolean,
    val isIncomplete: Boolean,
    val lastError: String?,
)

data class WarningRefreshResult(
    val successfulSources: Set<WarningSourceId>,
    val failedSources: Map<WarningSourceId, String>,
)

class NorthSeaWarningRepository(
    private val database: AppDatabase,
    private val adapters: List<WarningSourceAdapter>,
    private val now: () -> Instant = Instant::now,
) {
    private val dao = database.northSeaWarningDao()
    private val refreshMutex = Mutex()
    private val adapterBySource = adapters.associateBy(WarningSourceAdapter::source)

    val documents: List<OfficialWarningDocument> =
        adapters
            .flatMap(WarningSourceAdapter::documents)
            .filter { document ->
                OfficialUrlPolicy.linkKind(document.url) == document.linkKind
            }.distinctBy(OfficialWarningDocument::id)

    val unseenCount: Flow<Int> =
        dao.observeAll().map { entities ->
            val today = now().atZone(BERLIN_ZONE).toLocalDate()
            entities.count { entity -> entity.isUnseenOn(today) }
        }

    val state: Flow<WarningRepositoryState> =
        combine(
            dao.observeAll(),
            dao.observeSyncStates(),
        ) { entities, syncEntities ->
            val today = now().atZone(BERLIN_ZONE).toLocalDate()
            WarningRepositoryState(
                warnings =
                    mergeAndDeduplicateWarnings(
                        entities.mapNotNull { entity -> entity.toModelOrNull() },
                    ).filter { warning -> warning.isActiveOn(today) },
                sourceStates =
                    syncEntities.mapNotNull { entity -> entity.toModelOrNull() }.associateBy(
                        WarningSourceSyncState::source,
                    ),
            )
        }

    suspend fun refresh(): WarningRefreshResult =
        refreshMutex.withLock {
            val attemptedAt = now()
            val results =
                coroutineScope {
                    adapters.map { adapter ->
                        async { adapter.fetchActiveWarnings(attemptedAt) }
                    }.map { deferred -> deferred.await() }
                }
            results.forEach { result -> persist(result, attemptedAt) }
            WarningRefreshResult(
                successfulSources =
                    results.filterIsInstance<WarningSourceResult.Success>().map { it.source }.toSet(),
                failedSources =
                    results
                        .filterIsInstance<WarningSourceResult.Failure>()
                        .associate { failure -> failure.source to failure.message },
            )
        }

    suspend fun loadDetails(id: String): Result<NorthSeaWarning> {
        val cachedEntity = dao.getById(id) ?: return Result.failure(NoSuchElementException(id))
        val cached =
            cachedEntity.toModelOrNull()
                ?: return Result.failure(IllegalStateException("Ungültiger Cache-Eintrag"))
        val adapter =
            adapterBySource[cached.source]
                ?: return Result.failure(IllegalStateException("Fehlender Quellen-Adapter"))
        return adapter.fetchDetails(cached).mapCatching { detailed ->
            val validated = validateWarning(detailed, cached.source) ?: error("Ungültige Quelldaten")
            val cachedAt = now()
            database.withTransaction {
                val previous = dao.getById(id)
                dao.upsertWarnings(
                    listOf(
                        validated.toEntity(
                            cachedAt = cachedAt,
                            previousSeenRevision = previous?.seenRevision,
                        ),
                    ),
                )
            }
            validated
        }
    }

    suspend fun markVisibleWarningsSeen() {
        dao.markActiveWarningsSeen(now().atZone(BERLIN_ZONE).toLocalDate().toString())
    }

    private suspend fun persist(
        result: WarningSourceResult,
        attemptedAt: Instant,
    ) {
        when (result) {
            is WarningSourceResult.Failure -> persistFailure(result, attemptedAt)
            is WarningSourceResult.Success -> persistSuccess(result, attemptedAt)
        }
    }

    private suspend fun persistFailure(
        failure: WarningSourceResult.Failure,
        attemptedAt: Instant,
    ) {
        database.withTransaction {
            val previous = dao.getSyncState(failure.source.name)
            dao.upsertSyncState(
                WarningSourceSyncEntity(
                    source = failure.source.name,
                    lastAttemptAt = attemptedAt.toEpochMilli(),
                    lastSuccessAt = previous?.lastSuccessAt,
                    isStale = true,
                    isIncomplete = previous?.isIncomplete ?: true,
                    lastError = failure.message,
                ),
            )
        }
    }

    private suspend fun persistSuccess(
        success: WarningSourceResult.Success,
        attemptedAt: Instant,
    ) {
        val snapshot = success.snapshot
        val validated =
            mergeAndDeduplicateWarnings(
                snapshot.warnings.mapNotNull { warning -> validateWarning(warning, snapshot.source) },
            )
        val droppedCount = snapshot.warnings.size - validated.size
        val snapshotIsComplete = snapshot.isComplete && droppedCount == 0
        database.withTransaction {
            val existing = dao.getBySource(snapshot.source.name)
            val existingById = existing.associateBy(NorthSeaWarningEntity::id)
            val incomingIds = validated.mapTo(mutableSetOf(), NorthSeaWarning::id)
            val incomingEntities =
                validated.map { warning ->
                    val cached = existingById[warning.id]?.toModelOrNull()
                    preserveCachedDetailsWhenSummaryIsUnchanged(
                        incoming = warning,
                        cached = cached,
                    ).toEntity(
                        cachedAt = attemptedAt,
                        previousSeenRevision = existingById[warning.id]?.seenRevision,
                    )
                }
            val noLongerPublished =
                if (snapshotIsComplete) {
                    existing
                        .filterNot { entity -> entity.id in incomingIds }
                        .map { entity ->
                            val cached = entity.toModelOrNull()
                            if (cached == null) {
                                entity.copy(
                                    lifecycle = WarningLifecycle.WITHDRAWN.name,
                                    cachedAt = attemptedAt.toEpochMilli(),
                                    seenRevision = null,
                                )
                            } else {
                                val withdrawn =
                                    cached.copy(
                                        lastUpdatedAt = attemptedAt,
                                        lifecycle = WarningLifecycle.WITHDRAWN,
                                        contentRevision = "",
                                    )
                                withdrawn
                                    .copy(contentRevision = WarningIdentity.contentRevision(withdrawn))
                                    .toEntity(
                                        cachedAt = attemptedAt,
                                        previousSeenRevision = null,
                                    )
                            }
                        }
                } else {
                    emptyList()
                }
            dao.upsertWarnings(incomingEntities + noLongerPublished)
            dao.upsertSyncState(
                WarningSourceSyncEntity(
                    source = snapshot.source.name,
                    lastAttemptAt = attemptedAt.toEpochMilli(),
                    lastSuccessAt = snapshot.fetchedAt.toEpochMilli(),
                    isStale = false,
                    isIncomplete =
                        !snapshotIsComplete || validated.any { warning -> !warning.isComplete },
                    lastError =
                        snapshot.issue
                            ?: if (droppedCount > 0) {
                                "$droppedCount amtliche Einträge konnten nicht sicher verarbeitet werden."
                            } else {
                                null
                            },
                ),
            )
        }
    }

    private fun validateWarning(
        warning: NorthSeaWarning,
        expectedSource: WarningSourceId,
    ): NorthSeaWarning? {
        if (warning.source != expectedSource || warning.title.isBlank() || warning.publisher.isBlank()) {
            return null
        }
        val actualLinkKind = OfficialUrlPolicy.linkKind(warning.sourceUrl) ?: return null
        if (actualLinkKind != warning.linkKind) return null
        val datesAreConsistent =
            warning.validFrom == null ||
                warning.validUntil == null ||
                !warning.validFrom.isAfter(warning.validUntil)
        val geometryIsValid = warning.geometry?.isValid != false
        val sanitized =
            warning.copy(
                geometry = warning.geometry.takeIf { geometryIsValid },
                isComplete = warning.isComplete && datesAreConsistent && geometryIsValid,
                contentRevision = "",
            )
        return sanitized.copy(contentRevision = WarningIdentity.contentRevision(sanitized))
    }

    companion object {
        private val BERLIN_ZONE = ZoneId.of("Europe/Berlin")
    }
}

/**
 * ELWIS list rows are deliberately incomplete. A normal refresh must not replace a previously
 * loaded official detail page with that summary: doing so would discard coordinates/full text and
 * make the same notice appear new again. If any list-visible source field changed, however, the
 * summary wins and details are fetched again on demand.
 */
internal fun preserveCachedDetailsWhenSummaryIsUnchanged(
    incoming: NorthSeaWarning,
    cached: NorthSeaWarning?,
): NorthSeaWarning {
    if (incoming.isComplete || cached?.isComplete != true) return incoming
    val unchanged =
        incoming.id == cached.id &&
            incoming.officialId == cached.officialId &&
            incoming.reference == cached.reference &&
            incoming.title == cached.title &&
            incoming.source == cached.source &&
            incoming.publisher == cached.publisher &&
            incoming.publishedDate == cached.publishedDate &&
            incoming.validFrom == cached.validFrom &&
            incoming.validUntil == cached.validUntil &&
            incoming.area == cached.area &&
            incoming.sourceUrl == cached.sourceUrl &&
            incoming.linkKind == cached.linkKind &&
            incoming.lifecycle == cached.lifecycle
    if (!unchanged) return incoming

    val refreshed = cached.copy(lastUpdatedAt = incoming.lastUpdatedAt, contentRevision = "")
    return refreshed.copy(contentRevision = WarningIdentity.contentRevision(refreshed))
}

private fun WarningSourceSyncEntity.toModelOrNull(): WarningSourceSyncState? {
    val parsedSource = WarningSourceId.entries.firstOrNull { it.name == source } ?: return null
    return WarningSourceSyncState(
        source = parsedSource,
        lastAttemptAt = Instant.ofEpochMilli(lastAttemptAt),
        lastSuccessAt = lastSuccessAt?.let(Instant::ofEpochMilli),
        isStale = isStale,
        isIncomplete = isIncomplete,
        lastError = lastError,
    )
}
