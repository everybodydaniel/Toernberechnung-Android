package com.example.trnberechnung.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChecklistDao {

    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY sortOrder ASC")
    fun getItemsForTrip(tripId: Int): Flow<List<ChecklistItem>>

    @Query("SELECT * FROM checklist_items WHERE tripId = :tripId ORDER BY sortOrder ASC")
    suspend fun getItemsForTripSync(tripId: Int): List<ChecklistItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChecklistItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ChecklistItem)

    @Update
    suspend fun update(item: ChecklistItem)

    @Delete
    suspend fun delete(item: ChecklistItem)

    @Query("DELETE FROM checklist_items WHERE tripId = :tripId")
    suspend fun deleteAllForTrip(tripId: Int)

    @Query("SELECT COUNT(*) FROM checklist_items WHERE tripId = :tripId")
    suspend fun countForTrip(tripId: Int): Int
}
