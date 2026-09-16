package com.example.trnberechnung

import android.app.Application
import androidx.room.Room
import com.example.trnberechnung.database.AppDatabase
import com.example.trnberechnung.model.AppPreferences
import com.example.trnberechnung.navigation.ActiveVoyageManager
import com.example.trnberechnung.navigation.FusedLocationProvider
import com.example.trnberechnung.navigation.NavigationTracker
import com.example.trnberechnung.navigation.VoyageServiceDependencies
import com.example.trnberechnung.navigation.VoyageServiceHost
import com.example.trnberechnung.repository.ActiveVoyageRepository
import com.example.trnberechnung.repository.NautiConversationRepository
import com.example.trnberechnung.repository.NorthSeaWarningRepository
import com.example.trnberechnung.repository.RoomActiveVoyagePersistence
import com.example.trnberechnung.warnings.BshWarningSource
import com.example.trnberechnung.warnings.ElwisWarningSource
import java.util.UUID

class TideNodeApplication :
    Application(),
    VoyageServiceHost {
    val database: AppDatabase by lazy {
        Room
            .databaseBuilder(
                applicationContext,
                AppDatabase::class.java,
                "tide_database",
            )
            .addMigrations(AppDatabase.MIGRATION_15_16)
            // Preserve warning data from version 15 onward. Unsupported older development schemas
            // still use the existing destructive fallback because the app has not been published.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val appPreferences: AppPreferences by lazy { AppPreferences(this) }

    val nautiConversationRepository: NautiConversationRepository by lazy {
        NautiConversationRepository(database.nautiDao(), ::localDataOwnerId)
    }

    val activeVoyageRepository: ActiveVoyageRepository by lazy {
        ActiveVoyageRepository(database.activeVoyageDao(), ::localDataOwnerId)
    }

    val northSeaWarningRepository: NorthSeaWarningRepository by lazy {
        NorthSeaWarningRepository(
            database = database,
            adapters = listOf(BshWarningSource(), ElwisWarningSource()),
        )
    }

    val roomActiveVoyagePersistence: RoomActiveVoyagePersistence by lazy {
        RoomActiveVoyagePersistence(database, ::localDataOwnerId)
    }

    val navigationLocationProvider: FusedLocationProvider by lazy {
        FusedLocationProvider(this)
    }

    val activeVoyageManager: ActiveVoyageManager by lazy {
        ActiveVoyageManager(
            navigationTracker = NavigationTracker(),
            persistence = roomActiveVoyagePersistence,
        )
    }

    override val voyageServiceDependencies: VoyageServiceDependencies by lazy {
        VoyageServiceDependencies(
            locationProvider = navigationLocationProvider,
            activeVoyageManager = activeVoyageManager,
        )
    }

    /**
     * Namespaces local Nauti and voyage data.
     *
     * Since the Crewspace account was removed there is no signed-in skipper to key on, so a single
     * installation-stable ID is generated once and reused. The indirection stays because the Room
     * entities are owner-scoped and reintroducing accounts should not require a migration.
     */
    fun localDataOwnerId(): String {
        val preferences = getSharedPreferences(LOCAL_DATA_PREFS, MODE_PRIVATE)
        val existing = preferences.getString(GUEST_OWNER_KEY, null)
        if (!existing.isNullOrBlank()) return "guest:$existing"
        val generated = UUID.randomUUID().toString()
        preferences.edit().putString(GUEST_OWNER_KEY, generated).apply()
        return "guest:$generated"
    }

    companion object {
        private const val LOCAL_DATA_PREFS = "local_data_owner"
        private const val GUEST_OWNER_KEY = "guest_owner_id"
    }
}
