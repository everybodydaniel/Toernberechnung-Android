package com.example.trnberechnung.model

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LogbookDao {
    @Query("SELECT * FROM logbook_entries ORDER BY id DESC")
    fun getAllLogs(): Flow<List<LogbookEntry>>

    @Insert
    suspend fun insertLog(log: LogbookEntry)

    @Update
    suspend fun updateLog(log: LogbookEntry)

    @Delete
    suspend fun deleteLog(log: LogbookEntry)

    @Query("DELETE FROM logbook_entries")
    suspend fun deleteAllLogs()
}
