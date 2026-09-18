package com.example.trnberechnung.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.trnberechnung.model.LogbookEntry
import com.example.trnberechnung.model.LogbookDao
import com.example.trnberechnung.model.CrewMember
import com.example.trnberechnung.model.CrewMemberDao
import com.example.trnberechnung.model.ChecklistItem
import com.example.trnberechnung.model.ChecklistDao
import androidx.room.TypeConverters

@TypeConverters(Converters::class)

@Database(
    entities = [
        TideEntity::class,
        LogbookEntry::class,
        CrewMember::class,
        ChecklistItem::class,
        PlannerEventEntity::class,
        NautiConversationEntity::class,
        NautiMessageEntity::class,
        ActiveVoyageEntity::class,
        VoyageBreadcrumbEntity::class,
        NorthSeaWarningEntity::class,
        WarningSourceSyncEntity::class,
    ],
    version = 17,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tideDao(): TideDao
    abstract fun logbookDao(): LogbookDao
    abstract fun crewMemberDao(): CrewMemberDao
    abstract fun checklistDao(): ChecklistDao
    abstract fun plannerEventDao(): PlannerEventDao
    abstract fun nautiDao(): NautiDao
    abstract fun activeVoyageDao(): ActiveVoyageDao
    abstract fun northSeaWarningDao(): NorthSeaWarningDao

    companion object {
        val MIGRATION_16_17 =
            object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "ALTER TABLE `planner_events` ADD COLUMN `participantIds` TEXT NOT NULL DEFAULT ''",
                    )
                }
            }

        val MIGRATION_15_16 =
            object : Migration(15, 16) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `north_sea_warnings` (
                            `id` TEXT NOT NULL,
                            `officialId` TEXT,
                            `reference` TEXT,
                            `title` TEXT NOT NULL,
                            `fullText` TEXT NOT NULL,
                            `source` TEXT NOT NULL,
                            `publisher` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `publishedDate` TEXT,
                            `validFrom` TEXT,
                            `validUntil` TEXT,
                            `area` TEXT,
                            `geometryType` TEXT,
                            `coordinates` TEXT,
                            `sourceUrl` TEXT NOT NULL,
                            `linkKind` TEXT NOT NULL,
                            `lastUpdatedAt` INTEGER NOT NULL,
                            `cachedAt` INTEGER NOT NULL,
                            `lifecycle` TEXT NOT NULL,
                            `isComplete` INTEGER NOT NULL,
                            `contentRevision` TEXT NOT NULL,
                            `seenRevision` TEXT,
                            PRIMARY KEY(`id`)
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_north_sea_warnings_source` " +
                            "ON `north_sea_warnings` (`source`)",
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_north_sea_warnings_lifecycle_validUntil` " +
                            "ON `north_sea_warnings` (`lifecycle`, `validUntil`)",
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `warning_source_sync` (
                            `source` TEXT NOT NULL,
                            `lastAttemptAt` INTEGER NOT NULL,
                            `lastSuccessAt` INTEGER,
                            `isStale` INTEGER NOT NULL,
                            `isIncomplete` INTEGER NOT NULL,
                            `lastError` TEXT,
                            PRIMARY KEY(`source`)
                        )
                        """.trimIndent(),
                    )
                }
            }
    }
}
