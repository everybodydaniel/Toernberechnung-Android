package com.example.trnberechnung.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigration16To17Test {
    @get:Rule
    val helper =
        MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase::class.java,
            emptyList(),
            FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrationPreservesExistingEventsAndAddsAnEmptyParticipantSelection() {
        helper.createDatabase(TEST_DATABASE, 16).apply {
            execSQL(
                """
                INSERT INTO planner_events (
                    id, startDate, endDate, title, description, category
                ) VALUES ('legacy-event', '2026-07-04', '2026-07-04', 'Ablegen', '', 'Allgemein')
                """.trimIndent(),
            )
            close()
        }

        val database =
            helper.runMigrationsAndValidate(
                TEST_DATABASE,
                17,
                true,
                AppDatabase.MIGRATION_16_17,
            )

        database.query("SELECT title, participantIds FROM planner_events WHERE id = 'legacy-event'").use {
            assertTrue(it.moveToFirst())
            assertEquals("Ablegen", it.getString(0))
            assertEquals("", it.getString(1))
        }
        database.close()
    }

    companion object {
        private const val TEST_DATABASE = "migration-16-17"
    }
}
