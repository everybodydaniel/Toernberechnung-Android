package com.example.trnberechnung.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CrewMemberDao {
    @Query("SELECT * FROM crew_members ORDER BY id ASC")
    fun getAllCrew(): Flow<List<CrewMember>>

    @Insert
    suspend fun insertCrew(member: CrewMember)

    @Update
    suspend fun updateCrew(member: CrewMember)

    @Delete
    suspend fun deleteCrew(member: CrewMember)
}
